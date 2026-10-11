package com.leeburke.springgame.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.resolution.ActionEngine;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.action.resolution.UnavailableReason;
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.ai.narration.AttackCue;
import com.leeburke.springgame.ai.narration.EnemyAttackNarration;
import com.leeburke.springgame.ai.narration.EnemyAttackNarrationContext;
import com.leeburke.springgame.ai.narration.NarrationMode;
import com.leeburke.springgame.ai.narration.NarrationNames;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContext;
import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder;
import com.leeburke.springgame.ai.narration.Perception;
import com.leeburke.springgame.ai.narration.PlaceDescriber;
import com.leeburke.springgame.ai.narration.TerminalFact;
import com.leeburke.springgame.config.GameApiProperties;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyAttackOption;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.behavior.EnemyBehavior;
import com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules;
import com.leeburke.springgame.enemy.behavior.EnemyDecision;
import com.leeburke.springgame.enemy.behavior.EnemyDecisionContext;
import com.leeburke.springgame.game.DefenseGate;
import com.leeburke.springgame.game.DestinationCheck;
import com.leeburke.springgame.game.EffectApplier;
import com.leeburke.springgame.game.EffectApplier.StateChanges;
import com.leeburke.springgame.game.EncounterRules;
import com.leeburke.springgame.game.ExitTraversal;
import com.leeburke.springgame.game.LegacyContainers;
import com.leeburke.springgame.game.ExplorationQuestion;
import com.leeburke.springgame.game.IdleSteps;
import com.leeburke.springgame.game.InspectionTargetCheck;
import com.leeburke.springgame.game.GameSnapshot;
import com.leeburke.springgame.game.PendingAttack;
import com.leeburke.springgame.game.ResolutionContextFactory;
import com.leeburke.springgame.game.RouteGrounding;
import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.game.RunStatus;
import com.leeburke.springgame.game.TerminalRules;
import com.leeburke.springgame.game.TurnRandom;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.PendingAttackStore;
import com.leeburke.springgame.persistence.PersistedStateException;
import com.leeburke.springgame.persistence.RunSessionStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.TurnStore.TurnRecord;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKnowledge;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.view.ExplorationLeads;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;
import com.leeburke.springgame.persistence.WorldStore;

/**
 * Runs one turn request through the authoritative pipeline: interpret (AI or slash command), validate,
 * resolve in Java, apply effects, let one enemy respond, then narrate. Deliberately not
 * {@code @Transactional}: each phase uses its own short transaction from a
 * {@link TransactionTemplate}, so no database transaction is ever open while a model is called.
 * <ol>
 * <li>Claim: replay a stored answer, finalise an unfinished turn, or start this one (INTERPRETING,
 * with a lease) after the state-version and rate checks.</li>
 * <li>Interpret, with no transaction open. Deterministic rejections are stored; a transient AI
 * failure deletes the record so the same key can be retried.</li>
 * <li>Mechanics, in one transaction holding the run row lock and fenced on the lease: resolve,
 * enforce the mandatory defense, apply effects, run the enemy phase, advance the state version and
 * store the mechanics summary.</li>
 * <li>Narrate and complete ({@link NarrationFinalizer}).</li>
 * </ol>
 */
@Service
public class TurnService {

	public static final int MAX_INPUT_LENGTH = 500;
	private static final int MAX_CLAIM_ATTEMPTS = 4;
	private static final String COMMAND_HINT = "Slash commands always work, for example /attack entity_1 slash or /defend parry.";

	private final RunSessionStore sessions;
	private final TurnStore turns;
	private final GameStateLoader loader;
	private final ResolutionContextFactory contexts;
	private final ActionInterpreter interpreter;
	private final ActionValidator validator = new ActionValidator();
	private final ActionEngine engine;
	private final EnemyBehavior behavior;
	private final OutcomeNarrationContextBuilder narrationContexts;
	private final GameRunStore runs;
	private final EnemyStore enemies;
	private final WorldStore world;
	private final PendingAttackStore pendingAttacks;
	private final NarrationFinalizer finalizer;
	private final AbuseLimits limits;
	private final WorldContentCatalog worldContent;
	private final GameContentCatalog content;
	private final TransactionTemplate transactions;
	private final TransactionTemplate readOnly;
	private final Clock clock;
	private final Duration lease;
	private final TurnJson json = new TurnJson();

	TurnService(RunSessionStore sessions, TurnStore turns, GameStateLoader loader, ResolutionContextFactory contexts,
			ActionInterpreter interpreter, ActionEngine engine, EnemyBehavior behavior,
			OutcomeNarrationContextBuilder narrationContexts, GameRunStore runs, EnemyStore enemies, WorldStore world,
			PendingAttackStore pendingAttacks, NarrationFinalizer finalizer, AbuseLimits limits,
			WorldContentCatalog worldContent, GameContentCatalog content, PlatformTransactionManager transactionManager,
			Clock clock, GameApiProperties properties) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.turns = Objects.requireNonNull(turns, "turns");
		this.loader = Objects.requireNonNull(loader, "loader");
		this.contexts = Objects.requireNonNull(contexts, "contexts");
		this.interpreter = Objects.requireNonNull(interpreter, "interpreter");
		this.engine = Objects.requireNonNull(engine, "engine");
		this.behavior = Objects.requireNonNull(behavior, "behavior");
		this.narrationContexts = Objects.requireNonNull(narrationContexts, "narrationContexts");
		this.runs = Objects.requireNonNull(runs, "runs");
		this.enemies = Objects.requireNonNull(enemies, "enemies");
		this.world = Objects.requireNonNull(world, "world");
		this.pendingAttacks = Objects.requireNonNull(pendingAttacks, "pendingAttacks");
		this.finalizer = Objects.requireNonNull(finalizer, "finalizer");
		this.limits = Objects.requireNonNull(limits, "limits");
		this.worldContent = Objects.requireNonNull(worldContent, "worldContent");
		this.content = Objects.requireNonNull(content, "content");
		this.transactions = new TransactionTemplate(transactionManager);
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
		this.clock = Objects.requireNonNull(clock, "clock");
		this.lease = properties.lease();
	}

	/**
	 * Submits one turn. The caller has already checked the run token.
	 *
	 * @param stateVersion the state version of the view the player acted on
	 * @return the stored answer: 200 with a turn response, or a stored rejection (409 STALE_VIEW, 422)
	 * @throws GameException for answers that are not stored (in progress, rate limited, finished, ...)
	 */
	public TurnReply submit(UUID runId, UUID requestKey, String input, long stateVersion) {
		if (input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
			throw new GameException(ErrorCode.INVALID_REQUEST, "Input must be 1 to " + MAX_INPUT_LENGTH + " characters.");
		}
		String requestHash = RunTokens.sha256Hex(stateVersion + "\n" + input);

		Claim claim = claim(runId, requestKey, requestHash, stateVersion, input);
		if (claim.reply().isPresent()) {
			return claim.reply().get();
		}
		UUID owner = claim.owner().orElseThrow();
		TurnTiming timing = new TurnTiming();
		String outcome = "ERROR";
		try {
			TurnReply reply = play(runId, requestKey, owner, input, stateVersion, timing);
			outcome = String.valueOf(reply.status());
			return reply;
		} catch (GameException e) {
			outcome = e.code().name();
			throw e;
		} finally {
			timing.log(outcome);
		}
	}

	/** A new turn this request owns: interpret, ground, resolve and narrate, timing each phase. */
	private TurnReply play(UUID runId, UUID requestKey, UUID owner, String input, long stateVersion, TurnTiming timing) {

		Optional<Interpretation> interpretation = readOnly.execute(status -> {
			RunSession session = sessions.find(runId).orElseThrow();
			if (session.stateVersion() != stateVersion) {
				return Optional.<Interpretation>empty();
			}
			GameSnapshot snapshot = loader.load(session);
			Map<String, String> labels = ExitLabels.of(snapshot.scene(), world, worldContent);
			PlaceDescriber places = new PlaceDescriber(worldContent, snapshot.scene().definitionCode(), snapshot.view(),
					snapshot.fallenVisibleEnemyIds(), labels, code -> content.findItem(code).map(i -> i.displayName()).orElse("something"));
			HintWriter hints = new HintWriter(places, snapshot.location().zoneId(), Optional.of(ExplorationLeads.of(snapshot.view(),
					snapshot.scene().state().visitedZones(), ExitLabels.undiscovered(snapshot.scene(), world))));
			return Optional.of(new Interpretation(snapshot.pending(), contexts.interpretation(snapshot, labels), labels,
					ExitLabels.backward(snapshot.scene(), world, worldContent), snapshot.fallenVisibleEnemyIds(), snapshot.view(), hints,
					hints.where()));
		});
		if (interpretation.isEmpty()) {
			return finish(runId, requestKey, owner, TurnStatus.STALE, staleError());
		}

		// No transaction is open while the interpreter (possibly a remote model) runs.
		ActionInterpretationResult result = interpreter.interpret(input, interpretation.get().setup());
		timing.phase("interpret");
		ValidatedActionIntent validated;
		boolean freeText;
		switch (result) {
			case ActionInterpretationResult.NotSupported unsupported -> {
				return finish(runId, requestKey, owner, TurnStatus.REJECTED, new GameException(ErrorCode.ACTION_NOT_SUPPORTED,
						"That is not something you can attempt here.", Optional.empty(), Optional.of(interpretation.get().where())));
			}
			case ActionInterpretationResult.Failed failed -> {
				if (failed.reason() == ActionInterpretationResult.Failure.AI_UNAVAILABLE) {
					// Transient: forget this attempt so the same key can be retried later.
					turns.release(runId, requestKey, owner);
					throw new GameException(ErrorCode.INTERPRETATION_FAILED, "Free-text actions are unavailable right now.",
							Optional.of("AI_UNAVAILABLE"), Optional.of(COMMAND_HINT));
				}
				GameException error = failed.reason() == ActionInterpretationResult.Failure.INVALID_COMMAND
						? new GameException(ErrorCode.INVALID_COMMAND, "That command could not be understood.", Optional.empty(),
								Optional.of(String.join(" ", failed.details())).filter(h -> !h.isBlank()))
						: new GameException(ErrorCode.INTERPRETATION_FAILED, "That action could not be understood.",
								Optional.of(failed.reason().name()), Optional.of(interpretation.get().where()));
				return finish(runId, requestKey, owner, TurnStatus.REJECTED, error);
			}
			case ActionInterpretationResult.Unclear unclear -> {
				// The model named what it could not place. Java may still ground the journey it did give
				// (or the one place the player's words name); otherwise the player is asked.
				Interpretation seen = interpretation.get();
				if (seen.pending().isPresent()
						&& !unclear.intent().map(intent -> DefenseGate.answers(intent, seen.pending())).orElse(false)) {
					return finish(runId, requestKey, owner, TurnStatus.REJECTED, defenseRequired());
				}
				RouteGrounding.Result grounded = unclear.intent()
						.map(intent -> RouteGrounding.ground(input, intent, seen.view(), seen.exitLabels(), seen.backExits(), seen.fallen()))
						.orElseGet(() -> RouteGrounding.groundNamedPlace(input, seen.view(), seen.exitLabels(), seen.backExits()));
				Grounding outcome = settle(grounded, Optional.empty(), seen);
				timing.phase("grounding");
				if (grounded instanceof RouteGrounding.Grounded g) {
					timing.grounded(unclear.intent().orElse(g.intent()), g.intent());
				}
				if (outcome.refusal().isPresent()) {
					return finish(runId, requestKey, owner, TurnStatus.REJECTED, outcome.refusal().get());
				}
				validated = outcome.intent().orElseThrow();
				freeText = true;
			}
			case ActionInterpretationResult.Interpreted interpreted -> {
				freeText = interpreted.source() != ActionInterpretationResult.Source.COMMAND;
				if (freeText) {
					// Java plans the route, and never walks the player somewhere their words did not name.
					// The defense comes first: grounding never decides a turn that must open with one.
					Interpretation seen = interpretation.get();
					if (!DefenseGate.answers(interpreted.validated().intent(), seen.pending())) {
						return finish(runId, requestKey, owner, TurnStatus.REJECTED, defenseRequired());
					}
					// A close look must be at something the player's words name: never a stray object instead.
					ValidatedActionIntent read = interpreted.validated();
					InspectionTargetCheck.Result looked = InspectionTargetCheck.check(input, read.intent(), seen.setup());
					if (looked instanceof InspectionTargetCheck.Unclear) {
						return finish(runId, requestKey, owner, TurnStatus.REJECTED, unclear(seen));
					}
					if (looked instanceof InspectionTargetCheck.Retargeted retargeted) {
						Optional<ValidatedActionIntent> regrounded = validator.validated(retargeted.intent(), seen.setup().validation());
						if (regrounded.isEmpty()) {
							return finish(runId, requestKey, owner, TurnStatus.REJECTED, unclear(seen));
						}
						read = regrounded.get();
					}
					RouteGrounding.Result grounded = RouteGrounding.ground(input, read.intent(), seen.view(),
							seen.exitLabels(), seen.backExits(), seen.fallen());
					Grounding outcome = settle(grounded, Optional.of(read), seen);
					timing.phase("grounding");
					if (grounded instanceof RouteGrounding.Grounded g) {
						timing.grounded(interpreted.validated().intent(), g.intent());
					}
					if (outcome.refusal().isPresent()) {
						return finish(runId, requestKey, owner, TurnStatus.REJECTED, outcome.refusal().get());
					}
					validated = outcome.intent().orElseThrow();
				} else {
					validated = interpreted.validated();
				}
			}
		}
		if (!DefenseGate.answers(validated.intent(), interpretation.get().pending())) {
			return finish(runId, requestKey, owner, TurnStatus.REJECTED, defenseRequired());
		}

		Optional<String> wording = freeText ? Optional.of(input) : Optional.empty();
		Mechanics mechanics = transactions.execute(status -> {
			Mechanics m = mechanics(runId, requestKey, owner, stateVersion, validated, wording, ExplorationQuestion.asks(input));
			if (m.kind() != Mechanics.Kind.COMMITTED && m.kind() != Mechanics.Kind.STALE) {
				status.setRollbackOnly();
			}
			return m;
		});
		timing.phase("mechanics");
		return switch (mechanics.kind()) {
			case COMMITTED -> {
				TurnReply reply = finalizer.finalizeTurn(runId, requestKey);
				timing.phase("narration");
				yield reply;
			}
			case STALE -> mechanics.reply().orElseThrow();
			case FENCED -> throw inProgress();
			case DEFENSE_NOT_RESOLVED -> finish(runId, requestKey, owner, TurnStatus.REJECTED,
					new GameException(ErrorCode.DEFENSE_NOT_RESOLVED,
							"Your defense could not be carried out, so nothing happened and the attack is still coming.",
							Optional.empty(), Optional.of("Defend with something you can do now, for example /defend evade.")));
			case NOTHING_POSSIBLE -> finish(runId, requestKey, owner, TurnStatus.REJECTED,
					new GameException(ErrorCode.ACTION_NOT_SUPPORTED, "That isn't something you can do here yet.",
							Optional.of("NOT_POSSIBLE_YET"), Optional.of(interpretation.get().where())));
			case ALREADY_THERE -> finish(runId, requestKey, owner, TurnStatus.REJECTED,
					new GameException(ErrorCode.ACTION_NOT_SUPPORTED, "You're already there. Say where you want to go next.",
							Optional.of("ALREADY_THERE"), Optional.of(interpretation.get().where())));
			case OUT_OF_REACH -> finish(runId, requestKey, owner, TurnStatus.REJECTED,
					new GameException(ErrorCode.ACTION_NOT_SUPPORTED,
							interpretation.get().hints().outOfReach(mechanics.unreachable().orElseThrow()),
							Optional.of("OUT_OF_REACH"), Optional.of(interpretation.get().where())));
		};
	}

	/** Phase 1: replay, finalise, or start this request as the run's one unfinished turn. */
	private Claim claim(UUID runId, UUID requestKey, String requestHash, long stateVersion, String input) {
		for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
			Instant now = clock.instant();
			Optional<TurnRecord> existing = turns.find(runId, requestKey);
			if (existing.isPresent()) {
				TurnRecord record = existing.get();
				if (!record.requestHash().equals(requestHash)) {
					throw new GameException(ErrorCode.IDEMPOTENCY_KEY_REUSED, "This idempotency key was used for a different request.");
				}
				switch (record.status()) {
					case COMPLETED, REJECTED, STALE -> {
						return Claim.of(new TurnReply(record.responseStatus().orElseThrow(), record.responseJson().orElseThrow()));
					}
					case MECHANICS_COMMITTED -> {
						return Claim.of(finalizer.finalizeTurn(runId, requestKey));
					}
					case INTERPRETING -> {
						if (record.leaseUntil().orElseThrow().isAfter(now)) {
							throw inProgress();
						}
						UUID owner = UUID.randomUUID();
						if (turns.takeOver(runId, requestKey, owner, now.plus(lease), now)) {
							return Claim.owned(owner);
						}
						continue;
					}
				}
			}

			Optional<TurnRecord> unfinished = turns.findUnfinished(runId);
			if (unfinished.isPresent()) {
				TurnRecord other = unfinished.get();
				if (other.status() == TurnStatus.MECHANICS_COMMITTED) {
					// A new turn never advances past un-narrated mechanics.
					finalizer.finalizeTurn(runId, other.requestKey());
				} else if (other.leaseUntil().orElseThrow().isAfter(now)) {
					throw inProgress();
				} else {
					turns.deleteAbandoned(runId, other.requestKey(), now);
				}
				continue;
			}

			RunSession session = sessions.find(runId).orElseThrow(() -> new GameException(ErrorCode.RUN_NOT_FOUND, "Run not found."));
			if (session.status() == RunStatus.INITIALIZING) {
				throw new GameException(ErrorCode.RUN_INITIALIZING, "The run is still being created.");
			}
			if (session.status().terminal()) {
				throw new GameException(ErrorCode.RUN_FINISHED, "This run is over.");
			}
			try {
				if (session.stateVersion() != stateVersion) {
					String body = json.write(staleError().body());
					turns.insertFinished(runId, requestKey, requestHash, stateVersion, TurnStatus.STALE, ErrorCode.STALE_VIEW.status(),
							body, now);
					return Claim.of(new TurnReply(ErrorCode.STALE_VIEW.status(), body));
				}
				limits.acquireTurn(runId);
				UUID owner = UUID.randomUUID();
				turns.insertInterpreting(runId, requestKey, requestHash, stateVersion, owner, now.plus(lease), now, input);
				return Claim.owned(owner);
			} catch (DuplicateKeyException race) {
				// Another request inserted this key or another unfinished turn first: look again.
			}
		}
		throw inProgress();
	}

	/** Phase 3, inside the mechanics transaction. */
	/**
	 * @param exploring the player asked where they can go: an untargeted look is answered with what they
	 *                  know of the scene's ways and places after this turn ({@link ExplorationLeads})
	 */
	private Mechanics mechanics(UUID runId, UUID requestKey, UUID owner, long stateVersion, ValidatedActionIntent validated,
			Optional<String> wording, boolean exploring) {
		RunSession session = sessions.lock(runId);
		boolean stillOwned = turns.find(runId, requestKey)
				.filter(r -> r.status() == TurnStatus.INTERPRETING && r.leaseOwner().filter(owner::equals).isPresent()).isPresent();
		if (!stillOwned) {
			return Mechanics.of(Mechanics.Kind.FENCED);
		}
		if (session.stateVersion() != stateVersion || session.status() != RunStatus.ACTIVE) {
			return new Mechanics(Mechanics.Kind.STALE, Optional.of(finish(runId, requestKey, owner, TurnStatus.STALE, staleError())));
		}

		GameSnapshot snapshot = loader.load(session);
		int turnNumber = session.turnNumber() + 1;
		ResolvedOutcome outcome = engine.resolve(validated, contexts.resolution(snapshot, validated),
				TurnRandom.player(snapshot.runSeed(), turnNumber));
		if (!DefenseGate.resolved(outcome, snapshot.pending())) {
			return Mechanics.of(Mechanics.Kind.DEFENSE_NOT_RESOLVED);
		}
		// Nothing meaningful happened (no step resolved, not even as a failure, other than a move to
		// where the player already was): commit nothing. Missed attacks, failed defenses, blocked
		// moves, holding position and observations are meaningful and always commit.
		Set<String> idle = IdleSteps.of(outcome, validated.intent());
		if (outcome.steps().stream().noneMatch(s -> s.status() == StepStatus.RESOLVED && !idle.contains(s.stepId()))) {
			// Reaching for something not within reach could not even begin: say where it is instead.
			Optional<String> unreachable = unreachableObject(outcome, validated.intent());
			if (unreachable.isPresent()) {
				return new Mechanics(Mechanics.Kind.OUT_OF_REACH, Optional.empty(), unreachable);
			}
			return Mechanics.of(idle.isEmpty() ? Mechanics.Kind.NOTHING_POSSIBLE : Mechanics.Kind.ALREADY_THERE);
		}

		StateChanges changes = EffectApplier.apply(snapshot, outcome);
		NarrationNames names = NarrationNames.of(validated.context().view(), worldContent);
		SceneInstance scene = snapshot.scene();
		if (changes.playerHpAfter() != changes.playerHpBefore()) {
			runs.updatePlayerHp(runId, changes.playerHpAfter());
		}
		changes.enemyHp().forEach((entityId, hp) -> enemies.updateHp(scene.id(), entityId, hp));
		if (changes.attackConsumed()) {
			pendingAttacks.delete(runId);
		}
		// What the player now knows of this scene (every zone stood in, and those beside it) and any
		// container changed: one revision-checked scene update. Taken items go onto the tool belt.
		SceneState known = scene.state().visiting(snapshot.location().zoneId());
		for (String zone : changes.visited()) {
			known = known.seeing(SceneKnowledge.perceivable(known, zone)).visiting(zone);
		}
		for (String objectId : changes.opened()) {
			known = known.withContainer(known.container(objectId).orElseThrow().opened());
		}
		for (EffectApplier.TakenItem item : changes.taken()) {
			known = known.withContainer(known.container(item.objectId()).orElseThrow().without(item.itemCode()));
			runs.addToolBeltItem(runId, item.itemCode());
		}
		if (!known.equals(scene.state())) {
			world.updateSceneState(scene.id(), scene.revision(), known);
		}

		String movedTo = null;
		String enteredScene = null;
		Optional<OutcomeNarrationContextBuilder.Arrival> arrival = Optional.empty();
		if (changes.exitId().isPresent()) {
			String exitId = changes.exitId().get();
			SceneExit exit = scene.state().exits().stream().filter(x -> x.id().equals(exitId)).findFirst().orElseThrow();
			SceneInstance stored = world.findScene(exit.destinationSceneId())
					.orElseThrow(() -> new PersistedStateException("Exit " + exitId + " leads to a missing scene"));
			PlayerLocation location = ExitTraversal.arrival(scene, exitId, stored);
			world.setPlayerLocation(runId, location);
			world.markDiscovered(stored.id());
			// Arriving, the player takes in where they stand and what is beside it; nothing further in.
			SceneInstance normalized = LegacyContainers.normalized(stored, worldContent.containers());
			SceneState arrivedState = normalized.state().seeing(SceneKnowledge.perceivable(normalized.state(), location.zoneId()))
					.visiting(location.zoneId());
			if (!arrivedState.equals(stored.state())) {
				world.updateSceneState(stored.id(), stored.revision(), arrivedState);
			}
			SceneInstance destination = new SceneInstance(stored.id(), stored.runId(), stored.definitionCode(), stored.placement(),
					true, stored.revision(), arrivedState);
			enteredScene = SceneNames.scene(worldContent, destination);
			// What the player sees on arrival: a fresh view of the new scene from where they stand, under the
			// normal visibility rules (nothing hidden), so the arrival and any look around are told truthfully.
			GameSnapshot arrived = new GameSnapshot(session, snapshot.runSeed(), snapshot.player(), location, destination,
					enemies.findEnemies(destination.id()), Optional.empty());
			Optional<String> cameIn = destination.state().exits().stream()
					.filter(x -> x.destinationSceneId().equals(scene.id())).map(SceneExit::id).findFirst();
			arrival = Optional.of(new OutcomeNarrationContextBuilder.Arrival(enteredScene, SceneNames.zone(destination, location.zoneId()),
					Optional.of(new OutcomeNarrationContextBuilder.ArrivalView(destination.definitionCode(), arrived.view(),
							arrived.fallenVisibleEnemyIds(), ExitLabels.of(destination, world, worldContent), cameIn))));
		} else if (!changes.zone().equals(snapshot.location().zoneId())) {
			world.setPlayerLocation(runId, new PlayerLocation(scene.id(), changes.zone()));
			movedTo = SceneNames.zone(scene, changes.zone());
		}

		List<EnemyInstance> defeated = changes.defeated().stream().map(id -> snapshot.enemy(id).orElseThrow()).toList();
		RunStatus runStatus = TerminalRules.after(changes.playerDown(), defeated);
		List<String> defeatedNames = defeated.stream().map(e -> names.entity(e.entityId()).orElse("something unseen")).toList();

		boolean attackStillPending = snapshot.pending().isPresent() && !changes.attackConsumed();
		Optional<RunSession.EnemyCursor> cursor = session.cursor();
		Optional<TurnStore.EnemyAction> enemyAction = Optional.empty();
		MechanicsSummary.EnemyTurn enemyTurn = null;
		MechanicsSummary.AttackNarration attackNarration = null;
		if (EncounterRules.enemyPhaseRuns(outcome, idle, runStatus, changes.exitId().isPresent(), attackStillPending)) {
			List<EnemyInstance> eligible = new ArrayList<>();
			for (EnemyInstance enemy : EncounterRules.withinReach(snapshot.visibleEnemies(), scene.state(), changes.zone())) {
				int hp = changes.enemyHp().getOrDefault(enemy.entityId(), enemy.currentHp());
				eligible.add(new EnemyInstance(enemy.entityId(), enemy.definitionCode(), enemy.stats(), enemy.maxHp(), hp,
						enemy.body(), enemy.weaponCode()));
			}
			Optional<EnemyInstance> actor = EncounterRules.nextActor(eligible, scene.id(), session.cursor());
			if (actor.isPresent()) {
				EnemyInstance self = actor.get();
				List<String> recent = turns.recentEnemyChoices(runId, scene.id(), self.entityId(), EnemyBehaviorRules.REPETITION_WINDOW);
				String attackRef = "attack-" + turnNumber;
				EnemyDecision decision = behavior.decide(new EnemyDecisionContext(contexts.combatant(self), recent, attackRef),
						TurnRandom.enemy(snapshot.runSeed(), turnNumber));
				String attackerName = names.entity(self.entityId()).orElse("something unseen");
				cursor = Optional.of(new RunSession.EnemyCursor(scene.id(), self.entityId()));
				switch (decision) {
					case EnemyDecision.Attack attack -> {
						IncomingAttack incoming = attack.attack();
						String cue = EnemyAttackNarration.cueTextOf(AttackCue.of(incoming.template()));
						pendingAttacks.insert(runId, new PendingAttack(incoming, scene.id(), attack.optionCode(), self.weaponCode(),
								turnNumber, cue, Optional.empty()));
						attackNarration = new MechanicsSummary.AttackNarration(attackRef, EnemyAttackNarrationContext.of(incoming, names,
								content.findWeapon(self.weaponCode()).orElseThrow()));
						enemyAction = Optional.of(new TurnStore.EnemyAction(scene.id(), self.entityId(), attack.optionCode()));
						enemyTurn = new MechanicsSummary.EnemyTurn(attackerName, "ATTACK");
					}
					case EnemyDecision.Hold hold -> {
						enemyAction = Optional.of(new TurnStore.EnemyAction(scene.id(), self.entityId(), EnemyAttackOption.HOLD_CODE));
						enemyTurn = new MechanicsSummary.EnemyTurn(attackerName, "HOLD");
					}
				}
			}
		}

		Map<String, IncomingAttack> incoming = snapshot.pending().map(p -> Map.of(p.attack().ref(), p.attack())).orElse(Map.of());
		NarrationMode mode = switch (runStatus) {
			case DEAD -> NarrationMode.RUN_DEATH;
			case VICTORIOUS -> NarrationMode.RUN_VICTORY;
			default -> NarrationMode.NORMAL;
		};
		Optional<TerminalFact> terminal = switch (runStatus) {
			case DEAD -> Optional.of(new TerminalFact.PlayerDied(
					snapshot.pending().flatMap(p -> names.entity(p.attack().attackerEntityId()))));
			case VICTORIOUS -> Optional.of(new TerminalFact.GuardianDefeated(defeated.stream()
					.filter(e -> e.definitionCode().equals(TerminalRules.GUARDIAN_CODE))
					.map(e -> names.entity(e.entityId()).orElse("Chapel Guardian")).findFirst().orElseThrow()));
			default -> Optional.empty();
		};
		// Asked where to go, with a look in this scene: what the player now knows of its ways and places.
		Optional<ExplorationLeads> leads = Optional.empty();
		if (exploring && changes.exitId().isEmpty() && validated.intent().steps().stream()
				.anyMatch(s -> s.payload() instanceof ActionPayload.ObservePayload o && o.target() instanceof ActionTarget.Unspecified)) {
			String standing = changes.zone();
			PlayerSceneView after = PlayerSceneViewProjector.project(known, standing, SceneKnowledge.knownZones(known, standing));
			leads = Optional.of(ExplorationLeads.of(after, known.visitedZones(), ExitLabels.undiscovered(scene, world)));
		}
		OutcomeNarrationContext narration = narrationContexts.build(outcome, validated, incoming, mode, terminal, wording, arrival,
				new OutcomeNarrationContextBuilder.SceneKnowledge(snapshot.fallenVisibleEnemyIds(),
						ExitLabels.of(scene, world, worldContent), scene.definitionCode(), lastLook(runId), leads,
						scene.state().visitedZones().map(Set::copyOf)));
		MechanicsSummary summary = new MechanicsSummary(MechanicsSummary.CURRENT_SCHEMA_VERSION, turnNumber, outcome.overall(),
				runStatus, changes.playerHpLost(), defeatedNames, movedTo, enteredScene, enemyTurn, narration, attackNarration);

		sessions.advance(runId, runStatus, cursor);
		if (!turns.commitMechanics(runId, requestKey, owner, turnNumber, enemyAction, json.write(summary))) {
			return Mechanics.of(Mechanics.Kind.FENCED);
		}
		return Mechanics.of(Mechanics.Kind.COMMITTED);
	}

	/** Stores a final error for this owner's request and returns it as the reply. */
	private TurnReply finish(UUID runId, UUID requestKey, UUID owner, TurnStatus status, GameException error) {
		String body = json.write(error.body());
		if (!turns.finish(runId, requestKey, owner, status, error.code().status(), body)) {
			throw inProgress();
		}
		return new TurnReply(error.code().status(), body);
	}

	/**
	 * Settles a grounding result: the intent to resolve (a rewritten one is validated again, exactly
	 * as the interpreter's own answer was), or the refusal that asks the player which way.
	 *
	 * @param interpreted the interpreter's validated intent, when it gave one (kept if nothing changed)
	 */
	private Grounding settle(RouteGrounding.Result result, Optional<ValidatedActionIntent> interpreted, Interpretation seen) {
		return switch (result) {
			case RouteGrounding.Grounded grounded -> validator.validated(grounded.intent(), seen.setup().validation())
					.map(Grounding::of)
					.orElseGet(() -> Grounding.refused(grounded.intent().unresolvedReferences().isEmpty()
							? new GameException(ErrorCode.INTERPRETATION_FAILED, "That action could not be understood.",
									Optional.of("INVALID_OUTPUT"), Optional.of(seen.where()))
							: unclear(seen)));
			case RouteGrounding.Ambiguous ambiguous -> Grounding.refused(new GameException(ErrorCode.INTERPRETATION_FAILED,
					"Which way did you mean?", Optional.of("UNCLEAR_DESTINATION"), Optional.of((ambiguous.choices().isEmpty() ? ""
							: "It could be " + seen.hints().choices(ambiguous.choices()) + ". ") + seen.where())));
			case RouteGrounding.Threshold threshold -> Grounding.refused(new GameException(ErrorCode.ACTION_NOT_SUPPORTED,
					seen.hints().threshold(threshold.ways()), Optional.of("AT_THRESHOLD"), Optional.of(seen.where())));
			case RouteGrounding.Unchanged unchanged -> interpreted.map(Grounding::of).orElseGet(() -> Grounding.refused(unclear(seen)));
		};
	}

	/**
	 * What the player perceived the last time they looked around, from the previous turn's stored
	 * facts: a look that finds exactly the same can then be told briefly. Absent when the previous turn
	 * had no look, or its record cannot be read (an older format is simply not compared).
	 */
	private Optional<Perception> lastLook(UUID runId) {
		try {
			return turns.findLatestCommitted(runId).flatMap(TurnStore.TurnRecord::summaryJson)
					.map(summary -> json.read(summary, MechanicsSummary.class))
					.flatMap(summary -> summary.narration().facts().reversed().stream()
							.filter(NarrationFact.Perceived.class::isInstance)
							.map(fact -> ((NarrationFact.Perceived) fact).perception())
							.findFirst());
		} catch (RuntimeException unreadable) {
			return Optional.empty();
		}
	}

	private static GameException defenseRequired() {
		return new GameException(ErrorCode.DEFENSE_REQUIRED, "An attack is coming: your action must begin by defending against it.",
				Optional.empty(), Optional.of("For example /defend parry, or describe how you defend."));
	}

	private static GameException unclear(Interpretation seen) {
		return new GameException(ErrorCode.INTERPRETATION_FAILED, "That wasn't clear enough to act on. Try naming what you mean.",
				Optional.of("UNCLEAR"), Optional.of(seen.where()));
	}

	/** Either the intent to resolve or the refusal to send. */
	private record Grounding(Optional<ValidatedActionIntent> intent, Optional<GameException> refusal) {
		static Grounding of(ValidatedActionIntent intent) {
			return new Grounding(Optional.of(intent), Optional.empty());
		}

		static Grounding refused(GameException refusal) {
			return new Grounding(Optional.empty(), Optional.of(refusal));
		}
	}

	private static GameException staleError() {
		return new GameException(ErrorCode.STALE_VIEW, "The game has moved on since this view; reload and try again.");
	}

	private static GameException inProgress() {
		return new GameException(ErrorCode.REQUEST_IN_PROGRESS, "Another action for this run is in progress; retry shortly.");
	}

	private record Claim(Optional<TurnReply> reply, Optional<UUID> owner) {
		static Claim of(TurnReply reply) {
			return new Claim(Optional.of(reply), Optional.empty());
		}

		static Claim owned(UUID owner) {
			return new Claim(Optional.empty(), Optional.of(owner));
		}
	}

	/**
	 * What phase 1 read for the interpreter: the setup, plus what the player knows (for route grounding:
	 * the view, exit labels, the exits that lead back and fallen creatures) and hints in the world's
	 * words (for refusals).
	 */
	private record Interpretation(Optional<PendingAttack> pending, InterpretationSetup setup, Map<String, String> exitLabels,
			Set<String> backExits, Set<String> fallen, PlayerSceneView view, HintWriter hints, String where) {
	}

	/** The object of the first interaction that could not begin because it was out of reach, if any. */
	private static Optional<String> unreachableObject(ResolvedOutcome outcome, ActionIntent intent) {
		return outcome.steps().stream()
				.filter(s -> s.unavailable().filter(UnavailableReason.OUT_OF_REACH::equals).isPresent())
				.flatMap(s -> intent.steps().stream().filter(step -> step.id().equals(s.stepId())))
				.map(ActionStep::payload)
				.flatMap(p -> p instanceof ActionPayload.InteractPayload interact && interact.target() instanceof ActionTarget.ObjectTarget object
						? java.util.stream.Stream.of(object.objectId()) : java.util.stream.Stream.empty())
				.findFirst();
	}

	/** @param unreachable for {@link Kind#OUT_OF_REACH}: the object that was out of reach */
	private record Mechanics(Kind kind, Optional<TurnReply> reply, Optional<String> unreachable) {
		enum Kind {
			COMMITTED,
			STALE,
			FENCED,
			DEFENSE_NOT_RESOLVED,
			NOTHING_POSSIBLE,
			ALREADY_THERE,
			OUT_OF_REACH
		}

		Mechanics(Kind kind, Optional<TurnReply> reply) {
			this(kind, reply, Optional.empty());
		}

		static Mechanics of(Kind kind) {
			return new Mechanics(kind, Optional.empty());
		}
	}
}
