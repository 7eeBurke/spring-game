package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.game.service.GameDriver.Reply;
import com.leeburke.springgame.game.service.GameTestConfiguration.SwitchableAi;
import com.leeburke.springgame.game.view.ChronicleView;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.SceneInstance;

/**
 * Regression for the first real playtest (character Hesk). The interpreter's answers are scripted:
 * where step 0 found what the model actually produced (from Hesk's stored turns), that exact
 * reading is replayed; otherwise the reading a correct interpretation gives. In the hub, zones are
 * aliased in ID order (zone_1 Chapel Road, zone_2 Lantern Hearth) and the road is exit_1.
 */
@SpringBootTest(properties = { "game.api.invite-codes=" + GameDriver.INVITE, "game.api.limits.turns-per-minute-per-run=10000",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class OpeningPlaytestIntegrationTest {

	private static final String CHAPEL_ROAD = "zone_1";
	private static final String HEARTH = "zone_2";
	private static final String ROAD_EXIT = "exit_1";

	@Autowired
	private RunCreationService creation;
	@Autowired
	private TurnService turns;
	@Autowired
	private GameViewService views;
	@Autowired
	private ChronicleService chronicles;
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

	// --- Action documents, as the interpreter would answer ---

	private static String target(String kind, String alias) {
		return alias == null
				? "{\"kind\":\"NONE\",\"alias\":null,\"bodyPart\":null,\"specificity\":\"UNSPECIFIED\"}"
				: "{\"kind\":\"" + kind + "\",\"alias\":\"" + alias + "\",\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}";
	}

	private static String step(String relation, String action, String slot, String payload) {
		StringBuilder json = new StringBuilder("{\"relation\":\"" + relation + "\",\"action\":\"" + action + "\"");
		for (String s : List.of("attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate")) {
			json.append(",\"").append(s).append("\":").append(s.equals(slot) ? payload : "null");
		}
		return json.append('}').toString();
	}

	private static String move(String relation, String type, String target) {
		return step(relation, "MOVE", "move", "{\"movementType\":\"" + type + "\",\"target\":" + target
				+ ",\"goal\":\"NONE\",\"approach\":\"NORMAL\"}");
	}

	private static String observe(String relation, String kind, String target) {
		return step(relation, "OBSERVE", "observe", "{\"kind\":\"" + kind + "\",\"target\":" + target + "}");
	}

	private static String document(String... steps) {
		return "{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":["
				+ String.join(",", steps) + "],\"unresolved\":[]}";
	}

	private CountingAi interpreterAnswers(String... documents) {
		CountingAi model = new CountingAi();
		for (String doc : documents) model.answerStructured(doc);
		ai.use(model);
		return model;
	}

	private String facts(UUID runId, int turnNumber) {
		return jdbc.queryForObject("SELECT mechanics_summary -> 'narration' -> 'facts' FROM run_turn WHERE run_id = ? AND turn_number = ?",
				String.class, runId, turnNumber);
	}

	// --- The opening ---

	@Test
	void everyRunShowsItsDirectionAndTheNamedRoad() {
		UUID runId = game.newRun();
		GameView view = game.view(runId);

		assertThat(view.objective()).isEqualTo("Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.");
		assertThat(view.scene().exits()).extracting(GameView.ExitView::leadsTo).containsExactly("the road to the Hollow Chapel");
		ChronicleView chronicle = chronicles.chronicle(runId, Optional.empty(), 20);
		assertThat(chronicle.opening().objective()).isEqualTo(view.objective());
	}

	@Test
	void lookingAroundAtTheHearthNamesTheWayOnwardAndCostsNoEnemyTurn() {
		UUID runId = game.newRun();
		// Hesk turn 1, as the model read it: watch, then search the hearth.
		interpreterAnswers(document(observe("START", "WATCH", target("NONE", null)), observe("THEN", "SEARCH", target("ZONE", HEARTH))));

		Reply reply = game.turn(runId, "I look around. Look for signs of places to go");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(reply.body().path("overall").asString()).isEqualTo("COMPLETE_SUCCESS");
		assertThat(reply.body().path("enemyTurn").isNull()).isTrue();
		String facts = facts(runId, 1);
		// The doors are told as the way into the chapel: their passage names it, so no "road to the Hollow Chapel" is repeated.
		assertThat(facts).contains("Perceived", "the hearth beneath the lantern", "the chapel road",
				"the sagging west doors into the Hollow Chapel");
		assertThat(facts).doesNotContain("StepHadNoEffect");
	}

	@Test
	void theFallbackObservationIsUsefulAndShort() {
		UUID runId = game.newRun();
		// AI disabled (the default here): the slash command needs no interpreter, and narration falls back.
		Reply reply = game.turn(runId, "/search");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		String text = reply.body().path("narration").path("text").asString();
		// "/search" asks where to go: the answer leads with where the player stands and the ways, with no recap of the place.
		assertThat(text).startsWith("You stand in the hearth beneath the lantern.")
				.contains("the chapel road", "Not yet explored: the sagging west doors into the Hollow Chapel, from the chapel road.")
				.doesNotContain("road to the Hollow Chapel")
				.doesNotContain("Lantern Hearth", "Chapel Road");
	}

	@Test
	void enteringTheChapelNeverWalksBackToTheHearth() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		assertThat(game.turn(runId, "I continue forward").status()).isEqualTo(200);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Chapel Road");

		// Hesk turn 5, and the real probe, as the model read it: a move back to Lantern Hearth. The player
		// named the chapel, and from Chapel Road that is only the road: Java takes it.
		model.answerStructured(document(move("START", "ADVANCE", target("ZONE", HEARTH))));
		Reply entered = game.turn(runId, "I enter the chapel");

		assertThat(entered.status()).as(entered.raw()).isEqualTo(200);
		assertThat(model.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(2); // one call per turn: no repair
		GameView inside = game.view(runId);
		assertThat(inside.location().region()).isEqualTo("Hollow Chapel");
		assertThat(entered.body().path("changes").path("enteredScene").asString()).isEqualTo(inside.location().scene());
		assertThat(inside.scene().exits()).extracting(GameView.ExitView::leadsTo).contains("the way to The Last Lantern");
		assertThat(inside.scene().exits()).extracting(GameView.ExitView::leadsTo).allMatch(
				label -> label.equals("the way to The Last Lantern") || label.equals("an unexplored way"));
	}

	@Test
	void theRoadCanBeTakenFromTheHearthInOneTurn() {
		UUID runId = game.newRun();
		interpreterAnswers(document(move("START", "REPOSITION", target("ZONE", CHAPEL_ROAD)),
				move("THEN", "ADVANCE", target("EXIT", ROAD_EXIT))));

		Reply reply = game.turn(runId, "I follow the road all the way into the Hollow Chapel");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().region()).isEqualTo("Hollow Chapel");
		assertThat(facts(runId, 1)).contains("WalkedTo", "CrossedInto");
	}

	@Test
	void continuingOnwardIsBoundedAndTruthful() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		// Hesk turn 4, as the model read it: "advance" to the zone already reached, then search.
		model.answerStructured(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD)), observe("THEN", "SEARCH", target("NONE", null))));

		Reply reply = game.turn(runId, "I continue onward until I find something");

		// The way on begins here and the player did not ask to go through it: they stay, and the look goes ahead,
		// answered as a look for somewhere to go ("onward").
		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().scene()).isEqualTo("The Last Lantern");
		assertThat(facts(runId, 2)).contains("StayedPut", "SoughtWays", "the sagging west doors into the Hollow Chapel")
				.doesNotContain("CrossedInto");
	}

	// --- A move to where the player already is ---

	@Test
	void continuingToWhereYouAlreadyAreCostsNoTurnAndNoNarration() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		long version = game.view(runId).stateVersion();
		int narrated = model.calls(AiRole.OUTCOME_NARRATOR);
		// The edge case: "continue forward" read as a move to the zone the player is already in.
		model.answerStructured(document(move("START", "REPOSITION", target("ZONE", CHAPEL_ROAD))));

		Reply refused = game.turn(runId, "I continue forward");

		assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
		assertThat(refused.code()).isEqualTo("ACTION_NOT_SUPPORTED");
		assertThat(refused.body().path("error").path("reason").asString()).isEqualTo("ALREADY_THERE");
		assertThat(refused.body().path("error").path("hint").asString())
				.isEqualTo("You're at the chapel road. From here you can reach the hearth beneath the lantern. "
						+ "Ways on: the sagging west doors into the Hollow Chapel, right here.");
		assertThat(model.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(narrated);
		assertThat(game.view(runId).stateVersion()).isEqualTo(version);
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number IS NOT NULL", runId)).isEqualTo(1);
	}

	@Test
	void theSameMoveAsACommandIsRefusedButHoldingPositionStillCounts() {
		UUID runId = game.newRun();
		assertThat(game.turn(runId, "/move " + CHAPEL_ROAD).status()).isEqualTo(200);

		Reply again = game.turn(runId, "/move " + CHAPEL_ROAD);
		assertThat(again.status()).as(again.raw()).isEqualTo(422);
		assertThat(again.body().path("error").path("reason").asString()).isEqualTo("ALREADY_THERE");

		Reply hold = game.turn(runId, "/hold");
		assertThat(hold.status()).as(hold.raw()).isEqualTo(200);
		assertThat(game.view(runId).stateVersion()).isEqualTo(2);
	}

	@Test
	void anUnneededStepToTheRoadStillLetsThePlayerTakeIt() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		// Already on the road, the model still applies the two-step rule: the first step is idle, yet "succeeds".
		model.answerStructured(document(move("START", "REPOSITION", target("ZONE", CHAPEL_ROAD)),
				move("IF_PREVIOUS_SUCCEEDS", "ADVANCE", target("EXIT", ROAD_EXIT))));

		Reply reply = game.turn(runId, "I go on into the Hollow Chapel");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().region()).isEqualTo("Hollow Chapel");
	}

	@Test
	void aJourneyToTheRoadStopsThereAndIsToldExactly() {
		UUID runId = game.newRun();
		// "The chapel road" names the zone in full, so the journey ends there, short of the chapel.
		String reading = document(move("START", "ADVANCE", target("EXIT", ROAD_EXIT)));
		// The interpreter answers; the narrator is unavailable, so Java's deterministic narration is used.
		ai.use(new com.leeburke.springgame.ai.AiProvider() {
			@Override
			public com.leeburke.springgame.ai.AiResponse generateStructured(com.leeburke.springgame.ai.AiStructuredRequest request) {
				return new com.leeburke.springgame.ai.AiResponse.Success(reading, java.time.Duration.ofMillis(1), Optional.empty());
			}

			@Override
			public com.leeburke.springgame.ai.AiResponse generateText(com.leeburke.springgame.ai.AiTextRequest request) {
				return com.leeburke.springgame.ai.AiResponse.Failure.of(com.leeburke.springgame.ai.AiFailureKind.TIMEOUT);
			}
		});

		Reply reply = game.turn(runId, "I walk down to the chapel road");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(reply.body().path("narration").path("text").asString())
				.startsWith("You make your way through the waystation's open gate to the chapel road.")
				.doesNotContain("Chapel Road", "Lantern Hearth");
		GameView view = game.view(runId);
		assertThat(view.location().scene()).isEqualTo("The Last Lantern");
		assertThat(view.location().zone().name()).isEqualTo("Chapel Road");
		String facts = facts(runId, 1);
		assertThat(facts).contains("WalkedTo", "the chapel road").doesNotContain("CrossedInto");
		assertThat(reply.body().path("changes").path("enteredScene").isNull()).isTrue();
	}

	// --- The real probe's readings, grounded ---

	@Test
	void followingTheRoadFromTheHearthGoesAsFarAsTheRoadOnly() {
		UUID runId = game.newRun();
		// The real probe's reading: the road exit directly, from the hearth. Nothing in the words goes through it.
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("EXIT", ROAD_EXIT))));

		Reply reply = game.turn(runId, "follow the road");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().scene()).isEqualTo("The Last Lantern");
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Chapel Road");
		assertThat(discovered(runId)).isEqualTo(1);
		assertThat(model.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(1);
	}

	@Test
	void continuingOnwardWithAnUnclearPhraseAtTheWayOnAsksInsteadOfCrossing() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		// Onward, with the bound it cannot place: a step and an unresolved phrase (not repaired).
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"MEDIUM\",\"steps\":["
				+ move("START", "ADVANCE", target("NONE", null)) + "],\"unresolved\":[{\"stepNumber\":1,\"phrase\":\"until I find something\"}]}");

		long version = game.view(runId).stateVersion();
		int narrated = model.calls(AiRole.OUTCOME_NARRATOR);
		Reply asked = game.turn(runId, "I continue onward until I find something");

		assertThat(asked.status()).as(asked.raw()).isEqualTo(422);
		assertThat(asked.body().path("error").path("reason").asString()).isEqualTo("AT_THRESHOLD");
		assertThat(asked.body().path("error").path("message").asString())
				.isEqualTo("Before you: the sagging west doors into the Hollow Chapel. Do you want to go through?");
		assertThat(game.view(runId).stateVersion()).isEqualTo(version);
		assertThat(model.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(narrated);
		assertThat(model.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(2); // one per turn, no repair
	}

	@Test
	void anAnswerWithNothingButAnUnclearPhraseAsksWithoutATurnOrNarration() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		long version = game.view(runId).stateVersion();
		int narrated = model.calls(AiRole.OUTCOME_NARRATOR);
		// The real probe's INVALID_OUTPUT case: no steps, only the phrase it could not place. Nothing in the
		// words names a place, so Java cannot ground it either: the player is asked, at no cost.
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"LOW\",\"steps\":[],"
				+ "\"unresolved\":[{\"stepNumber\":null,\"phrase\":\"until I find something\"}]}");

		Reply asked = game.turn(runId, "I continue onward until I find something");

		assertThat(asked.status()).as(asked.raw()).isEqualTo(422);
		assertThat(asked.code()).isEqualTo("INTERPRETATION_FAILED");
		assertThat(asked.body().path("error").path("reason").asString()).isEqualTo("UNCLEAR");
		assertThat(asked.body().path("error").path("hint").asString()).startsWith("You're at the chapel road.");
		assertThat(model.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(2); // not repaired
		assertThat(model.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(narrated);
		assertThat(game.view(runId).stateVersion()).isEqualTo(version);
	}

	@Test
	void anUnnamedAdvanceIsNeverTakenBackward() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		// "Forward", read as the way back to the hearth: neither back, nor through the way on without asking.
		model.answerStructured(document(move("START", "ADVANCE", target("ZONE", HEARTH))));

		Reply asked = game.turn(runId, "I continue forward");

		assertThat(asked.status()).as(asked.raw()).isEqualTo(422);
		assertThat(asked.body().path("error").path("reason").asString()).isEqualTo("AT_THRESHOLD");
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Chapel Road");
	}

	@Test
	void goingBackOnPurposeIsHonoured() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I continue forward");
		model.answerStructured(document(move("START", "REPOSITION", target("ZONE", HEARTH))));

		Reply reply = game.turn(runId, "I go back to the hearth");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Lantern Hearth");
	}

	// --- Player agency: the playtest that crossed without being asked ---

	private static final String PLAYTEST = "I walk forward, keeping an eye on my surroundings to see if I spot anything";

	private int discovered(UUID runId) {
		return game.count("SELECT count(*) FROM scene_instance WHERE run_id = ? AND discovered", runId);
	}

	private tools.jackson.databind.JsonNode factsTree(UUID runId, int turn) {
		return tools.jackson.databind.json.JsonMapper.builder().build().readTree(facts(runId, turn));
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "NONE", "EXIT" })
	void walkingForwardWhileLookingStopsAtTheRoadAndLooksThere(String reading) {
		UUID runId = game.newRun();
		// Either reading the model may give: onward with no target, or the road exit (even marked EXPLICIT).
		String target = reading.equals("NONE") ? target("NONE", null) : target("EXIT", ROAD_EXIT);
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target), observe("THEN", "WATCH", target("NONE", null))));

		Reply reply = game.turn(runId, PLAYTEST);

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		GameView view = game.view(runId);
		assertThat(view.location().scene()).isEqualTo("The Last Lantern");
		assertThat(view.location().zone().name()).isEqualTo("Chapel Road");
		assertThat(discovered(runId)).as("no new place discovered").isEqualTo(1);
		String facts = facts(runId, 1);
		assertThat(facts).contains("WalkedTo", "Perceived", "the sagging west doors into the Hollow Chapel")
				.doesNotContain("CrossedInto", "StepCancelled");
		tools.jackson.databind.JsonNode observed = factsTree(runId, 1).get(1);
		assertThat(observed.path("perception").path("here").path("label").asString()).isEqualTo("Chapel Road");
		assertThat(jdbc.queryForObject("SELECT mechanics_summary -> 'narration' ->> 'currentZone' FROM run_turn WHERE run_id = ? "
				+ "AND turn_number = 1", String.class, runId)).as("narrated where the player ends up").isEqualTo("the chapel road");
		assertThat(model.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(1);
	}

	@Test
	void approachingTheChapelLookingForItsEntranceDoesNotEnter() {
		UUID runId = game.newRun();
		interpreterAnswers(document(move("START", "ADVANCE", target("EXIT", ROAD_EXIT)), observe("THEN", "SEARCH", target("NONE", null))));

		Reply reply = game.turn(runId, "I head toward the chapel, looking for its entrance");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Chapel Road");
		assertThat(discovered(runId)).isEqualTo(1);
		assertThat(facts(runId, 1)).contains("Perceived").doesNotContain("CrossedInto");
	}

	@Test
	void goingInsideTheOneWayInCrosses() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I walk to the chapel road");
		model.answerStructured(document(move("START", "ADVANCE", target("NONE", null))));

		Reply reply = game.turn(runId, "I go inside");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().region()).isEqualTo("Hollow Chapel");
		assertThat(discovered(runId)).isEqualTo(2);
	}

	@Test
	void enteringAndLookingAroundDescribesTheArrivalTruthfully() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I walk to the chapel road");
		model.answerStructured(document(move("START", "ADVANCE", target("EXIT", ROAD_EXIT)), observe("THEN", "SEARCH", target("NONE", null))));

		Reply reply = game.turn(runId, "I enter the Hollow Chapel and look around");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		GameView inside = game.view(runId);
		assertThat(inside.location().region()).isEqualTo("Hollow Chapel");
		tools.jackson.databind.JsonNode facts = factsTree(runId, 2);
		assertThat(facts).extracting(f -> f.path("fact").asString())
				.containsExactly("CrossedInto", "Perceived"); // the look is kept, not cancelled, and does not retell the arrival
		tools.jackson.databind.JsonNode crossed = facts.get(0);
		assertThat(crossed.path("arrival").path("label").asString()).isEqualTo(inside.location().zone().name());
		assertThat(crossed.path("behindLeadsTo").asString()).isEqualTo("the way to The Last Lantern");
		assertThat(crossed.path("sceneDescription").asString()).isNotBlank();
		tools.jackson.databind.JsonNode seen = facts.get(1).path("perception");
		assertThat(seen.path("here").isNull()).as("the arrival already told the place").isTrue();
		// Only what the player can now see: every creature, thing and hazard named is in the player's own view.
		java.util.Set<String> visible = new java.util.HashSet<>();
		inside.scene().creatures().forEach(c -> visible.add(c.name()));
		inside.scene().objects().forEach(o -> visible.add(o.name()));
		inside.scene().hazards().forEach(h -> visible.add(h.name()));
		for (String group : List.of("creatures", "things", "hazards")) {
			seen.path(group).forEach(thing -> assertThat(visible).contains(thing.path("name").asString()));
		}
		assertThat(seen.path("ways").toString()).contains("the way to The Last Lantern");
		assertThat(jdbc.queryForObject("SELECT mechanics_summary -> 'narration' ->> 'currentZone' FROM run_turn WHERE run_id = ? "
				+ "AND turn_number = 2", String.class, runId)).isEqualTo(crossed.path("arrival").path("phrase").asString());
	}

	@Test
	void headingBackToTheLastLanternRetreats() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "REPOSITION", target("ZONE", CHAPEL_ROAD)),
				move("THEN", "ADVANCE", target("EXIT", ROAD_EXIT))));
		game.turn(runId, "I follow the road all the way into the chapel");
		GameView inside = game.view(runId);
		String back = inside.scene().exits().stream().filter(x -> x.leadsTo().equals("the way to The Last Lantern")).findFirst()
				.orElseThrow().alias();
		// Looking at the way back is not taking it.
		model.answerStructured(document(observe("START", "INSPECT", target("EXIT", back))));
		Reply looked = game.turn(runId, "I examine the way back");
		assertThat(looked.status()).as(looked.raw()).isIn(200, 422);
		assertThat(game.view(runId).location().region()).isEqualTo("Hollow Chapel");

		model.answerStructured(document(move("START", "ADVANCE", target("EXIT", back))));
		Reply reply = game.turn(runId, "I head back to the Last Lantern");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(game.view(runId).location().scene()).isEqualTo("The Last Lantern");
	}

	@Test
	void aPendingAttackStillComesFirst() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		GameView pending = game.untilPending(runId);
		long version = pending.stateVersion();
		String attack = pending.pendingAttack().alias();

		// Walking on without defending is refused, before any movement is grounded or resolved.
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("NONE", null))));
		Reply refused = game.turn(runId, "I walk forward");
		assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
		assertThat(refused.code()).isEqualTo("DEFENSE_REQUIRED");
		assertThat(game.view(runId).stateVersion()).isEqualTo(version);

		// Defending first, then walking forward and looking: the defense stays first and nothing crosses.
		String scene = game.view(runId).location().scene();
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":\"" + attack
				+ "\",\"confidence\":\"HIGH\",\"steps\":[" + step("START", "DEFEND", "defend",
						"{\"method\":\"PARRY\",\"evadeType\":\"UNSPECIFIED\",\"parryContact\":\"UNSPECIFIED\",\"cover\":"
								+ target("NONE", null) + "}")
				+ "," + move("THEN", "ADVANCE", target("NONE", null)) + "," + observe("THEN", "WATCH", target("NONE", null)) + "],\"unresolved\":[]}");
		game.setPlayerHp(runId, game.maxHp(runId));
		Reply reply = game.turn(runId, "I parry, then walk forward keeping watch");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(factsTree(runId, game.view(runId).lastTurn().turnNumber()).get(0).path("fact").asString()).isEqualTo("PlayerDefended");
		assertThat(game.view(runId).location().scene()).isEqualTo(scene);
	}

	// --- The second playtest: the chapel's entrance, and the decision to go in ---

	@Test
	void followingTheRoadWhileWatchingShowsTheChapelsDoors() {
		UUID runId = game.newRun();
		interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD)), observe("THEN", "WATCH", target("NONE", null))));

		Reply reply = game.turn(runId, "I follow the road, watching my surroundings");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(facts(runId, 1)).contains("WalkedTo", "Perceived", "the sagging west doors into the Hollow Chapel", "squat, blackened church");
		assertThat(game.view(runId).location().region()).as("watching is not entering").isNull();
	}

	@Test
	void continuingOnwardToTheChapelAtItsDoorsAsksBeforeGoingIn() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(move("START", "ADVANCE", target("ZONE", CHAPEL_ROAD))));
		game.turn(runId, "I follow the road");
		long version = game.view(runId).stateVersion();
		// The playtest's words, read as heading for the chapel: approached, not entered.
		model.answerStructured(document(move("START", "ADVANCE", target("EXIT", ROAD_EXIT))));

		Reply asked = game.turn(runId, "I continue onward to hollow chapel");

		assertThat(asked.status()).as(asked.raw()).isEqualTo(422);
		assertThat(asked.body().path("error").path("reason").asString()).isEqualTo("AT_THRESHOLD");
		assertThat(asked.body().path("error").path("message").asString())
				.isEqualTo("Before you: the sagging west doors into the Hollow Chapel. Do you want to go through?");
		assertThat(asked.body().path("error").path("hint").asString()).doesNotContain("Chapel Road", "Lantern Hearth");
		assertThat(game.view(runId).stateVersion()).isEqualTo(version);
		assertThat(game.view(runId).location().region()).isNull();
	}

	// --- Refusing only what truly could not happen ---

	@Test
	void anActionWithNothingPossibleCostsNoTurnAndNoNarration() {
		UUID runId = game.newRun();
		CountingAi model = interpreterAnswers(document(step("START", "USE_ABILITY", "useAbility",
				"{\"ability\":\"ability_1\",\"target\":{\"kind\":\"SELF\",\"alias\":null,\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}}")));

		Reply refused = game.turn(runId, "I call on my gift");

		assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
		assertThat(refused.code()).isEqualTo("ACTION_NOT_SUPPORTED");
		assertThat(refused.body().path("error").path("reason").asString()).isEqualTo("NOT_POSSIBLE_YET");
		assertThat(refused.body().path("error").path("hint").asString())
				.isEqualTo("You're at the hearth beneath the lantern. From here you can reach the chapel road. Ways on: "
						+ "the sagging west doors into the Hollow Chapel, reached from the chapel road.");
		assertThat(model.calls(AiRole.OUTCOME_NARRATOR)).isZero();
		assertThat(game.view(runId).stateVersion()).isZero();
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number IS NOT NULL", runId)).isZero();
	}

	@Test
	void aBlockedMoveIsAFailureThatStillCommits() {
		UUID runId = game.newRun();
		// The exact command to take the road from the hearth: the exit is not in this zone, so the move
		// resolves as FAILURE. Commands are exact and never re-routed.
		CountingAi model = new CountingAi();
		ai.use(model);

		Reply blocked = game.turn(runId, "/move " + ROAD_EXIT);

		assertThat(blocked.status()).as(blocked.raw()).isEqualTo(200);
		assertThat(blocked.body().path("overall").asString()).isEqualTo("FAILURE");
		assertThat(game.view(runId).stateVersion()).isEqualTo(1);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Lantern Hearth");
		assertThat(model.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(1);
	}

	@Test
	void aMissedAttackIsAFailureThatStillCommits() {
		UUID runId = game.newRun();
		SceneInstance scene = game.placeAmongEnemies(runId, 1);
		EnemyInstance enemy = game.visibleEnemies(scene).getFirst();
		String target = game.alias(scene, enemy.entityId());

		// Hit or miss, it happened; repeat until a miss is seen, so the failure path itself is covered.
		boolean missed = false;
		for (int i = 0; i < 30 && !missed; i++) {
			game.setPlayerHp(runId, game.maxHp(runId));
			game.setEnemyHp(scene, enemy.entityId(), enemy.maxHp());
			long version = game.view(runId).stateVersion();
			Reply attack = game.strike(runId, target);
			assertThat(attack.status()).as(attack.raw()).isEqualTo(200);
			assertThat(game.view(runId).stateVersion()).isGreaterThan(version);
			missed = !attack.body().path("overall").asString().equals("COMPLETE_SUCCESS");
			if (!game.view(runId).status().equals("ACTIVE")) break;
		}
		assertThat(missed).as("a miss within 30 swings").isTrue();
	}

	@Test
	void slashCommandsAreNotSecondGuessed() {
		UUID runId = game.newRun();
		// "/move zone_2" names nothing in words; commands are exact and never pass through the destination check.
		Reply reply = game.turn(runId, "/move " + CHAPEL_ROAD);
		assertThat(reply.status()).isEqualTo(200);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Chapel Road");
	}
}
