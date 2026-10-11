package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.game.service.GameDriver.Reply;
import com.leeburke.springgame.game.service.GameTestConfiguration.SwitchableAi;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKnowledge;
import com.leeburke.springgame.world.SceneObject;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The first Hollow Chapel area as a new player meets it, through exact commands and scripted
 * interpreter answers (never a live model): discover the guaranteed crate, look at it, reach for it
 * from too far (refused, no turn spent), walk to it, open it, take what is inside, and find it gone.
 * Every change is persisted, replays grant nothing twice, and the player's view never holds more
 * than they have seen.
 */
@SpringBootTest(properties = { "game.api.invite-codes=" + GameDriver.INVITE, "game.api.limits.turns-per-minute-per-run=10000",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class HollowChapelExplorationIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Autowired
	private RunCreationService creation;
	@Autowired
	private TurnService turns;
	@Autowired
	private GameViewService views;
	@Autowired
	private WorldStore world;
	@Autowired
	private EnemyStore enemies;
	@Autowired
	private GameRunStore runs;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private SwitchableAi ai;

	private GameDriver game;

	@BeforeEach
	void setUp() {
		game = new GameDriver(creation, turns, views, world, enemies, runs, jdbc);
	}

	@AfterEach
	void resetAi() {
		ai.reset();
	}

	private JsonNode facts(UUID runId, int turn) {
		return JSON.readTree(jdbc.queryForObject("SELECT mechanics_summary -> 'narration' -> 'facts' FROM run_turn WHERE run_id = ? "
				+ "AND turn_number = ?", String.class, runId, turn));
	}

	private int lastTurn(UUID runId) {
		return game.view(runId).lastTurn().turnNumber();
	}

	/** Into the first Hollow Chapel scene, through the road. */
	private GameView enterTheChapel(UUID runId) {
		assertThat(game.turn(runId, "/move zone_1").status()).isEqualTo(200);
		Reply entered = game.turn(runId, "/move exit_1");
		assertThat(entered.status()).as(entered.raw()).isEqualTo(200);
		GameView inside = game.view(runId);
		assertThat(inside.location().region()).isEqualTo("Hollow Chapel");
		return inside;
	}

	/** The guaranteed first find: the first crate in alias order (its local ID sorts before any other crate's). */
	private static GameView.ThingView firstCrate(GameView view) {
		return view.scene().objects().stream().filter(o -> o.name().equals("Crate")).findFirst().orElseThrow();
	}

	private SceneInstance currentScene(UUID runId) {
		return world.findScene(world.findPlayerLocation(runId).orElseThrow().sceneId()).orElseThrow();
	}

	/** A new run, inside its first chapel scene, which has no creature in it (so no enemy turn interrupts). */
	private UUID quietRunInsideTheChapel() {
		for (int i = 0; i < 40; i++) {
			UUID candidate = game.newRun();
			enterTheChapel(candidate);
			if (currentScene(candidate).state().entities().isEmpty()) {
				return candidate;
			}
		}
		throw new AssertionError("No run in 40 had a quiet first chapel scene");
	}

	// --- Action documents, as the interpreter would answer (scripted; never a live model) ---

	private static String target(String kind, String alias) {
		return "{\"kind\":\"" + kind + "\",\"alias\":\"" + alias + "\",\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}";
	}

	private static String step(String relation, String action, String slot, String payload) {
		StringBuilder json = new StringBuilder("{\"relation\":\"" + relation + "\",\"action\":\"" + action + "\"");
		for (String s : List.of("attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate")) {
			json.append(",\"").append(s).append("\":").append(s.equals(slot) ? payload : "null");
		}
		return json.append('}').toString();
	}

	private static String closeDistance(String objectAlias) {
		return step("START", "MOVE", "move", "{\"movementType\":\"CLOSE_DISTANCE\",\"target\":" + target("OBJECT", objectAlias)
				+ ",\"goal\":\"NONE\",\"approach\":\"NORMAL\"}");
	}

	private static String open(String objectAlias) {
		return step("THEN", "INTERACT", "interact", "{\"kind\":\"OPEN\",\"target\":" + target("OBJECT", objectAlias)
				+ ",\"carried\":null,\"approach\":\"NORMAL\"}");
	}

	private void interpreterAnswers(String... steps) {
		CountingAi model = new CountingAi();
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":["
				+ String.join(",", steps) + "],\"unresolved\":[]}");
		ai.use(model);
	}

	@Test
	void aNewPlayerFindsApproachesInspectsOpensAndTakesTheFirstFind() {
		UUID runId = quietRunInsideTheChapel();
		GameView inside = game.view(runId);
		int beltBefore = runs.findRun(runId).orElseThrow().playerCharacter().toolBelt().entries().size();
		assertThat(beltBefore).as("a new character has room on the belt").isLessThan(5);

		// Discover: arriving, the crate in the next place is in sight, closed, and one step away.
		GameView.ThingView crate = firstCrate(inside);
		assertThat(crate.container()).isEqualTo("closed");
		assertThat(crate.reach()).isEqualTo("one step away");

		// Inspect from here: its own description and state, no movement.
		String zoneBefore = inside.location().zone().name();
		assertThat(game.turn(runId, "/inspect " + crate.alias()).status()).isEqualTo(200);
		JsonNode inspected = facts(runId, lastTurn(runId)).get(0);
		assertThat(inspected.path("fact").asString()).isEqualTo("Inspected");
		assertThat(inspected.path("inSight").asBoolean()).isTrue();
		assertThat(inspected.path("description").asString()).startsWith("A wooden crate");
		assertThat(inspected.path("state").asString()).isEqualTo("closed");
		assertThat(game.view(runId).location().zone().name()).isEqualTo(zoneBefore);

		// Opening it from here cannot even begin: refused with where it is, no turn spent, nothing changed.
		GameView before = game.view(runId);
		UUID tooFarKey = UUID.randomUUID();
		Reply tooFar = game.turn(runId, tooFarKey, "/open " + crate.alias(), before.stateVersion());
		assertThat(tooFar.status()).as(tooFar.raw()).isEqualTo(422);
		assertThat(tooFar.body().path("error").path("code").asString()).isEqualTo("ACTION_NOT_SUPPORTED");
		assertThat(tooFar.body().path("error").path("reason").asString()).isEqualTo("OUT_OF_REACH");
		assertThat(tooFar.body().path("error").path("message").asString()).startsWith("The crate is in ").endsWith("Go there first.");
		GameView after = game.view(runId);
		assertThat(after.stateVersion()).isEqualTo(before.stateVersion());
		assertThat(after.lastTurn().turnNumber()).isEqualTo(before.lastTurn().turnNumber());
		assertThat(firstCrate(after).container()).isEqualTo("closed");
		// Its replay gives the same refusal.
		assertThat(game.turn(runId, tooFarKey, "/open " + crate.alias(), before.stateVersion()).raw()).isEqualTo(tooFar.raw());

		// Approach: one step, and it is within reach.
		assertThat(game.turn(runId, "/move " + crate.alias()).status()).isEqualTo(200);
		assertThat(facts(runId, lastTurn(runId)).get(0).path("fact").asString()).isEqualTo("WalkedTo");
		GameView.ThingView beside = firstCrate(game.view(runId));
		assertThat(beside.reach()).isEqualTo("here");

		// Open: its contents come into sight, and stay open.
		Reply opened = game.turn(runId, "/open " + beside.alias());
		assertThat(opened.status()).as(opened.raw()).isEqualTo(200);
		JsonNode openFact = facts(runId, lastTurn(runId)).get(0);
		assertThat(openFact.path("fact").asString()).isEqualTo("OpenedContainer");
		assertThat(openFact.path("contents")).hasSize(1);
		String item = openFact.path("contents").get(0).path("item").asString();
		assertThat(List.of("Restorative Salve", "Bandage", "Torch")).contains(item);
		assertThat(openFact.path("contents").get(0).path("description").asString()).as("told with how it looks").isNotBlank();
		assertThat(firstCrate(game.view(runId)).container()).isEqualTo("open, holding " + item);
		assertThat(currentScene(runId).state().container("first_find").orElseThrow().open()).isTrue();

		// Take: onto the belt, out of the crate.
		UUID takeKey = UUID.randomUUID();
		long version = game.view(runId).stateVersion();
		Reply taken = game.turn(runId, takeKey, "/take " + beside.alias(), version);
		assertThat(taken.status()).as(taken.raw()).isEqualTo(200);
		JsonNode takeFact = facts(runId, lastTurn(runId)).get(0);
		assertThat(takeFact.path("fact").asString()).isEqualTo("TookItem");
		assertThat(takeFact.path("item").asString()).isEqualTo(item);
		assertThat(game.view(runId).character().items()).extracting(GameView.OwnedView::name).contains(item);
		assertThat(firstCrate(game.view(runId)).container()).isEqualTo("open and empty");
		assertThat(runs.findRun(runId).orElseThrow().playerCharacter().toolBelt().entries()).hasSize(beltBefore + 1);

		// A replay of the same request returns the stored answer and grants nothing twice.
		Reply replay = game.turn(runId, takeKey, "/take " + beside.alias(), version);
		assertThat(replay.raw()).isEqualTo(taken.raw());
		assertThat(runs.findRun(runId).orElseThrow().playerCharacter().toolBelt().entries()).hasSize(beltBefore + 1);

		// Taking again: there is nothing left, and nothing respawns.
		Reply again = game.turn(runId, "/take " + beside.alias());
		assertThat(again.body().path("overall").asString()).isEqualTo("FAILURE");
		assertThat(facts(runId, lastTurn(runId)).get(0).path("reason").asString()).isEqualTo("EMPTY");
		assertThat(runs.findRun(runId).orElseThrow().playerCharacter().toolBelt().entries()).hasSize(beltBefore + 1);
	}

	@Test
	void movingTowardTheFirstFindAndOpeningItWorksInOneTurnBecauseOneStepPutsItInReach() {
		UUID runId = quietRunInsideTheChapel();
		GameView.ThingView crate = firstCrate(game.view(runId));
		assertThat(crate.reach()).isEqualTo("one step away");
		interpreterAnswers(closeDistance(crate.alias()), open(crate.alias()));

		Reply reply = game.turn(runId, "I move toward the crate and see if I can open it");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		JsonNode facts = facts(runId, lastTurn(runId));
		assertThat(facts.get(0).path("fact").asString()).isEqualTo("WalkedTo");
		assertThat(facts.get(1).path("fact").asString()).isEqualTo("OpenedContainer");
		assertThat(firstCrate(game.view(runId)).reach()).isEqualTo("here");
		assertThat(firstCrate(game.view(runId)).container()).startsWith("open, holding ");
		assertThat(currentScene(runId).state().container("first_find").orElseThrow().open()).isTrue();
	}

	@Test
	void twoStepsAwayTheStepIsKeptAndTheCrateIsToldAsStillOutOfReach() {
		// A quiet scene with a crate, and an empty place two passages from it where the player stands.
		UUID runId = null;
		String objectId = null;
		for (int i = 0; i < 60 && runId == null; i++) {
			UUID candidate = game.newRun();
			for (SceneInstance scene : game.scenes(candidate)) {
				if (!scene.state().entities().isEmpty()) {
					continue;
				}
				for (SceneObject object : scene.state().objects()) {
					if (scene.state().container(object.id()).isEmpty()
							|| scene.state().isHidden(com.leeburke.springgame.world.HiddenContentKind.OBJECT, object.id())) {
						continue;
					}
					Optional<String> stand = SceneKnowledge.steps(scene.state().allKnown(), object.zoneId()).entrySet().stream()
							.filter(e -> e.getValue() == 2).map(Map.Entry::getKey).sorted().findFirst();
					if (stand.isPresent() && runId == null) {
						SceneInstance placed = game.place(candidate, s -> s.id().equals(scene.id()));
						world.setPlayerLocation(candidate, new PlayerLocation(placed.id(), stand.get()));
						runId = candidate;
						objectId = object.id();
					}
				}
				if (runId != null) {
					break;
				}
			}
		}
		assertThat(runId).as("a quiet scene with a crate two passages from an empty place").isNotNull();
		GameView view = game.view(runId);
		GameView.ThingView crate = view.scene().objects().stream().filter(o -> o.container() != null && "two steps away".equals(o.reach()))
				.findFirst().orElseThrow();
		String zoneBefore = view.location().zone().name();
		int turnBefore = view.lastTurn() == null ? 0 : view.lastTurn().turnNumber();
		interpreterAnswers(closeDistance(crate.alias()), open(crate.alias()));

		Reply reply = game.turn(runId, "I move toward the crate and see if I can open it");

		// The step forward is real progress and is kept; the open could not begin, and is told as out of reach.
		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(lastTurn(runId)).isEqualTo(turnBefore + 1);
		GameView after = game.view(runId);
		assertThat(after.location().zone().name()).isNotEqualTo(zoneBefore);
		JsonNode facts = facts(runId, lastTurn(runId));
		assertThat(facts.get(0).path("fact").asString()).isEqualTo("WalkedTo");
		assertThat(facts.get(1).path("fact").asString()).isEqualTo("InteractionFailed");
		assertThat(facts.get(1).path("reason").asString()).isEqualTo("OUT_OF_REACH");
		assertThat(facts.get(1).path("where").asString()).as("told where it is, as a place the player knows").isNotBlank();
		assertThat(currentScene(runId).state().container(objectId).orElseThrow().open()).isFalse();
	}

	@Test
	void aPendingDefenseIsRefusedFirstBeforeAnyInteraction() {
		java.util.function.Predicate<SceneInstance> withSomethingToOpen = s -> !game.visibleEnemies(s).isEmpty()
				&& s.state().objects().stream().anyMatch(o -> s.state().container(o.id()).isPresent()
						&& !s.state().isHidden(com.leeburke.springgame.world.HiddenContentKind.OBJECT, o.id()));
		UUID runId = null;
		for (int i = 0; i < 100 && runId == null; i++) {
			UUID candidate = game.newRun();
			if (game.scenes(candidate).stream().anyMatch(withSomethingToOpen)) {
				game.placeAmongEnemies(candidate, withSomethingToOpen);
				runId = candidate;
			}
		}
		assertThat(runId).as("a run with enemies and a crate in one scene").isNotNull();
		GameView pending = game.untilPending(runId);
		String crate = pending.scene().objects().stream().filter(o -> o.container() != null).findFirst().orElseThrow().alias();

		Reply refused = game.turn(runId, "/open " + crate);

		assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
		assertThat(refused.body().path("error").path("code").asString()).isEqualTo("DEFENSE_REQUIRED");
		assertThat(game.view(runId).awaiting()).isEqualTo("DEFENSE");
	}

	@Test
	void theFirstFindIsAlwaysThereAndUsefulInNewRuns() {
		for (int i = 0; i < 5; i++) {
			UUID runId = game.newRun();
			GameView inside = enterTheChapel(runId);
			GameView.ThingView crate = firstCrate(inside);
			assertThat(crate.reach()).isIn("here", "one step away");
			assertThat(currentScene(runId).state().container("first_find").orElseThrow().contents()).hasSize(1);
		}
	}

	@Test
	void arrivingRevealsOnlyWhatThePlayerCanPerceiveAndNothingInsideClosedContainers() {
		UUID runId = game.newRun();
		GameView inside = enterTheChapel(runId);
		SceneInstance scene = currentScene(runId);
		String zone = world.findPlayerLocation(runId).orElseThrow().zoneId();

		Set<String> perceivable = SceneKnowledge.perceivable(scene.state(), zone);
		Set<String> labels = scene.state().zones().stream().filter(z -> perceivable.contains(z.id()))
				.map(z -> z.displayName()).collect(Collectors.toSet());
		assertThat(inside.scene().zones()).extracting(GameView.ZoneView::name).containsExactlyInAnyOrderElementsOf(labels);
		// Nothing further in: every creature and object shown is in a zone the player can perceive.
		assertThat(inside.scene().creatures()).allMatch(c -> inside.scene().zones().stream().anyMatch(z -> z.alias().equals(c.zone())));
		assertThat(inside.scene().objects()).allMatch(o -> inside.scene().zones().stream().anyMatch(z -> z.alias().equals(o.zone())));
		// A closed crate's contents are nowhere in the view.
		String item = scene.state().container("first_find").orElseThrow().contents().getFirst();
		String raw = JSON.writeValueAsString(inside);
		String itemName = switch (item) {
			case "RESTORATIVE_SALVE" -> "Restorative Salve";
			case "BANDAGE" -> "Bandage";
			default -> "Torch";
		};
		assertThat(raw).doesNotContain("\"" + item + "\"").doesNotContain("holding " + itemName);
		// What was perceived is remembered: the scene now records it.
		assertThat(scene.state().seenZones().orElseThrow()).containsAll(perceivable);
	}

	@Test
	void anOldSaveWithNoContainerStateStillLoadsAndItsCratesAreEmpty() {
		UUID runId = game.newRun();
		SceneInstance withCrate = game.place(runId, s -> s.state().objects().stream().anyMatch(o -> o.definitionCode().equals("CRATE")));
		String crateId = withCrate.state().objects().stream().filter(o -> o.definitionCode().equals("CRATE")).findFirst().orElseThrow().id();
		String crateZone = withCrate.state().objects().stream().filter(o -> o.id().equals(crateId)).findFirst().orElseThrow().zoneId();
		world.setPlayerLocation(runId, new com.leeburke.springgame.world.PlayerLocation(withCrate.id(), crateZone));
		// Stored as before containers existed: schema version 1, with neither containers nor seen zones.
		jdbc.update("UPDATE scene_instance SET state_schema_version = 1, state = state - 'containers' - 'allSeen' - 'seenZones' - 'visitsRecorded' - 'visitedZones' "
				+ "WHERE id = ?", withCrate.id());
		jdbc.update("UPDATE scene_instance SET state = jsonb_set(state, '{hiddenContent}', "
				+ "(SELECT coalesce(jsonb_agg(h), '[]'::jsonb) FROM jsonb_array_elements(state -> 'hiddenContent') h "
				+ "WHERE h ->> 'localId' <> ?)) WHERE id = ?", crateId, withCrate.id());

		GameView view = game.view(runId);
		GameView.ThingView crate = view.scene().objects().stream().filter(o -> o.name().startsWith("Crate") && o.reach().equals("here"))
				.findFirst().orElseThrow();
		assertThat(crate.container()).isEqualTo("closed");

		Reply opened = game.turn(runId, "/open " + crate.alias());
		assertThat(opened.status()).as(opened.raw()).isEqualTo(200);
		JsonNode fact = facts(runId, lastTurn(runId)).get(0);
		assertThat(fact.path("fact").asString()).isEqualTo("OpenedContainer");
		assertThat(fact.path("contents")).as("nothing is invented for an old run").isEmpty();
		assertThat(jdbc.queryForObject("SELECT state_schema_version FROM scene_instance WHERE id = ?", Integer.class, withCrate.id()))
				.isEqualTo(3);
	}
}
