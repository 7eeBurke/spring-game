package com.leeburke.springgame.narratorbenchmark;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.ENEMIES;
import static com.leeburke.springgame.enemy.EnemyFixtures.WORLD;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.resolution.ActionEngine;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.DisabledAiProvider;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.AliasKind;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.NarrationMode;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContext;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder.Arrival;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder.ArrivalView;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder.SceneKnowledge;
import com.leeburke.springgame.ai.narration.Surroundings;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.game.GameSnapshot;
import com.leeburke.springgame.game.ResolutionContextFactory;
import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.game.RunStatus;
import com.leeburke.springgame.game.TurnRandom;
import com.leeburke.springgame.run.initialization.RunInitialization;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.RunWorldGenerator;
import com.leeburke.springgame.world.view.ExplorationLeads;

/**
 * The eight benchmark scenarios, each an authoritative Outcome Narrator context produced by the
 * game's own code: a seeded run world (as a new run generates it), real slash commands interpreted
 * and validated, resolved by the real {@link ActionEngine} (fixed d20 faces for combat), and turned
 * into narration facts by the production {@link OutcomeNarrationContextBuilder}. Nothing about the
 * world is written by hand: places, passages, objects, items and enemies come from the bundled
 * content and the generator. No Spring, no database, no AI.
 * <p>
 * The scene state between scenarios follows what the game would have recorded (seen and visited
 * zones, the crate opened and then emptied). Exit labels and scene names follow the rules of the
 * game's {@code ExitLabels} and {@code SceneNames} (which need the database, so they are restated).
 */
public final class BenchmarkScenarios {

	/** Suggested pacing for a scenario: a guideline, never a target to pad toward. */
	public record Pace(String kind, int minWords, int maxWords) {
	}

	/**
	 * @param wording what the player typed (the narrator receives it as untrusted context)
	 * @param how     how the context was produced (seed, command, rolls)
	 */
	public record Scenario(int number, String title, Pace pace, String wording, String how, OutcomeNarrationContext context) {
	}

	static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-0000000be4c4");
	private static final String HUB_ROAD = "road_to_chapel";
	private static final String ROAD_BACK = "lantern_road";
	private static final String FIRST_FIND = "first_find";

	private final long seed;
	private final RunInitialization init;
	private final PlayerCharacterState player;
	private final ResolutionContextFactory contexts = new ResolutionContextFactory(
			new InterpretationContextBuilder(WORLD, code -> CONTENT.findItem(code).map(i -> i.displayName()).orElse(code)), ENEMIES,
			CONTENT);
	private final ActionInterpreter commands = new ActionInterpreter(new DisabledAiProvider(AiFailureKind.DISABLED), "unused", 1,
			new AiGenerationSettings("none", 100, Duration.ofSeconds(1), Optional.empty()));
	private final OutcomeNarrationContextBuilder narration = new OutcomeNarrationContextBuilder(CONTENT, WORLD);

	private BenchmarkScenarios(long seed) {
		this.seed = seed;
		long[] ids = { 0 };
		this.init = new RunInitializer(new RunWorldGenerator(WORLD, () -> new UUID(0, ++ids[0])), new EnemyRosterGenerator(ENEMIES))
				.initialize(RUN, seed, GenerationContextSnapshot.empty());
		this.player = PlayerCharacterState.from(new PlayerCharacterGenerator(CONTENT, new PlayerStatGenerator())
				.generate(TurnRandom.character(seed)));
	}

	/** The first seed (from 0) that gives every scenario its subject; deterministic. */
	public static long chosenSeed() {
		for (long seed = 0; seed < 5000; seed++) {
			if (new BenchmarkScenarios(seed).suitable()) {
				return seed;
			}
		}
		throw new IllegalStateException("No seed below 5000 fits the benchmark");
	}

	public static List<Scenario> build() {
		return new BenchmarkScenarios(chosenSeed()).scenarios();
	}

	/** A quiet Ruined Nave behind the doors, the salve in the first crate, the Bell Passage beyond, and an enemy somewhere. */
	private boolean suitable() {
		SceneInstance entry = entry();
		return entry.definitionCode().equals("RUINED_NAVE") && entry.state().entities().isEmpty()
				&& entry.state().container(FIRST_FIND).map(c -> c.contents().equals(List.of("RESTORATIVE_SALVE"))).orElse(false)
				&& onwardTo(entry, "BELL_PASSAGE").isPresent()
				&& init.world().region().scenes().stream().anyMatch(s -> !visibleEnemies(s).isEmpty());
	}

	private List<Scenario> scenarios() {
		List<Scenario> scenarios = new ArrayList<>();
		SceneInstance hub = init.world().hub();
		SceneInstance entry = entry();
		Set<UUID> hubAndNave = Set.of(hub.id(), entry.id());
		String door = exit(entry, ROAD_BACK).zoneId();
		String aisle = entry.state().objects().stream().filter(o -> o.id().equals(FIRST_FIND)).findFirst().orElseThrow().zoneId();

		// 1. Through the west doors: from the road's end into the first scene.
		SceneState road = hub.state().seeing(List.of("lantern_hearth", "chapel_road")).visiting("lantern_hearth").visiting("chapel_road");
		GameSnapshot atRoad = snapshot(with(hub, road), "chapel_road", List.of());
		scenarios.add(scenario(1, "Entering the Hollow Chapel for the first time", new Pace("New major scene", 100, 180),
				"I go in through the west doors", atRoad, "/move " + alias(atRoad, AliasKind.EXIT, HUB_ROAD),
				Optional.of(arrival(entry, door, hubAndNave, Optional.of(ROAD_BACK))),
				knowledge(atRoad, hub, Set.of(hub.id()), Optional.empty(), Optional.empty())));

		// The nave as the player knows it after coming in at the west end.
		SceneState inside = arrived(entry.state(), door);

		// 3. One place on: the west end to the central aisle.
		GameSnapshot atDoor = snapshot(with(entry, inside), door, List.of());
		scenarios.add(scenario(3, "Moving one zone within a known scene", new Pace("Short movement", 15, 45),
				"I walk up the central aisle", atDoor, "/move " + alias(atDoor, AliasKind.ZONE, aisle), Optional.empty(),
				knowledge(atDoor, entry, hubAndNave, Optional.empty(), Optional.empty())));

		// 4. Open the crate beside the aisle.
		SceneState atCrate = arrived(inside, aisle);
		GameSnapshot beforeOpen = snapshot(with(entry, atCrate), aisle, List.of());
		scenarios.add(scenario(4, "Opening the wooden crate (Restorative Salve inside)", new Pace("Opening a container", 10, 35),
				"I open the wooden crate", beforeOpen, "/open " + alias(beforeOpen, AliasKind.OBJECT, FIRST_FIND), Optional.empty(),
				knowledge(beforeOpen, entry, hubAndNave, Optional.empty(), Optional.empty())));

		// 5. Take the salve; the game tells what it looks like (no inspection of a carried item exists).
		SceneState opened = atCrate.withContainer(atCrate.container(FIRST_FIND).orElseThrow().opened());
		GameSnapshot beforeTake = snapshot(with(entry, opened), aisle, List.of());
		scenarios.add(scenario(5, "Taking the salve and examining it", new Pace("Taking an item, with its look", 15, 50),
				"I take the salve and examine it", beforeTake, "/take " + alias(beforeTake, AliasKind.OBJECT, FIRST_FIND),
				Optional.empty(), knowledge(beforeTake, entry, hubAndNave, Optional.empty(), Optional.empty())));

		// 6. Where haven't I been? Answered from what the player knows.
		SceneState emptied = opened.withContainer(opened.container(FIRST_FIND).orElseThrow().without("RESTORATIVE_SALVE"));
		GameSnapshot asking = snapshot(with(entry, emptied), aisle, List.of());
		ExplorationLeads leads = ExplorationLeads.of(asking.view(), emptied.visitedZones(), undiscovered(entry, hubAndNave));
		scenarios.add(scenario(6, "Asking which unexplored ways remain", new Pace("Answering where to go", 25, 80),
				"Is there a way I haven't gone?", asking, "/search", Optional.empty(),
				knowledge(asking, entry, hubAndNave, Optional.empty(), Optional.of(leads))));

		// 7. A second look around, with nothing changed since the last one.
		OutcomeNarrationContext firstLook = context(asking, "/watch", "I look around", Optional.empty(),
				knowledge(asking, entry, hubAndNave, Optional.empty(), Optional.empty()));
		var lastLook = firstLook.facts().stream().filter(NarrationFact.Perceived.class::isInstance)
				.map(f -> ((NarrationFact.Perceived) f).perception()).findFirst();
		scenarios.add(scenario(7, "Looking around again when nothing has changed", new Pace("Repeated look, unchanged", 5, 25),
				"I look around again", asking, "/watch", Optional.empty(), knowledge(asking, entry, hubAndNave, lastLook, Optional.empty())));

		// 2. On from the apse into the Bell Passage (the nave fully walked by then).
		SceneState walked = emptied;
		for (var zone : entry.state().zones()) {
			walked = walked.seeing(List.of(zone.id())).visiting(zone.id());
		}
		SceneExit toBells = onwardTo(entry, "BELL_PASSAGE").orElseThrow();
		SceneInstance bells = scene(toBells.destinationSceneId());
		GameSnapshot atApse = snapshot(with(entry, walked), toBells.zoneId(), List.of());
		String backZone = bells.state().exits().stream().filter(x -> x.destinationSceneId().equals(entry.id())).findFirst().orElseThrow()
				.zoneId();
		String backExit = bells.state().exits().stream().filter(x -> x.destinationSceneId().equals(entry.id())).findFirst().orElseThrow()
				.id();
		String crossing = "/move " + alias(atApse, AliasKind.EXIT, toBells.id());
		Optional<Arrival> intoBells = Optional.of(arrival(bells, backZone, Set.of(hub.id(), entry.id(), bells.id()), Optional.of(backExit)));
		SceneKnowledge fromTheNave = knowledge(atApse, entry, hubAndNave, Optional.empty(), Optional.empty());
		// The player's words name the passage they actually go through.
		String through = ((NarrationFact.CrossedInto) context(atApse, crossing, "I go through", intoBells, fromTheNave).facts().getFirst())
				.through();
		scenarios.add(scenario(2, "Arriving in a different new scene: the Bell Passage", new Pace("New major scene", 100, 180),
				"I go through " + through, atApse, crossing, intoBells, fromTheNave));

		// 8. A blow in a fight: standing with a visible enemy, a slash with a fixed d20.
		SceneInstance fight = init.world().region().scenes().stream().filter(s -> !visibleEnemies(s).isEmpty()).findFirst().orElseThrow();
		EnemyInstance foe = visibleEnemies(fight).getFirst();
		String foeZone = fight.state().entities().stream().filter(e -> e.id().equals(foe.entityId())).findFirst().orElseThrow().zoneId();
		GameSnapshot squaring = snapshot(with(fight, arrived(fight.state(), foeZone)), foeZone, init.enemies().inScene(fight.id()));
		String foeName = WORLD.findElement(foe.definitionCode()).orElseThrow().displayName();
		scenarios.add(scenario(8, "A confirmed combat action (a slash that lands)", new Pace("Ordinary combat action", 20, 60),
				"I slash at the " + foeName.toLowerCase(java.util.Locale.ROOT) + " with my weapon", squaring,
				"/attack " + alias(squaring, AliasKind.ENTITY, foe.entityId()) + " slash with weapon_1", Optional.empty(),
				knowledge(squaring, fight, Set.of(hub.id(), entry.id(), fight.id()), Optional.empty(), Optional.empty()), 15));

		scenarios.sort(java.util.Comparator.comparingInt(Scenario::number));
		return List.copyOf(scenarios);
	}

	// --- one scenario ---

	private Scenario scenario(int number, String title, Pace pace, String wording, GameSnapshot snapshot, String command,
			Optional<Arrival> arrival, SceneKnowledge knowledge, int... d20) {
		OutcomeNarrationContext context = context(snapshot, command, wording, arrival, knowledge, d20);
		String how = "seed " + seed + ", command `" + command + "`" + (d20.length > 0 ? ", d20 " + java.util.Arrays.toString(d20) : "");
		return new Scenario(number, title, pace, wording, how, context);
	}

	private OutcomeNarrationContext context(GameSnapshot snapshot, String command, String wording, Optional<Arrival> arrival,
			SceneKnowledge knowledge, int... d20) {
		ActionInterpretationResult result = commands.interpret(command, contexts.interpretation(snapshot));
		if (!(result instanceof ActionInterpretationResult.Interpreted interpreted)) {
			throw new IllegalStateException(command + " was not interpreted: " + result);
		}
		ValidatedActionIntent validated = interpreted.validated();
		ResolvedOutcome outcome = new ActionEngine().resolve(validated, contexts.resolution(snapshot, validated), new Rolls(d20));
		return narration.build(outcome, validated, Map.of(), NarrationMode.NORMAL, Optional.empty(), Optional.of(wording), arrival,
				knowledge);
	}

	// --- the world as the game would hold it ---

	private SceneInstance entry() {
		return scene(init.world().region().entrySceneId());
	}

	private SceneInstance scene(UUID id) {
		if (init.world().hub().id().equals(id)) {
			return init.world().hub();
		}
		return init.world().region().scenes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
	}

	private Optional<SceneExit> onwardTo(SceneInstance from, String archetype) {
		return from.state().exits().stream()
				.filter(x -> !x.id().equals(ROAD_BACK))
				.filter(x -> init.world().region().scenes().stream().anyMatch(s -> s.id().equals(x.destinationSceneId())
						&& s.definitionCode().equals(archetype)))
				.findFirst();
	}

	private static SceneExit exit(SceneInstance scene, String id) {
		return scene.state().exits().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
	}

	private List<EnemyInstance> visibleEnemies(SceneInstance scene) {
		return init.enemies().inScene(scene.id()).stream().filter(e -> !scene.state().isHidden(HiddenContentKind.ENTITY, e.entityId()))
				.sorted(java.util.Comparator.comparing(EnemyInstance::entityId)).toList();
	}

	/** Standing somewhere: it and the places beside it are seen, and it is visited. */
	private static SceneState arrived(SceneState state, String zone) {
		return state.seeing(com.leeburke.springgame.world.SceneKnowledge.perceivable(state, zone)).visiting(zone);
	}

	private static SceneInstance with(SceneInstance scene, SceneState state) {
		return new SceneInstance(scene.id(), scene.runId(), scene.definitionCode(), scene.placement(), true, scene.revision(), state);
	}

	private GameSnapshot snapshot(SceneInstance scene, String zone, List<EnemyInstance> enemies) {
		return new GameSnapshot(new RunSession(RUN, RunStatus.ACTIVE, 0, 0, Optional.empty()), seed, player,
				new PlayerLocation(scene.id(), zone), scene, enemies, Optional.empty());
	}

	private String alias(GameSnapshot snapshot, AliasKind kind, String id) {
		return contexts.interpretation(snapshot).aliases().aliasOf(kind, id)
				.orElseThrow(() -> new IllegalStateException("No " + kind + " alias for " + id));
	}

	private SceneKnowledge knowledge(GameSnapshot snapshot, SceneInstance scene, Set<UUID> discovered,
			Optional<com.leeburke.springgame.ai.narration.Perception> lastLook, Optional<ExplorationLeads> leads) {
		return new SceneKnowledge(snapshot.fallenVisibleEnemyIds(), labels(scene, discovered), scene.definitionCode(), lastLook, leads);
	}

	private Arrival arrival(SceneInstance destination, String zone, Set<UUID> discovered, Optional<String> cameIn) {
		SceneState state = arrived(destination.state(), zone);
		SceneInstance there = with(destination, state);
		GameSnapshot arrived = snapshot(there, zone, init.enemies().inScene(destination.id()));
		String zoneName = state.zones().stream().filter(z -> z.id().equals(zone)).findFirst().orElseThrow().displayName();
		return new Arrival(sceneName(destination), zoneName, Optional.of(new ArrivalView(destination.definitionCode(), arrived.view(),
				arrived.fallenVisibleEnemyIds(), labels(destination, discovered), cameIn)));
	}

	/** Known exits whose destination is not yet discovered. */
	private static Set<String> undiscovered(SceneInstance scene, Set<UUID> discovered) {
		Set<String> ways = new java.util.LinkedHashSet<>();
		scene.state().exits().stream().filter(x -> !discovered.contains(x.destinationSceneId())).forEach(x -> ways.add(x.id()));
		return ways;
	}

	/** Exit labels as the game's ExitLabels gives them: the hub's road by its region, discovered places by name, else unexplored. */
	private Map<String, String> labels(SceneInstance scene, Set<UUID> discovered) {
		Map<String, String> labels = new LinkedHashMap<>();
		for (SceneExit exit : scene.state().exits()) {
			if (scene.state().isHidden(HiddenContentKind.EXIT, exit.id())) {
				continue;
			}
			if (scene.kind() == SceneKind.HUB && exit.id().equals(HUB_ROAD)) {
				labels.put(exit.id(), "the road to the Hollow Chapel");
			} else if (discovered.contains(exit.destinationSceneId())) {
				String name = sceneName(scene(exit.destinationSceneId()));
				labels.put(exit.id(), "the way to " + (name.startsWith("The ") ? name : "the " + name));
			} else {
				labels.put(exit.id(), Surroundings.UNEXPLORED);
			}
		}
		return labels;
	}

	/** Scene names as the game's SceneNames gives them: the fixed scene's or archetype's display name. */
	private static String sceneName(SceneInstance scene) {
		return WORLD.findArchetype(scene.definitionCode()).map(a -> a.displayName())
				.or(() -> WORLD.findFixedScene(scene.definitionCode()).map(f -> f.displayName())).orElse(scene.definitionCode());
	}

	/** Preset d20 faces; anything else fails (so a scenario never draws a roll it did not declare). */
	private static final class Rolls implements RandomGenerator {
		private final Deque<Integer> faces = new ArrayDeque<>();

		Rolls(int... faces) {
			for (int face : faces) {
				this.faces.add(face);
			}
		}

		@Override
		public int nextInt(int origin, int bound) {
			if (faces.isEmpty()) {
				throw new IllegalStateException("More rolls drawn than supplied");
			}
			return faces.poll();
		}

		@Override
		public long nextLong() {
			throw new IllegalStateException("Only d20 rolls are expected");
		}
	}
}
