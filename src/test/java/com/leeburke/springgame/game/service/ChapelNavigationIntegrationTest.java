package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.HashMap;
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
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;
import com.leeburke.springgame.game.service.GameDriver.Reply;
import com.leeburke.springgame.game.service.GameTestConfiguration.SwitchableAi;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKnowledge;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finding the way through the Hollow Chapel, against PostgreSQL with scripted interpreter answers
 * (never a live model): the west doors lead in at the first scene's entrance, "where haven't I been?"
 * is answered from what the player knows (and only that), the answer is honest when nothing is left,
 * and taking an item is told from the item, with the container left as it really is.
 */
@SpringBootTest(properties = { "game.api.invite-codes=" + GameDriver.INVITE, "game.api.limits.turns-per-minute-per-run=10000",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class ChapelNavigationIntegrationTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final WorldContentCatalog WORLD = WorldContentLoader.loadBundled();
	private static final String UNEXPLORED = "an unexplored way";

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
	@Autowired
	private ChronicleService chronicles;

	private GameDriver game;

	@BeforeEach
	void setUp() {
		game = new GameDriver(creation, turns, views, world, enemies, runs, jdbc);
	}

	@AfterEach
	void resetAi() {
		ai.reset();
	}

	// --- helpers ---

	private JsonNode facts(UUID runId) {
		return JSON.readTree(jdbc.queryForObject("SELECT mechanics_summary -> 'narration' -> 'facts' FROM run_turn WHERE run_id = ? "
				+ "AND turn_number = ?", String.class, runId, game.view(runId).lastTurn().turnNumber()));
	}

	private SceneInstance currentScene(UUID runId) {
		return world.findScene(world.findPlayerLocation(runId).orElseThrow().sceneId()).orElseThrow();
	}

	private Reply command(UUID runId, String command) {
		Reply reply = game.turn(runId, command);
		assertThat(reply.status()).as(command + ": " + reply.raw()).isEqualTo(200);
		return reply;
	}

	/** Through the hub to the road's end and in at the west doors. */
	private GameView enterTheChapel(UUID runId) {
		command(runId, "/move zone_1");
		command(runId, "/move exit_1");
		GameView inside = game.view(runId);
		assertThat(inside.location().region()).isEqualTo("Hollow Chapel");
		return inside;
	}

	/** A new run, inside the chapel, whose first scene holds no creature (so no enemy turn interrupts). */
	private UUID quietRunInside() {
		for (int i = 0; i < 40; i++) {
			UUID runId = game.newRun();
			enterTheChapel(runId);
			if (currentScene(runId).state().entities().isEmpty()) {
				return runId;
			}
		}
		throw new AssertionError("No run in 40 had a quiet first chapel scene");
	}

	/** Walks to a known zone (by alias), one passage per command, over the connections the view shows. */
	private void walkTo(UUID runId, String zoneAlias) {
		for (int i = 0; i < 8; i++) {
			GameView view = game.view(runId);
			String here = view.location().zone().alias();
			if (here.equals(zoneAlias)) {
				return;
			}
			Map<String, String> previous = new HashMap<>();
			ArrayDeque<String> queue = new ArrayDeque<>(List.of(here));
			previous.put(here, here);
			while (!queue.isEmpty()) {
				String at = queue.poll();
				for (GameView.ConnectionView c : view.scene().connections()) {
					String next = c.zoneA().equals(at) ? c.zoneB() : c.zoneB().equals(at) ? c.zoneA() : null;
					if (next != null && !previous.containsKey(next)) {
						previous.put(next, at);
						queue.add(next);
					}
				}
			}
			String step = zoneAlias;
			while (!previous.get(step).equals(here)) {
				step = previous.get(step);
			}
			command(runId, "/move " + step);
		}
		throw new AssertionError("Could not walk to " + zoneAlias);
	}

	private void scriptedSearch() {
		CountingAi model = new CountingAi();
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":["
				+ "{\"relation\":\"START\",\"action\":\"OBSERVE\",\"attack\":null,\"defend\":null,\"move\":null,\"interact\":null,"
				+ "\"observe\":{\"kind\":\"SEARCH\",\"target\":{\"kind\":\"NONE\",\"alias\":null,\"bodyPart\":null,\"specificity\":\"UNSPECIFIED\"}},"
				+ "\"useAbility\":null,\"useItem\":null,\"communicate\":null}],\"unresolved\":[]}");
		ai.use(model);
	}

	/** The phrases of the scene's zones the player has not seen (they must appear nowhere). */
	private Set<String> unseenPhrases(UUID runId) {
		SceneInstance scene = currentScene(runId);
		String zone = world.findPlayerLocation(runId).orElseThrow().zoneId();
		Set<String> known = SceneKnowledge.knownZones(scene.state(), zone);
		return scene.state().zones().stream().filter(z -> !known.contains(z.id()))
				.map(z -> WORLD.texts().scene(scene.definitionCode()).orElseThrow().zones().get(z.id()).phrase()).collect(Collectors.toSet());
	}

	// --- the doors and the way back ---

	@Test
	void theWestDoorsLeadInAtTheEntranceAndTheWayBackIsThoseDoors() {
		for (int i = 0; i < 4; i++) {
			UUID runId = game.newRun();
			assertThat(game.view(runId).objective()).startsWith("Follow Chapel Road");
			GameView inside = enterTheChapel(runId);
			SceneInstance first = currentScene(runId);

			assertThat(List.of("RUINED_NAVE", "BELL_PASSAGE")).contains(first.definitionCode());
			assertThat(world.findPlayerLocation(runId).orElseThrow().zoneId())
					.isEqualTo(WORLD.findArchetype(first.definitionCode()).orElseThrow().entranceZone());
			if (first.definitionCode().equals("RUINED_NAVE")) {
				assertThat(world.findPlayerLocation(runId).orElseThrow().zoneId()).isEqualTo("nave_entrance");
			}
			JsonNode crossed = facts(runId).get(0);
			assertThat(crossed.path("fact").asString()).isEqualTo("CrossedInto");
			assertThat(crossed.path("behind").asString()).as("the way back is the doors you came in by").contains("west doors");
			assertThat(inside.objective()).as("inside, the direction is deeper").startsWith("Find a way deeper into the Hollow Chapel");
		}
	}

	// --- where haven't I been? ---

	@Test
	void lookingForAWayNotYetTakenAnswersFromWhatIsKnownAndMovesNoOne() {
		UUID runId = quietRunInside();
		GameView atTheDoors = game.view(runId);
		Set<String> unseen = unseenPhrases(runId);

		scriptedSearch();
		Reply asked = command(runId, "I look for a way I haven't gone");

		JsonNode sought = facts(runId).get(0);
		assertThat(sought.path("fact").asString()).isEqualTo("SoughtWays");
		assertThat(game.view(runId).location().zone().alias()).as("asking moves no one").isEqualTo(atTheDoors.location().zone().alias());
		// The leads lead: no recap of the things around or of the place's description.
		assertThat(sought.path("perception").path("things")).isEmpty();
		assertThat(sought.path("perception").path("beside")).isEmpty();
		assertThat(sought.path("perception").path("ways")).as("the leads carry the ways, each with its place").isEmpty();
		assertThat(sought.path("perception").path("here").path("description").asString()).isEmpty();
		// From the doors, the only places known are here and beside: the way on lies farther in, unseen yet.
		assertThat(sought.path("unvisited")).isNotEmpty();
		assertThat(sought.path("nothingKnownLeft").asBoolean()).isFalse();
		String raw = sought.toString();
		unseen.forEach(phrase -> assertThat(raw).as("nothing unseen is told").doesNotContain(phrase));
		assertThat(asked.raw()).doesNotContain("first_find");

		// One place in, the far end comes into sight, and with it the unexplored way on.
		String beside = atTheDoors.scene().leads().unvisitedZones().getFirst();
		walkTo(runId, beside);
		scriptedSearch();
		command(runId, "Is there a way deeper into the chapel?");
		JsonNode deeper = facts(runId).get(0);
		assertThat(deeper.path("unexplored")).isNotEmpty();
		deeper.path("unexplored").forEach(lead -> {
			assertThat(lead.path("leadsTo").asString()).isEqualTo(UNEXPLORED);
			assertThat(lead.path("what").asString()).isNotBlank().isNotEqualTo("a way out");
		});
		assertThat(deeper.path("visited").toString()).as("where the player has been is told").isNotEqualTo("[]");
		// The Scene sheet shows the same leads, by alias.
		assertThat(game.view(runId).scene().leads().unexploredExits()).isNotEmpty();
	}

	@Test
	void whenEveryKnownWayHasBeenTriedItSaysSoWithoutInventingOne() {
		UUID runId = quietRunInside();
		String sceneName = game.view(runId).location().scene();
		// Visit every known place, and look through every way on and come straight back.
		for (int round = 0; round < 12; round++) {
			GameView view = game.view(runId);
			Optional<String> unvisited = view.scene().leads().unvisitedZones().stream().findFirst();
			if (unvisited.isPresent()) {
				walkTo(runId, unvisited.get());
				continue;
			}
			Optional<GameView.ExitView> wayOn = view.scene().exits().stream().filter(x -> x.leadsTo().equals(UNEXPLORED)).findFirst();
			if (wayOn.isEmpty()) {
				break;
			}
			walkTo(runId, wayOn.get().zone());
			command(runId, "/move " + wayOn.get().alias());
			GameView there = game.view(runId);
			GameView.ExitView back = there.scene().exits().stream()
					.filter(x -> x.leadsTo().contains(sceneName) && x.zone().equals(there.location().zone().alias())).findFirst().orElseThrow();
			command(runId, "/move " + back.alias());
		}
		assertThat(game.view(runId).scene().leads().unexploredExits()).isEmpty();
		assertThat(game.view(runId).scene().leads().unvisitedZones()).isEmpty();

		scriptedSearch();
		command(runId, "Where can I go from here?");

		JsonNode sought = facts(runId).get(0);
		assertThat(sought.path("fact").asString()).isEqualTo("SoughtWays");
		assertThat(sought.path("nothingKnownLeft").asBoolean()).isTrue();
		assertThat(sought.path("unexplored")).isEmpty();
		assertThat(sought.path("known")).isNotEmpty();
		assertThat(sought.path("known").toString()).as("the ways known are the ones tried").doesNotContain(UNEXPLORED);
	}

	// --- the live Cracked Belfry sequence ---

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

	private void interpreterAnswers(String... steps) {
		CountingAi model = new CountingAi();
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":["
				+ String.join(",", steps) + "],\"unresolved\":[]}");
		ai.use(model);
	}

	private String zoneAlias(UUID runId, String name) {
		return game.view(runId).scene().zones().stream().filter(z -> z.name().equals(name)).findFirst().orElseThrow().alias();
	}

	/** A new run whose first scene is a quiet Bell Passage, entered at its landing. */
	private UUID quietBellPassage() {
		for (int i = 0; i < 80; i++) {
			UUID runId = game.newRun();
			enterTheChapel(runId);
			SceneInstance first = currentScene(runId);
			if (first.definitionCode().equals("BELL_PASSAGE") && first.state().entities().isEmpty()) {
				return runId;
			}
		}
		throw new AssertionError("No run in 80 opened on a quiet Bell Passage");
	}

	@Test
	void seeingWhetherTheHoleIsSafeToDropDownLooksAtTheBelfryNeverAtTheCrateBehind() {
		UUID runId = quietBellPassage();
		// The rope gallery: the crate opened and the torch (or whatever it holds) taken, as in the playtest.
		GameView.ThingView crate = game.view(runId).scene().objects().stream().filter(o -> "closed".equals(o.container())).findFirst()
				.orElseThrow();
		command(runId, "/move " + crate.alias());
		String crateHere = game.view(runId).scene().objects().stream().filter(o -> "here".equals(o.reach()) && o.container() != null)
				.findFirst().orElseThrow().alias();
		command(runId, "/open " + crateHere);
		command(runId, "/take " + crateHere);
		assertThat(game.view(runId).location().zone().name()).isEqualTo("Rope Gallery");

		// "I walk toward the cracked belfry and look down the hole", as the interpreter read it live.
		String belfry = zoneAlias(runId, "Cracked Belfry");
		interpreterAnswers(step("START", "MOVE", "move", "{\"movementType\":\"ADVANCE\",\"target\":" + target("ZONE", belfry)
				+ ",\"goal\":\"NONE\",\"approach\":\"NORMAL\"}"),
				step("THEN", "OBSERVE", "observe", "{\"kind\":\"WATCH\",\"target\":" + target("ZONE", belfry) + "}"));
		command(runId, "I walk toward the cracked belfry and look down the hole");
		JsonNode walked = facts(runId);
		assertThat(walked.get(0).path("fact").asString()).isEqualTo("WalkedTo");
		assertThat(walked.get(1).path("fact").asString()).isEqualTo("InspectedPlace");
		assertThat(walked.get(1).path("place").path("description").asString()).contains("crack wide enough to show the drop below");

		// "I see if the hole is safe to drop down": the interpreter aimed at the emptied crate in the gallery behind.
		GameView atTheBelfry = game.view(runId);
		String theCrate = atTheBelfry.scene().objects().stream().filter(o -> o.container() != null).findFirst().orElseThrow().alias();
		long version = atTheBelfry.stateVersion();
		interpreterAnswers(step("START", "OBSERVE", "observe", "{\"kind\":\"INSPECT\",\"target\":" + target("OBJECT", theCrate) + "}"));

		Reply assessed = command(runId, "I see if the hole is safe to drop down");

		JsonNode look = facts(runId);
		assertThat(look).hasSize(1);
		assertThat(look.get(0).path("fact").asString()).isEqualTo("InspectedPlace");
		assertThat(look.get(0).path("where").asString()).isEqualTo("here");
		assertThat(look.get(0).path("place").path("phrase").asString()).isEqualTo("the cracked belfry");
		assertThat(look.get(0).path("place").path("description").asString()).contains("drop below");
		assertThat(look.get(0).path("ways")).as("the known ways from the belfry are told").isNotEmpty();
		look.get(0).path("ways").forEach(way -> assertThat(way.path("where").asString()).isEqualTo("here"));
		assertThat(look.toString()).doesNotContain("Crate").doesNotContain("candles");
		assertThat(assessed.body().path("narration").path("text").asString()).doesNotContain("crate");
		// A look only: nobody drops, climbs or moves.
		GameView after = game.view(runId);
		assertThat(after.location().zone().name()).isEqualTo("Cracked Belfry");
		assertThat(after.location().scene()).isEqualTo("Bell Passage");
		assertThat(after.stateVersion()).isEqualTo(version + 1);
	}

	@Test
	void aLookAtSomethingNothingKnownMatchesIsUnclearAndCostsNoTurn() {
		UUID runId = quietBellPassage();
		GameView before = game.view(runId);
		String anyObject = before.scene().objects().stream().findFirst().orElseThrow().alias();
		interpreterAnswers(step("START", "OBSERVE", "observe", "{\"kind\":\"INSPECT\",\"target\":" + target("OBJECT", anyObject) + "}"));

		Reply refused = game.turn(runId, "I study the strange glyphs");

		assertThat(refused.status()).as(refused.raw()).isEqualTo(422);
		assertThat(refused.body().path("error").path("reason").asString()).isEqualTo("UNCLEAR");
		assertThat(game.view(runId).stateVersion()).isEqualTo(before.stateVersion());
	}

	// --- telling only what is new ---

	@Test
	void aPlaceReachedForTheFirstTimeIsDescribedAndAFamiliarOneIsNot() {
		UUID runId = quietRunInside();
		GameView atTheDoors = game.view(runId);
		String doors = atTheDoors.location().zone().name();
		String beside = atTheDoors.scene().leads().unvisitedZones().getFirst();

		walkTo(runId, beside);
		JsonNode firstTime = facts(runId).get(0);
		assertThat(firstTime.path("fact").asString()).isEqualTo("WalkedTo");
		assertThat(firstTime.path("to").path("description").asString()).as("newly reached: described").isNotBlank();

		String back = game.view(runId).scene().zones().stream().filter(z -> z.name().equals(doors)).findFirst().orElseThrow().alias();
		walkTo(runId, back);
		JsonNode familiar = facts(runId).get(0);
		assertThat(familiar.path("fact").asString()).isEqualTo("WalkedTo");
		assertThat(familiar.path("to").path("phrase").asString()).isNotBlank();
		assertThat(familiar.path("to").path("description").asString()).as("been here: told briefly").isEmpty();
	}

	@Test
	void aSecondLookWithNothingChangedIsToldDirectlyAndRestoredAsSuch() {
		UUID runId = quietRunInside();
		command(runId, "/watch");
		long versionBefore = game.view(runId).stateVersion();

		Reply again = command(runId, "/watch");

		assertThat(again.body().path("narration").path("source").asString()).isEqualTo("DIRECT");
		assertThat(again.body().path("narration").path("text").asString()).isEqualTo("Nothing has changed around you.");
		assertThat(facts(runId).get(0).path("unchanged").asBoolean()).isTrue();
		// Persisted and restored as told: the view and the chronicle both read it back as DIRECT.
		GameView reloaded = game.view(runId);
		assertThat(reloaded.lastTurn().narration().source()).isEqualTo("DIRECT");
		assertThat(reloaded.stateVersion()).as("a look changes nothing but the turn").isEqualTo(versionBefore + 1);
		var turnsTold = chronicles.chronicle(runId, Optional.empty(), 20).turns();
		assertThat(turnsTold.getLast().narration().source()).isEqualTo("DIRECT");
		assertThat(turnsTold.get(turnsTold.size() - 2).narration().source()).as("the first look was not unchanged").isNotEqualTo("DIRECT");
	}

	// --- taking and looking ---

	@Test
	void takingTheFindAndExaminingItTellsTheItemAndTheCrateAsItNowIs() {
		UUID runId = quietRunInside();
		GameView.ThingView crate = game.view(runId).scene().objects().stream().filter(o -> "closed".equals(o.container())).findFirst()
				.orElseThrow();
		command(runId, "/move " + crate.alias());
		// Aliases are numbered per view: find the crate again, now within reach.
		crate = game.view(runId).scene().objects().stream().filter(o -> "closed".equals(o.container()) && "here".equals(o.reach()))
				.findFirst().orElseThrow();
		command(runId, "/open " + crate.alias());
		String item = currentScene(runId).state().container("first_find").orElseThrow().contents().getFirst();
		// As the interpreter once read "I take the vial and examine it": a take, then a look at the crate.
		CountingAi model = new CountingAi();
		String target = "{\"kind\":\"OBJECT\",\"alias\":\"" + crate.alias() + "\",\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}";
		model.answerStructured("{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":["
				+ "{\"relation\":\"START\",\"action\":\"INTERACT\",\"attack\":null,\"defend\":null,\"move\":null,"
				+ "\"interact\":{\"kind\":\"PICK_UP\",\"target\":" + target + ",\"carried\":null,\"approach\":\"NORMAL\"},"
				+ "\"observe\":null,\"useAbility\":null,\"useItem\":null,\"communicate\":null},"
				+ "{\"relation\":\"THEN\",\"action\":\"OBSERVE\",\"attack\":null,\"defend\":null,\"move\":null,\"interact\":null,"
				+ "\"observe\":{\"kind\":\"INSPECT\",\"target\":" + target + "},\"useAbility\":null,\"useItem\":null,\"communicate\":null}],"
				+ "\"unresolved\":[]}");
		ai.use(model);

		command(runId, "I take the vial and examine it");

		JsonNode facts = facts(runId);
		assertThat(facts.get(0).path("fact").asString()).isEqualTo("TookItem");
		assertThat(facts.get(0).path("description").asString()).as("the item is told as it looks in the hand").isNotBlank();
		assertThat(facts.get(1).path("fact").asString()).isEqualTo("Inspected");
		assertThat(facts.get(1).path("state").asString()).as("the crate as it now is").isEqualTo("open and empty");
		assertThat(game.view(runId).character().items()).extracting(GameView.OwnedView::name)
				.contains(WORLD_ITEMS.getOrDefault(item, item));
	}

	private static final Map<String, String> WORLD_ITEMS = Map.of("RESTORATIVE_SALVE", "Restorative Salve", "BANDAGE", "Bandage",
			"TORCH", "Torch");
}
