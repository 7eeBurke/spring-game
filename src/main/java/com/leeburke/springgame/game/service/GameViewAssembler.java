package com.leeburke.springgame.game.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.ai.interpreter.ActionInterpretationContext;
import com.leeburke.springgame.ai.interpreter.AliasKind;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.ai.narration.FatedBand;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.content.world.RegionDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.game.GameSnapshot;
import com.leeburke.springgame.game.PendingAttack;
import com.leeburke.springgame.game.ResolutionContextFactory;
import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.game.RunStatus;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.game.view.GameView.NarrationView;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.persistence.IntroductionStore;
import com.leeburke.springgame.persistence.RunSessionStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.WorldStore;

/**
 * Builds the player's {@link GameView} from the same interpretation context the Action Interpreter
 * would receive, so the view, the interpreter and slash commands share one alias scheme and one
 * visibility filter. Reads only: never calls AI and never writes.
 */
@Component
class GameViewAssembler {

	private final RunSessionStore sessions;
	private final GameStateLoader loader;
	private final ResolutionContextFactory contexts;
	private final IntroductionStore introductions;
	private final TurnStore turns;
	private final WorldContentCatalog world;
	private final WorldStore worldStore;
	private final StoredResponses responses = new StoredResponses();

	GameViewAssembler(RunSessionStore sessions, GameStateLoader loader, ResolutionContextFactory contexts,
			IntroductionStore introductions, TurnStore turns, WorldContentCatalog world, WorldStore worldStore) {
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.loader = Objects.requireNonNull(loader, "loader");
		this.contexts = Objects.requireNonNull(contexts, "contexts");
		this.introductions = Objects.requireNonNull(introductions, "introductions");
		this.turns = Objects.requireNonNull(turns, "turns");
		this.world = Objects.requireNonNull(world, "world");
		this.worldStore = Objects.requireNonNull(worldStore, "worldStore");
	}

	/** The current view, as GET shows it. */
	@Transactional(readOnly = true)
	public GameView view(UUID runId) {
		RunSession session = activeOrFinished(runId);
		Optional<TurnStore.TurnRecord> latest = turns.findLatestCommitted(runId);
		boolean finalizing = latest.filter(t -> t.status() == TurnStatus.MECHANICS_COMMITTED).isPresent();
		Optional<GameView.LastTurnView> lastTurn = latest.map(t -> new GameView.LastTurnView(t.turnNumber().getAsInt(),
				t.responseJson().flatMap(responses::narration).orElse(null)));
		return assemble(session, finalizing, lastTurn);
	}

	/** The view a turn's finalisation returns: that turn is the last one and is no longer finalizing. */
	@Transactional(readOnly = true)
	public GameView viewAfter(UUID runId, GameView.LastTurnView lastTurn) {
		return assemble(activeOrFinished(runId), false, Optional.of(lastTurn));
	}

	private RunSession activeOrFinished(UUID runId) {
		RunSession session = sessions.find(runId).orElseThrow(() -> new GameException(ErrorCode.RUN_NOT_FOUND, "Run not found."));
		if (session.status() == RunStatus.INITIALIZING) {
			throw new GameException(ErrorCode.RUN_INITIALIZING, "The run is still being created; retry its creation request.");
		}
		return session;
	}

	private GameView assemble(RunSession session, boolean finalizing, Optional<GameView.LastTurnView> lastTurn) {
		GameSnapshot snapshot = loader.load(session);
		InterpretationSetup setup = contexts.interpretation(snapshot);
		ActionInterpretationContext context = setup.context();
		Map<String, String> zoneNames = context.zones().stream()
				.collect(Collectors.toMap(ActionInterpretationContext.Zone::alias, ActionInterpretationContext.Zone::name));

		String awaiting = session.status() != RunStatus.ACTIVE ? "NONE" : snapshot.pending().isPresent() ? "DEFENSE" : "ACTION";
		NarrationView introduction = introductions.find(session.runId())
				.map(i -> new NarrationView(i.text(), i.source().name())).orElse(null);
		GameView.LocationView location = new GameView.LocationView(regionName(snapshot), SceneNames.scene(world, snapshot.scene()),
				new GameView.ZoneView(context.currentZone(), zoneNames.get(context.currentZone())));
		GameView.SceneView scene = new GameView.SceneView(
				context.zones().stream().map(z -> new GameView.ZoneView(z.alias(), z.name())).toList(),
				context.connections().stream().map(c -> new GameView.ConnectionView(c.zoneA(), c.zoneB())).toList(),
				context.entities().stream().map(e -> new GameView.CreatureView(e.alias(), e.name(), e.zone(), e.condition().name())).toList(),
				things(context.objects()), things(context.hazards()),
				context.exits().stream().map(x -> new GameView.ExitView(x.alias(), x.zone())).toList());
		GameView.PendingAttackView pending = snapshot.pending().map(p -> pendingView(p, setup)).orElse(null);
		return new GameView(session.runId(), session.status().name(), session.stateVersion(), awaiting, finalizing,
				introduction, character(snapshot.player(), context), location, scene, pending, lastTurn.orElse(null));
	}

	private String regionName(GameSnapshot snapshot) {
		return snapshot.scene().regionInstanceId()
				.map(id -> worldStore.findRegion(id).orElseThrow(() -> new IllegalStateException("Region " + id + " is missing")))
				.map(region -> world.findRegion(region.definitionCode()).map(RegionDefinition::displayName)
						.orElseThrow(() -> new IllegalStateException("Unknown region " + region.definitionCode())))
				.orElse(null);
	}

	private static GameView.PendingAttackView pendingView(PendingAttack pending, InterpretationSetup setup) {
		String alias = setup.aliases().aliasOf(AliasKind.ATTACK, pending.attack().ref()).orElseThrow();
		String attacker = setup.context().incomingAttacks().stream().filter(a -> a.alias().equals(alias)).findFirst()
				.flatMap(ActionInterpretationContext.IncomingAttackSummary::attacker)
				.flatMap(creature -> setup.context().entities().stream().filter(e -> e.alias().equals(creature)).findFirst())
				.map(ActionInterpretationContext.Creature::name).orElse("something unseen");
		return new GameView.PendingAttackView(alias, attacker, pending.cueText(),
				pending.narration().map(n -> new NarrationView(n.text(), n.source().name())).orElse(null));
	}

	private static GameView.CharacterView character(PlayerCharacterState player, ActionInterpretationContext context) {
		Map<String, Integer> stats = new LinkedHashMap<>();
		for (StatType stat : StatType.values()) {
			stats.put(stat.name(), player.stats().get(stat).value());
		}
		return new GameView.CharacterView(player.name(), player.currentHp(), player.maxHp(), stats, player.fated().value(),
				FatedBand.of(player.fated().value()).name(),
				context.playerBody().stream().map(b -> new GameView.BodyPartView(b.part().name(), b.severity().name())).toList(),
				owned(context.weapons()), owned(context.items()), owned(context.abilities()), player.passive().displayName());
	}

	private static List<GameView.OwnedView> owned(List<ActionInterpretationContext.Owned> owned) {
		return owned.stream().map(o -> new GameView.OwnedView(o.alias(), o.name())).toList();
	}

	private static List<GameView.ThingView> things(List<ActionInterpretationContext.Thing> things) {
		return things.stream().map(t -> new GameView.ThingView(t.alias(), t.name(), t.zone())).toList();
	}
}
