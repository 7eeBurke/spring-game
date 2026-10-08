package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
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
import com.leeburke.springgame.game.TerminalRules;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.game.service.GameDriver.Reply;
import com.leeburke.springgame.game.service.GameTestConfiguration.SwitchableAi;
import com.leeburke.springgame.game.view.ChronicleView;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;

/**
 * The run chronicle (Stage 15A) against PostgreSQL: the story is rebuilt from stored turns only,
 * in order, without duplicates, gaps or leaks, and reading it never calls AI or writes.
 * Same configuration as {@link TurnOrchestrationIntegrationTest}, so the context is shared.
 */
@SpringBootTest(properties = { "game.api.invite-codes=" + GameDriver.INVITE, "game.api.limits.turns-per-minute-per-run=10000",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class ChronicleIntegrationTest {

	@Autowired
	private RunCreationService creation;
	@Autowired
	private TurnService turns;
	@Autowired
	private GameViewService views;
	@Autowired
	private ChronicleService chronicles;
	@Autowired
	private TurnStore turnStore;
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

	private ChronicleView chronicle(UUID runId) {
		return chronicles.chronicle(runId, Optional.empty(), ChronicleService.DEFAULT_LIMIT);
	}

	private ChronicleView.Turn turn(ChronicleView chronicle, int number) {
		return chronicle.turns().stream().filter(t -> t.turnNumber() == number).findFirst().orElseThrow();
	}

	/** Every stored turn-request row, to prove reads write nothing. */
	private List<Map<String, Object>> turnRows(UUID runId) {
		return jdbc.queryForList("SELECT request_key, status, turn_number, response, player_input, narration_owner "
				+ "FROM run_turn WHERE run_id = ? ORDER BY created_at, request_key", runId);
	}

	@Test
	void flywayAppliedV7() {
		assertThat(game.count("SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success")).isEqualTo(1);
	}

	@Test
	void aNewRunHasItsIntroductionAndOpeningPlaceButNoTurns() {
		UUID runId = game.newRun();
		GameView view = game.view(runId);

		ChronicleView chronicle = chronicle(runId);

		assertThat(chronicle.status()).isEqualTo("ACTIVE");
		assertThat(chronicle.latestTurnNumber()).isZero();
		assertThat(chronicle.turns()).isEmpty();
		assertThat(chronicle.nextBefore()).isNull();
		assertThat(chronicle.opening().introduction()).isEqualTo(view.introduction());
		assertThat(chronicle.opening().scene()).isEqualTo(view.location().scene()).isEqualTo("The Last Lantern");
		assertThat(chronicle.opening().zone()).isEqualTo(view.location().zone().name());
	}

	@Test
	void theRegionIsNamedInTheViewOnlyInsideARegion() {
		UUID runId = game.newRun();
		assertThat(game.view(runId).location().region()).isNull();

		game.placeAmongEnemies(runId, 1);

		assertThat(game.view(runId).location().region()).isEqualTo("Hollow Chapel");
	}

	@Test
	void acceptedWordingIsKeptExactlyAndCommandsAreMarked() {
		UUID runId = game.newRun();
		SceneInstance scene = game.placeAmongEnemies(runId, 1);
		String alias = game.alias(scene, game.visibleEnemies(scene).getFirst().entityId());
		String words = "  I slash at it — \"for the Lantern!\" <script>alert('x')</script> ünïcödé ✦\nand again  ";
		CountingAi model = new CountingAi().answerStructured(GameDriver.attackDocument(alias));
		ai.use(model);

		Reply freeText = game.turn(runId, words);
		ai.reset();
		game.setPlayerHp(runId, game.maxHp(runId));
		GameView now = game.view(runId);
		Reply command = game.turn(runId, now.awaiting().equals("DEFENSE") ? "/defend parry" : "/hold");

		assertThat(freeText.status()).as(freeText.raw()).isEqualTo(200);
		assertThat(command.status()).as(command.raw()).isEqualTo(200);
		ChronicleView chronicle = chronicle(runId);
		assertThat(turn(chronicle, 1).action()).isEqualTo(new ChronicleView.Action(words, "FREE_TEXT"));
		assertThat(turn(chronicle, 2).action().kind()).isEqualTo("COMMAND");
		assertThat(turn(chronicle, 1).narration()).isEqualTo(new GameView.NarrationView(
				freeText.body().path("narration").path("text").asString(), freeText.body().path("narration").path("source").asString()));
		assertThat(turn(chronicle, 1).narrationPending()).isFalse();
	}

	@Test
	void rejectedAndStaleRequestsLeaveNoEntryAndNoWording() {
		UUID runId = game.newRun();
		UUID rejected = UUID.randomUUID();
		UUID stale = UUID.randomUUID();

		assertThat(game.turn(runId, rejected, "/fly over the walls", 0).status()).isEqualTo(422);
		assertThat(game.turn(runId, "/hold").status()).isEqualTo(200);
		assertThat(game.turn(runId, stale, "/hold", 0).status()).isEqualTo(409);

		assertThat(chronicle(runId).turns()).extracting(ChronicleView.Turn::turnNumber).containsExactly(1);
		assertThat(turnStore.find(runId, rejected).orElseThrow().playerInput()).isEmpty();
		assertThat(turnStore.find(runId, stale).orElseThrow().playerInput()).isEmpty();
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND player_input IS NOT NULL", runId)).isEqualTo(1);
	}

	@Test
	void replaysNeverDuplicateAnEntry() {
		UUID runId = game.newRun();
		UUID key = UUID.randomUUID();

		game.turn(runId, key, "/hold", 0);
		game.turn(runId, key, "/hold", 0);
		game.turn(runId, key, "/hold", 0);

		assertThat(chronicle(runId).turns()).hasSize(1);
		assertThat(chronicle(runId).latestTurnNumber()).isEqualTo(1);
	}

	@Test
	void pagesRunOldestFirstAndCursorBackToTheOpening() {
		UUID runId = game.newRun();
		for (int i = 0; i < 5; i++) {
			assertThat(game.turn(runId, "/hold").status()).isEqualTo(200);
		}

		ChronicleView latest = chronicles.chronicle(runId, Optional.empty(), 2);
		ChronicleView middle = chronicles.chronicle(runId, Optional.ofNullable(latest.nextBefore()), 2);
		ChronicleView first = chronicles.chronicle(runId, Optional.ofNullable(middle.nextBefore()), 2);
		ChronicleView all = chronicle(runId);

		assertThat(latest.turns()).extracting(ChronicleView.Turn::turnNumber).containsExactly(4, 5);
		assertThat(latest.nextBefore()).isEqualTo(4);
		assertThat(latest.opening()).isNull();
		assertThat(middle.turns()).extracting(ChronicleView.Turn::turnNumber).containsExactly(2, 3);
		assertThat(middle.nextBefore()).isEqualTo(2);
		assertThat(middle.opening()).isNull();
		assertThat(first.turns()).extracting(ChronicleView.Turn::turnNumber).containsExactly(1);
		assertThat(first.nextBefore()).isNull();
		assertThat(first.opening()).isNotNull();
		assertThat(all.turns()).extracting(ChronicleView.Turn::turnNumber).containsExactly(1, 2, 3, 4, 5);
		assertThat(all.nextBefore()).isNull();
		assertThat(chronicles.chronicle(runId, Optional.of(1), 5).turns()).isEmpty();
		assertThat(chronicles.chronicle(runId, Optional.of(1), 5).opening()).isNotNull();
		assertThat(chronicles.chronicle(runId, Optional.of(4), 3).turns()).extracting(ChronicleView.Turn::turnNumber)
				.containsExactly(1, 2, 3);
	}

	@Test
	void anEnemyAttacksCueAndNarrationStayInHistoryAfterItIsDefended() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		GameView pending = game.untilPending(runId);
		int attackTurn = pending.lastTurn().turnNumber();
		game.setPlayerHp(runId, game.maxHp(runId));

		assertThat(game.turn(runId, "/defend parry").status()).isEqualTo(200);

		assertThat(game.count("SELECT count(*) FROM pending_attack WHERE run_id = ?", runId)).isZero();
		ChronicleView.Enemy enemy = turn(chronicle(runId), attackTurn).enemy();
		assertThat(enemy.action()).isEqualTo("ATTACK");
		assertThat(enemy.attacker()).isEqualTo(pending.pendingAttack().attacker());
		assertThat(enemy.cueText()).isEqualTo(pending.pendingAttack().cueText());
		assertThat(enemy.narration()).isEqualTo(pending.pendingAttack().narration());
		ChronicleView.Turn defended = turn(chronicle(runId), attackTurn + 1);
		assertThat(defended.action().text()).isEqualTo("/defend parry");
		assertThat(defended.enemy()).isNull();
	}

	@Test
	void anUnfinishedTurnIsShownPendingWithoutNarratingOrWriting() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		UUID key = UUID.randomUUID();
		ai.use(new CountingAi().crashingText());
		assertThatThrownBy(() -> game.turn(runId, key, "/hold", 0)).isInstanceOf(IllegalStateException.class);
		CountingAi watching = new CountingAi();
		ai.use(watching);
		List<Map<String, Object>> before = turnRows(runId);

		ChronicleView chronicle = chronicle(runId);

		assertThat(watching.total()).as("reading history never calls a model").isZero();
		assertThat(turnRows(runId)).as("reading history never writes").isEqualTo(before);
		assertThat(turnStore.find(runId, key).orElseThrow().status()).isEqualTo(TurnStatus.MECHANICS_COMMITTED);
		ChronicleView.Turn unfinished = turn(chronicle, 1);
		assertThat(unfinished.narrationPending()).isTrue();
		assertThat(unfinished.narration()).isNull();
		assertThat(unfinished.action().text()).isEqualTo("/hold");
		if (unfinished.enemy() != null && unfinished.enemy().action().equals("ATTACK")) {
			assertThat(unfinished.enemy().cueText()).isEqualTo(game.view(runId).pendingAttack().cueText());
			assertThat(unfinished.enemy().narration()).isNull();
		}

		Reply recovered = game.turn(runId, key, "/hold", 0);
		ChronicleView.Turn finished = turn(chronicle(runId), 1);
		assertThat(finished.narrationPending()).isFalse();
		assertThat(finished.narration().text()).isEqualTo(recovered.body().path("narration").path("text").asString());
		assertThat(watching.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(1);
	}

	@Test
	void sceneTransitionsNameTheSceneAndArrivalZone() {
		UUID runId = game.newRun();
		SceneInstance origin = game.place(runId, s -> !s.state().exits().isEmpty());
		SceneExit exit = origin.state().exits().getFirst();
		world.setPlayerLocation(runId, new PlayerLocation(origin.id(), exit.zoneId()));
		GameView here = game.view(runId);
		String exitAlias = here.scene().exits().stream().filter(x -> x.zone().equals(here.location().zone().alias()))
				.findFirst().orElseThrow().alias();

		assertThat(game.turn(runId, "/move " + exitAlias).status()).isEqualTo(200);

		GameView arrived = game.view(runId);
		assertThat(turn(chronicle(runId), 1).enteredScene())
				.isEqualTo(new ChronicleView.Place(arrived.location().scene(), arrived.location().zone().name()));
	}

	@Test
	void deathEndsTheChronicle() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		for (int i = 0; i < 60 && game.view(runId).status().equals("ACTIVE"); i++) {
			game.untilPending(runId);
			game.setPlayerHp(runId, 1);
			assertThat(game.turn(runId, "/defend parry").status()).isEqualTo(200);
		}

		ChronicleView chronicle = chronicle(runId);

		assertThat(chronicle.status()).isEqualTo("DEAD");
		assertThat(chronicle.turns().getLast().ending()).isEqualTo("DEAD");
		assertThat(chronicle.turns().getLast().narration().text()).contains("The run ends here");
		assertThat(chronicle.turns().subList(0, chronicle.turns().size() - 1)).allMatch(t -> t.ending() == null);
	}

	@Test
	void victoryEndsTheChronicle() {
		UUID runId = game.newRun();
		SceneInstance chapel = game.place(runId, s -> game.visibleEnemies(s).stream()
				.anyMatch(e -> e.definitionCode().equals(TerminalRules.GUARDIAN_CODE)));
		EnemyInstance guardian = game.visibleEnemies(chapel).stream()
				.filter(e -> e.definitionCode().equals(TerminalRules.GUARDIAN_CODE)).findFirst().orElseThrow();
		String alias = game.alias(chapel, guardian.entityId());
		game.setEnemyHp(chapel, guardian.entityId(), 1);
		for (int i = 0; i < 40 && game.view(runId).status().equals("ACTIVE"); i++) {
			game.setPlayerHp(runId, game.maxHp(runId));
			game.strike(runId, alias);
		}

		ChronicleView chronicle = chronicle(runId);

		assertThat(chronicle.status()).isEqualTo("VICTORIOUS");
		assertThat(chronicle.turns().getLast().ending()).isEqualTo("VICTORIOUS");
	}

	@Test
	void turnsRecordedBeforeWordingWasKeptStillAppear() {
		UUID runId = game.newRun();
		game.turn(runId, "/hold");
		game.turn(runId, "/hold");
		jdbc.update("UPDATE run_turn SET player_input = NULL WHERE run_id = ? AND turn_number = 1", runId);

		ChronicleView chronicle = chronicle(runId);

		assertThat(turn(chronicle, 1).action()).isNull();
		assertThat(turn(chronicle, 1).narration()).isNotNull();
		assertThat(turn(chronicle, 2).action().text()).isEqualTo("/hold");
	}

	@Test
	void badPagingAndUnknownRunsAreRefused() {
		UUID runId = game.newRun();

		for (int limit : new int[] { 0, -1, ChronicleService.MAX_LIMIT + 1 }) {
			assertThatThrownBy(() -> chronicles.chronicle(runId, Optional.empty(), limit))
					.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
		}
		assertThatThrownBy(() -> chronicles.chronicle(runId, Optional.of(0), 5))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> chronicles.chronicle(UUID.randomUUID(), Optional.empty(), 5))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_NOT_FOUND));
	}
}
