package com.leeburke.springgame.ai.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.OutcomeEffect;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepOutcome;
import com.leeburke.springgame.action.resolution.StepResult;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.action.resolution.UnavailableReason;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.narration.AttemptedAction.TargetKind;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Derives the Outcome Narrator's facts from a {@link ResolvedOutcome} (confirmed backend truth, after
 * Stage 11) and, for each step, a Java summary of what was attempted from the validated intent. The
 * narrator never receives the intent itself. Names come from the player-visible scene and the
 * player's references; the attacker of a defended attack from the backend incoming attack, named
 * only if visible. Spoken words are passed on only for a RESOLVED communication step.
 */
public final class OutcomeNarrationContextBuilder {

	static final String UNSEEN = "something unseen";

	private final GameContentCatalog content;
	private final WorldContentCatalog world;

	public OutcomeNarrationContextBuilder(GameContentCatalog content, WorldContentCatalog world) {
		this.content = Objects.requireNonNull(content, "content");
		this.world = Objects.requireNonNull(world, "world");
	}

	/**
	 * @param validated     the intent that produced the outcome; its view is the scene as the player saw it
	 * @param incoming      the incoming attacks the outcome may have responded to, by reference
	 * @param playerWording what the player typed, if any; passed on only as a bounded, untrusted excerpt
	 * @throws IllegalArgumentException if the outcome's steps are not the intent's steps
	 */
	public OutcomeNarrationContext build(ResolvedOutcome outcome, ValidatedActionIntent validated,
			Map<String, IncomingAttack> incoming, NarrationMode mode, Optional<TerminalFact> terminal,
			Optional<String> playerWording) {
		return build(outcome, validated, incoming, mode, terminal, playerWording, Optional.empty());
	}

	/**
	 * @param arrival where the player arrived if the outcome left the scene, as names the player now sees
	 */
	public OutcomeNarrationContext build(ResolvedOutcome outcome, ValidatedActionIntent validated,
			Map<String, IncomingAttack> incoming, NarrationMode mode, Optional<TerminalFact> terminal,
			Optional<String> playerWording, Optional<Arrival> arrival) {
		return build(outcome, validated, incoming, mode, terminal, playerWording, arrival, SceneKnowledge.NONE);
	}

	/**
	 * @param knowledge what the player knows beyond the view (fallen creatures, where exits lead, the
	 *                  scene's code for its authored texts, and their last look), for telling the world
	 */
	public OutcomeNarrationContext build(ResolvedOutcome outcome, ValidatedActionIntent validated,
			Map<String, IncomingAttack> incoming, NarrationMode mode, Optional<TerminalFact> terminal,
			Optional<String> playerWording, Optional<Arrival> arrival, SceneKnowledge knowledge) {
		List<ActionStep> steps = validated.intent().steps();
		if (steps.size() != outcome.steps().size()) {
			throw new IllegalArgumentException("The outcome has " + outcome.steps().size() + " steps, the intent " + steps.size());
		}
		PlayerSceneView view = validated.context().view();
		NarrationNames names = NarrationNames.of(view, world);
		PlaceDescriber here = new PlaceDescriber(world, knowledge.sceneCode(), view, knowledge.fallenEntityIds(), knowledge.exitLabels(),
				this::itemName);
		// Containers as the steps leave them: a look or a take after an open sees what is there now.
		PlayerActionReferences references = validated.context().references();
		List<NarrationFact> facts = new ArrayList<>();
		// The player's zone as the steps play out, so a look after a move describes the new place.
		String zoneId = view.currentZoneId();
		// Where the player has already stood (when recorded): walking back there is familiar, told briefly.
		java.util.Optional<java.util.Set<String>> been = knowledge.visited().map(java.util.HashSet::new);
		boolean leftScene = false;
		for (int i = 0; i < steps.size(); i++) {
			StepOutcome step = outcome.steps().get(i);
			if (!step.stepId().equals(steps.get(i).id())) {
				throw new IllegalArgumentException("Outcome step " + step.stepId() + " is not intent step " + steps.get(i).id());
			}
			AttemptedAction attempt = attempt(steps.get(i).payload(), names, references, knowledge.exitLabels());
			if (step.status() != StepStatus.RESOLVED) {
				attempt = attempt.withoutWords();
			}
			if (leftScene && step.result().orElse(null) instanceof StepResult.ObservationResult) {
				// A look around just after arriving: what is in sight there, without retelling the place itself.
				Arrival where = arrival.orElseThrow(() -> new IllegalArgumentException("A scene was left without an arrival"));
				Perception seen = where.view().map(v -> v.describer(world, this::itemName).perceive(v.view().currentZoneId()).withoutHere())
						.orElseGet(() -> new Perception(Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of()));
				facts.add(new NarrationFact.Perceived(i + 1, attempt, seen, false));
				continue;
			}
			NarrationFact fact = fact(i + 1, step, steps.get(i).payload(), attempt, names, incoming, arrival, zoneId, here, knowledge);
			if (fact instanceof NarrationFact.WalkedTo walk && step.result().orElse(null) instanceof StepResult.MovementResult moved
					&& been.isPresent()) {
				if (been.get().contains(moved.toZone())) {
					fact = new NarrationFact.WalkedTo(walk.step(), walk.attempt(), walk.from(),
							new Perception.PlaceRef(walk.to().label(), walk.to().phrase(), ""), walk.passage());
				}
				been.get().add(moved.toZone());
			}
			facts.add(fact);
			if (fact instanceof NarrationFact.CrossedInto) {
				leftScene = true;
			}
			if (step.result().orElse(null) instanceof StepResult.MovementResult move && move.moved()) {
				zoneId = move.toZone();
			}
			if (step.result().orElse(null) instanceof StepResult.InteractionResult r && r.failure().isEmpty()) {
				here = afterInteraction(here, r);
			}
		}
		// Where the player is when the turn ends, in the world's words: the arrival, or the place the steps ended in.
		String zone = leftScene
				? arrival.flatMap(Arrival::view).map(v -> v.describer(world, this::itemName).place(v.view().currentZoneId()).phrase())
						.orElse(arrival.orElseThrow().zoneName())
				: here.place(zoneId).phrase();
		return new OutcomeNarrationContext(mode, zone, outcome.overall(), facts, terminal,
				playerWording.flatMap(OutcomeNarrationContext::excerpt));
	}

	private String itemName(String code) {
		return content.findItem(code).map(item -> item.displayName()).orElse("something");
	}

	private NarrationFact fact(int step, StepOutcome outcome, ActionPayload payload, AttemptedAction attempt, NarrationNames names,
			Map<String, IncomingAttack> incoming, Optional<Arrival> arrival, String currentZoneId, PlaceDescriber here,
			SceneKnowledge knowledge) {
		return switch (outcome.status()) {
			case CANCELLED -> new NarrationFact.StepCancelled(step, attempt, outcome.cancellation().orElseThrow());
			// Out of reach: the attempt could not begin. Told as the thing being out of reach, never as a try.
			case MECHANICS_UNAVAILABLE -> outcome.unavailable().filter(UnavailableReason.OUT_OF_REACH::equals).isPresent()
					&& payload instanceof ActionPayload.InteractPayload interact && interact.target() instanceof ActionTarget.ObjectTarget object
							? new NarrationFact.InteractionFailed(step, attempt, here.objectName(object.objectId()),
									StepResult.InteractionFailure.OUT_OF_REACH, here.objectPlace(object.objectId()))
							: new NarrationFact.StepHadNoEffect(step, attempt, outcome.unavailable().orElseThrow());
			case RESOLVED -> switch (outcome.result().orElseThrow()) {
				case StepResult.AttackResult a -> NarrationFact.PlayerAttacked.of(step, attempt, weaponName(a.weaponCode()),
						names.entity(a.targetEntityId()).orElse(UNSEEN), a.targetedBodyPart(), a.contact(),
						a.damage().finalDamage(), a.trauma().impactSeverity());
				case StepResult.DefenseResult d -> NarrationFact.PlayerDefended.of(step, attempt,
						Optional.ofNullable(incoming.get(d.attackRef()))
								.flatMap(attack -> names.entity(attack.attackerEntityId())).orElse(UNSEEN),
						d.method(), d.incomingContact(), d.damage().finalDamage(),
						Optional.ofNullable(incoming.get(d.attackRef())).flatMap(IncomingAttack::targetBodyPart),
						d.trauma().impactSeverity());
				case StepResult.MovementResult m -> m.moved()
						? new NarrationFact.WalkedTo(step, attempt, here.place(m.fromZone()), here.place(m.toZone()),
								here.passage(m.fromZone(), m.toZone()))
						: new NarrationFact.StayedPut(step, attempt, here.place(m.fromZone()),
								m.toZone().equals(m.fromZone()) ? Optional.empty() : Optional.of(here.place(m.toZone())));
				case StepResult.ExitResult x -> outcome.effects().stream().anyMatch(OutcomeEffect.LeftScene.class::isInstance)
						? crossed(step, attempt, arrival, here.wayPassage(x.exitId()))
						: new NarrationFact.ExitNotReached(step, attempt, zoneName(names, currentZoneId));
				case StepResult.ObservationResult o -> looked(step, attempt, payload, here, currentZoneId, knowledge);
				case StepResult.InteractionResult r -> interacted(step, attempt, r, here, arrival.isPresent());
				case StepResult.CommunicationResult c -> new NarrationFact.PlayerSpoke(step, attempt, c.kind(),
						c.addresseeEntityId().map(id -> names.entity(id).orElse(UNSEEN)));
			};
		};
	}

	/** Going through: the passage left by, the new place, the spot arrived at, and the way back at their back. */
	private NarrationFact crossed(int step, AttemptedAction attempt, Optional<Arrival> arrival, String through) {
		Arrival where = arrival.orElseThrow(() -> new IllegalArgumentException("A scene was left without an arrival"));
		if (where.view().isEmpty()) {
			return new NarrationFact.CrossedInto(step, attempt, through, where.sceneName(), "",
					new Perception.PlaceRef(where.zoneName(), "the " + where.zoneName().toLowerCase(java.util.Locale.ROOT), ""),
					Optional.empty(), Optional.empty());
		}
		ArrivalView seen = where.view().get();
		PlaceDescriber there = seen.describer(world, this::itemName);
		String arrivalZone = seen.view().currentZoneId();
		return new NarrationFact.CrossedInto(step, attempt, through, where.sceneName(), there.sceneDescription(), there.place(arrivalZone),
				seen.cameIn().map(there::wayPassage), seen.cameIn().map(there::wayLeadsTo).filter(to -> !to.isBlank()));
	}

	/**
	 * A look: around (what is in sight from here, flagged unchanged when it matches the last look),
	 * or at one thing, place or way out (its own description and state, when it is in sight).
	 */
	private NarrationFact looked(int step, AttemptedAction attempt, ActionPayload payload, PlaceDescriber here, String zoneId,
			SceneKnowledge knowledge) {
		ActionTarget target = payload instanceof ActionPayload.ObservePayload observe ? observe.target() : ActionTarget.unspecified();
		List<String> sight = new ArrayList<>(here.beside(zoneId));
		sight.add(zoneId);
		return switch (target) {
			case ActionTarget.ObjectTarget object -> here.object(object.objectId(), zoneId)
					.<NarrationFact>map(seen -> new NarrationFact.Inspected(step, attempt, seen.name(), seen.description(), seen.state(),
							seen.where(), true))
					.orElseGet(() -> new NarrationFact.Inspected(step, attempt, here.objectName(object.objectId()), "", Optional.empty(),
							here.objectPlace(object.objectId()).orElse(""), false));
			case ActionTarget.ZoneTarget zone -> {
				Perception.PlaceRef place = here.place(zone.zoneId());
				boolean inSight = sight.contains(zone.zoneId());
				yield new NarrationFact.InspectedPlace(step, attempt,
						inSight ? place : new Perception.PlaceRef(place.label(), place.phrase(), ""),
						zone.zoneId().equals(zoneId) ? "here" : place.phrase(), inSight ? here.waysFrom(zone.zoneId()) : List.of(), inSight);
			}
			case ActionTarget.ExitTarget exit -> {
				String exitZone = here.exitZone(exit.exitId()).orElse(zoneId);
				boolean inSight = sight.contains(exitZone);
				yield new NarrationFact.Inspected(step, attempt, here.exitLabel(exit.exitId()), inSight ? here.wayPassage(exit.exitId()) : "",
						Optional.empty(), exitZone.equals(zoneId) ? "here" : here.place(exitZone).phrase(), inSight);
			}
			default -> {
				if (knowledge.leads().isPresent()) {
					// A look for somewhere to go: answered from what the player knows of the scene's ways and places.
					com.leeburke.springgame.world.view.ExplorationLeads leads = knowledge.leads().get();
					PlaceDescriber after = new PlaceDescriber(world, knowledge.sceneCode(), leads.view(), knowledge.fallenEntityIds(),
							knowledge.exitLabels(), this::itemName);
					Perception inSight = after.perceive(leads.view().currentZoneId());
					Perception bearing = new Perception(inSight.here().map(p -> new Perception.PlaceRef(p.label(), p.phrase(), "")), List.of(),
							List.of(), inSight.hazards(), inSight.creatures(), List.of());
					yield new NarrationFact.SoughtWays(step, attempt, bearing, after.ways(leads.unexplored()),
							after.places(leads.unvisited()), leads.visited().stream().map(p -> after.place(p.zoneId()).phrase()).toList(),
							after.ways(leads.known()), leads.visitsRecorded(), leads.nothingKnownLeft());
				}
				Perception perception = here.perceive(zoneId);
				yield new NarrationFact.Perceived(step, attempt, perception, knowledge.lastLook().filter(perception::equals).isPresent());
			}
		};
	}

	/** The describer with a container as a successful open or take left it. */
	private static PlaceDescriber afterInteraction(PlaceDescriber here, StepResult.InteractionResult r) {
		if (r.kind() == com.leeburke.springgame.action.InteractionKind.OPEN) {
			return here.withContainer(new PlayerSceneView.VisibleContainer(r.objectId(), true, r.contents()));
		}
		if (r.kind() == com.leeburke.springgame.action.InteractionKind.PICK_UP && r.itemCode().isPresent()) {
			List<String> left = new ArrayList<>(here.view().container(r.objectId()).map(PlayerSceneView.VisibleContainer::contents)
					.orElse(List.of()));
			left.remove(r.itemCode().get());
			return here.withContainer(new PlayerSceneView.VisibleContainer(r.objectId(), true, left));
		}
		return here;
	}

	/** Opening a container, taking from it, or why that did not happen. */
	private NarrationFact interacted(int step, AttemptedAction attempt, StepResult.InteractionResult result, PlaceDescriber here,
			boolean leftScene) {
		String name = here.objectName(result.objectId());
		if (result.failure().isPresent()) {
			StepResult.InteractionFailure why = result.failure().get();
			return new NarrationFact.InteractionFailed(step, attempt, name, why,
					why == StepResult.InteractionFailure.OUT_OF_REACH ? here.objectPlace(result.objectId()) : Optional.empty());
		}
		if (result.kind() == com.leeburke.springgame.action.InteractionKind.PICK_UP) {
			return new NarrationFact.TookItem(step, attempt, result.itemCode().map(this::itemName).orElse("something"),
					result.itemCode().flatMap(content::findItem).map(item -> item.description()).orElse(""), name);
		}
		return new NarrationFact.OpenedContainer(step, attempt, name, result.contents().stream()
				.map(code -> new NarrationFact.Found(itemName(code), content.findItem(code).map(item -> item.description()).orElse("")))
				.toList(), result.alreadyOpen());
	}

	/**
	 * What the player knows about the scene beyond its view.
	 *
	 * @param fallenEntityIds visible creatures at 0 HP
	 * @param exitLabels      where each known exit leads, as far as the player knows
	 * @param sceneCode       the scene's archetype or fixed-scene code, for its authored texts ("" when unknown)
	 * @param lastLook        what the player perceived the last time they looked around here, if they did
	 */
	public record SceneKnowledge(Set<String> fallenEntityIds, Map<String, String> exitLabels, String sceneCode,
			Optional<Perception> lastLook, Optional<com.leeburke.springgame.world.view.ExplorationLeads> leads,
			Optional<Set<String>> visited) {
		public static final SceneKnowledge NONE = new SceneKnowledge(Set.of(), Map.of());

		public SceneKnowledge {
			fallenEntityIds = Set.copyOf(fallenEntityIds);
			exitLabels = Map.copyOf(exitLabels);
			Objects.requireNonNull(sceneCode, "sceneCode");
			Objects.requireNonNull(lastLook, "lastLook");
			Objects.requireNonNull(leads, "leads");
			visited = Objects.requireNonNull(visited, "visited").map(Set::copyOf);
		}

		/** Where the player has stood is not known (every place reached is told as new). */
		public SceneKnowledge(Set<String> fallenEntityIds, Map<String, String> exitLabels, String sceneCode,
				Optional<Perception> lastLook, Optional<com.leeburke.springgame.world.view.ExplorationLeads> leads) {
			this(fallenEntityIds, exitLabels, sceneCode, lastLook, leads, Optional.empty());
		}

		/** No exploration question was asked. */
		public SceneKnowledge(Set<String> fallenEntityIds, Map<String, String> exitLabels, String sceneCode,
				Optional<Perception> lastLook) {
			this(fallenEntityIds, exitLabels, sceneCode, lastLook, Optional.empty());
		}

		public SceneKnowledge(Set<String> fallenEntityIds, Map<String, String> exitLabels) {
			this(fallenEntityIds, exitLabels, "", Optional.empty());
		}
	}

	/**
	 * Where the player arrived after leaving a scene.
	 *
	 * @param sceneName the new scene's label (for headings)
	 * @param zoneName  the arrival zone's label (for headings)
	 * @param view      what the player can know there, from a fresh player-visible view of the arrival
	 */
	public record Arrival(String sceneName, String zoneName, Optional<ArrivalView> view) {
		public Arrival {
			Objects.requireNonNull(sceneName, "sceneName");
			Objects.requireNonNull(zoneName, "zoneName");
			Objects.requireNonNull(view, "view");
		}

		public Arrival(String sceneName, String zoneName) {
			this(sceneName, zoneName, Optional.empty());
		}
	}

	/**
	 * The arrival as the player knows it: a fresh view of the new scene from the arrival spot.
	 *
	 * @param cameIn the exit at the player's back, the way they came in
	 */
	public record ArrivalView(String sceneCode, PlayerSceneView view, Set<String> fallen, Map<String, String> exitLabels,
			Optional<String> cameIn) {
		public ArrivalView {
			Objects.requireNonNull(sceneCode, "sceneCode");
			Objects.requireNonNull(view, "view");
			fallen = Set.copyOf(fallen);
			exitLabels = Map.copyOf(exitLabels);
			Objects.requireNonNull(cameIn, "cameIn");
		}

		PlaceDescriber describer(WorldContentCatalog world, java.util.function.Function<String, String> itemName) {
			return new PlaceDescriber(world, sceneCode, view, fallen, exitLabels, itemName);
		}
	}

	static AttemptedAction attempt(ActionPayload payload, NarrationNames names, PlayerActionReferences references) {
		return attempt(payload, names, references, Map.of());
	}

	/** @param exitLabels what each known exit is called, so an exit is named by where it leads, not "an exit" */
	static AttemptedAction attempt(ActionPayload payload, NarrationNames names, PlayerActionReferences references,
			Map<String, String> exitLabels) {
		Builder a = new Builder(payload.type(), exitLabels);
		switch (payload) {
			case ActionPayload.AttackPayload p -> {
				a.manner = Optional.of(p.method().name());
				a.template = Optional.of(p.template());
				a.approach = Optional.of(p.approach());
				a.purpose = Optional.of(p.purpose());
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.weapons().get(p.weaponRef())).map(WeaponDefinition::displayName);
			}
			case ActionPayload.DefendPayload p -> {
				a.manner = Optional.of(p.method().name());
				a.target(p.cover(), names);
			}
			case ActionPayload.MovePayload p -> {
				a.manner = Optional.of(p.movementType().name());
				a.approach = Optional.of(p.approach());
				a.target(p.target(), names);
			}
			case ActionPayload.InteractPayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.approach = Optional.of(p.approach());
				a.target(p.target(), names);
				a.using = p.carried().flatMap(c -> carriedName(c, references));
			}
			case ActionPayload.ObservePayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.target(p.target(), names);
			}
			case ActionPayload.UseAbilityPayload p -> {
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.abilities().get(p.abilityRef())).map(d -> d.displayName());
			}
			case ActionPayload.UseItemPayload p -> {
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.items().get(p.itemRef())).map(d -> d.displayName());
			}
			case ActionPayload.CommunicatePayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.target(p.target(), names);
				a.spokenWords = Optional.of(p.content());
			}
		}
		return a.build();
	}

	private static Optional<String> carriedName(CarriedReference carried, PlayerActionReferences references) {
		return carried.kind() == CarriedKind.WEAPON
				? Optional.ofNullable(references.weapons().get(carried.ref())).map(WeaponDefinition::displayName)
				: Optional.ofNullable(references.items().get(carried.ref())).map(d -> d.displayName());
	}

	private String weaponName(String code) {
		return content.findWeapon(code).map(WeaponDefinition::displayName).orElse("weapon");
	}

	private static String zoneName(NarrationNames names, String zoneId) {
		return names.zone(zoneId).orElse("somewhere unseen");
	}

	private static final class Builder {
		private final ActionType action;
		private Optional<String> manner = Optional.empty();
		private Optional<com.leeburke.springgame.action.AttackTemplate> template = Optional.empty();
		private Optional<com.leeburke.springgame.action.ActionApproach> approach = Optional.empty();
		private Optional<com.leeburke.springgame.action.AttackPurpose> purpose = Optional.empty();
		private Optional<TargetKind> targetKind = Optional.empty();
		private Optional<String> target = Optional.empty();
		private Optional<com.leeburke.springgame.mechanics.BodyPart> bodyPart = Optional.empty();
		private Optional<String> using = Optional.empty();
		private Optional<String> spokenWords = Optional.empty();

		private final Map<String, String> exitLabels;

		Builder(ActionType action, Map<String, String> exitLabels) {
			this.action = action;
			this.exitLabels = exitLabels;
		}

		void target(ActionTarget value, NarrationNames names) {
			switch (value) {
				case ActionTarget.EntityTarget t -> {
					set(TargetKind.CREATURE, names.entity(t.entityId()).orElse(UNSEEN));
					bodyPart = t.bodyPart();
				}
				case ActionTarget.ObjectTarget t -> set(TargetKind.OBJECT, names.object(t.objectId()).orElse(UNSEEN));
				case ActionTarget.HazardTarget t -> set(TargetKind.HAZARD, names.hazard(t.hazardId()).orElse(UNSEEN));
				case ActionTarget.ZoneTarget t -> set(TargetKind.ZONE, names.zone(t.zoneId()).orElse("somewhere unseen"));
				case ActionTarget.ExitTarget t -> set(TargetKind.EXIT, exitLabels.getOrDefault(t.exitId(), "a way out"));
				case ActionTarget.SelfTarget t -> {
					set(TargetKind.SELF, "yourself");
					bodyPart = t.bodyPart();
				}
				case ActionTarget.Unspecified t -> {
					// no target
				}
			}
		}

		private void set(TargetKind kind, String name) {
			targetKind = Optional.of(kind);
			target = Optional.of(name);
		}

		AttemptedAction build() {
			return new AttemptedAction(action, manner, template, approach, purpose, targetKind, target, bodyPart, using,
					spokenWords);
		}
	}
}
