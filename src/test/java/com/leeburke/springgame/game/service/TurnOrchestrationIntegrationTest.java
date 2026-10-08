package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.game.TerminalRules;
import com.leeburke.springgame.game.TurnStatus;
import com.leeburke.springgame.game.service.GameDriver.Reply;
import com.leeburke.springgame.game.service.GameTestConfiguration.MovableClock;
import com.leeburke.springgame.game.service.GameTestConfiguration.SwitchableAi;
import com.leeburke.springgame.game.view.GameView;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.TurnStore;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;

/**
 * The Stage 14 turn pipeline against PostgreSQL, with scripted AI only: the run lifecycle, the
 * encounter (pending attacks, mandatory defense, fallen enemies, death, victory), exits,
 * idempotency, leases and fencing, finalisation and crash recovery.
 */
@SpringBootTest(properties = { "game.api.invite-codes=" + GameDriver.INVITE, "game.api.limits.turns-per-minute-per-run=10000",
		"game.api.limits.creations-per-hour-per-invite=10000", "game.api.limits.creations-per-day=10000" })
@Import({ PostgresTestcontainersConfiguration.class, GameTestConfiguration.class })
class TurnOrchestrationIntegrationTest {

	@Autowired
	private RunCreationService creation;
	@Autowired
	private TurnService turns;
	@Autowired
	private GameViewService views;
	@Autowired
	private NarrationFinalizer finalizer;
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
	@Autowired
	private MovableClock clock;

	private GameDriver game;

	@BeforeEach
	void setUp() {
		game = new GameDriver(creation, turns, views, world, enemies, runs, jdbc);
	}

	@AfterEach
	void resetAi() {
		ai.reset();
	}

	private CountingAi counting() {
		CountingAi counting = new CountingAi();
		ai.use(counting);
		return counting;
	}

	/** A strict interpreter answer: SLASH at the given creature alias with weapon_1. */
	private static String attackDocument(String alias) {
		String target = "{\"kind\":\"ENTITY\",\"alias\":\"" + alias + "\",\"bodyPart\":null,\"specificity\":\"EXPLICIT\"}";
		String payload = "{\"weapon\":\"weapon_1\",\"method\":\"SLASH\",\"template\":\"HORIZONTAL_SWING\",\"target\":" + target
				+ ",\"approach\":\"NORMAL\",\"purpose\":\"DAMAGE\"}";
		StringBuilder step = new StringBuilder("{\"relation\":\"START\",\"action\":\"ATTACK\"");
		for (String slot : List.of("attack", "defend", "move", "interact", "observe", "useAbility", "useItem", "communicate")) {
			step.append(",\"").append(slot).append("\":").append(slot.equals("attack") ? payload : "null");
		}
		step.append('}');
		return "{\"schemaVersion\":1,\"supported\":true,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[" + step
				+ "],\"unresolved\":[]}";
	}

	// --- Run creation ---

	@Test
	void creationIsIdempotentAndNeverRotatesTheToken() {
		String token = GameDriver.newToken();
		String key = UUID.randomUUID().toString();

		UUID runId = creation.create(GameDriver.INVITE, key, "Bearer " + token, "10.0.0.1").runId();
		UUID again = creation.create(GameDriver.INVITE, key, "Bearer " + token, "10.0.0.1").runId();

		assertThat(again).isEqualTo(runId);
		assertThat(game.count("SELECT count(*) FROM run_session WHERE creation_key = ?::uuid", key)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT access_token_hash FROM run_session WHERE run_id = ?", String.class, runId))
				.isEqualTo(RunTokens.hash(token)).doesNotContain(token);
		assertThatThrownBy(() -> creation.create(GameDriver.INVITE, key, "Bearer " + GameDriver.newToken(), "10.0.0.1"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
		assertThatThrownBy(() -> creation.create(GameDriver.INVITE, UUID.randomUUID().toString(), "Bearer " + token, "10.0.0.1"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.TOKEN_IN_USE));
	}

	@Test
	void aNewRunIsActiveWithItsWorldAndIntroductionAndNothingPending() {
		UUID runId = game.newRun();
		GameView view = game.view(runId);

		assertThat(view.status()).isEqualTo("ACTIVE");
		assertThat(view.stateVersion()).isZero();
		assertThat(view.awaiting()).isEqualTo("ACTION");
		assertThat(view.introduction()).isNotNull();
		assertThat(view.introduction().source()).isEqualTo("FALLBACK");
		assertThat(view.pendingAttack()).isNull();
		assertThat(view.lastTurn()).isNull();
		assertThat(view.character().hp()).isEqualTo(view.character().maxHp());
	}

	@Test
	void anInterruptedCreationResumesWithoutRegeneratingTheWorld() {
		String token = GameDriver.newToken();
		String key = UUID.randomUUID().toString();
		ai.use(new CountingAi().crashingText());

		assertThatThrownBy(() -> creation.create(GameDriver.INVITE, key, "Bearer " + token, "10.0.0.2"))
				.isInstanceOf(IllegalStateException.class);
		UUID runId = jdbc.queryForObject("SELECT run_id FROM run_session WHERE creation_key = ?::uuid", UUID.class, key);
		assertThat(jdbc.queryForObject("SELECT status FROM run_session WHERE run_id = ?", String.class, runId)).isEqualTo("INITIALIZING");
		List<UUID> scenes = game.scenes(runId).stream().map(SceneInstance::id).toList();
		assertThat(scenes).isNotEmpty();
		assertThatThrownBy(() -> game.view(runId))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_INITIALIZING));
		assertThatThrownBy(() -> game.turn(runId, "/hold"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_INITIALIZING));

		ai.reset();
		assertThat(creation.create(GameDriver.INVITE, key, "Bearer " + token, "10.0.0.2").runId()).isEqualTo(runId);
		assertThat(game.scenes(runId).stream().map(SceneInstance::id).toList()).isEqualTo(scenes);
		assertThat(game.view(runId).status()).isEqualTo("ACTIVE");
	}

	// --- The encounter ---

	@Test
	void anEnemyAttackIsPersistedWithItsCueAndNeverRerolledOnReload() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);

		GameView pending = game.untilPending(runId);
		GameView reloaded = game.view(runId);

		assertThat(pending.pendingAttack().cueText()).startsWith("Incoming: ");
		assertThat(reloaded.pendingAttack()).isEqualTo(pending.pendingAttack());
		assertThat(jdbc.queryForObject("SELECT cue_text FROM pending_attack WHERE run_id = ?", String.class, runId))
				.isEqualTo(pending.pendingAttack().cueText());
		assertThat(pending.pendingAttack().narration()).isNotNull();
		assertThat(pending.pendingAttack().narration().source()).isEqualTo("FALLBACK");
		assertThat(pending.finalizing()).isFalse();
	}

	@Test
	void whileAnAttackIsPendingOnlyADefenseIsAcceptedAndARejectionChangesNothing() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		GameView pending = game.untilPending(runId);
		String attackRef = jdbc.queryForObject("SELECT attack_ref FROM pending_attack WHERE run_id = ?", String.class, runId);
		String target = pending.scene().creatures().getFirst().alias();

		Reply rejected = game.turn(runId, "/attack " + target + " slash with weapon_1");

		assertThat(rejected.status()).isEqualTo(422);
		assertThat(rejected.code()).isEqualTo("DEFENSE_REQUIRED");
		GameView after = game.view(runId);
		assertThat(after.stateVersion()).isEqualTo(pending.stateVersion());
		assertThat(jdbc.queryForObject("SELECT attack_ref FROM pending_attack WHERE run_id = ?", String.class, runId)).isEqualTo(attackRef);
		assertThat(game.count("SELECT turn_number FROM run_session WHERE run_id = ?", runId)).isEqualTo(pending.lastTurn().turnNumber());
	}

	@Test
	void aDefenseOnlyTurnConsumesTheAttackAndProvokesNoOther() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		game.untilPending(runId);
		game.setPlayerHp(runId, game.maxHp(runId));

		Reply defended = game.turn(runId, "/defend parry");

		assertThat(defended.status()).as(defended.raw()).isEqualTo(200);
		assertThat(defended.body().path("enemyTurn").isNull()).isTrue();
		assertThat(defended.body().path("view").path("awaiting").asString()).isIn("ACTION", "NONE");
		assertThat(game.count("SELECT count(*) FROM pending_attack WHERE run_id = ?", runId)).isZero();

		// Repeated defense-only turns cannot loop the encounter: with nothing incoming, nothing responds.
		for (int i = 0; i < 3 && game.view(runId).status().equals("ACTIVE"); i++) {
			Reply again = game.turn(runId, "/defend parry");
			assertThat(again.status()).as(again.raw()).isEqualTo(200);
			assertThat(again.body().path("overall").asString()).isEqualTo("MECHANICS_UNAVAILABLE");
			assertThat(again.body().path("enemyTurn").isNull()).isTrue();
			assertThat(game.count("SELECT count(*) FROM pending_attack WHERE run_id = ?", runId)).isZero();
		}
	}

	@Test
	void defendingThenCounterattackingLetsAnEnemyRespond() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		GameView pending = game.untilPending(runId);
		game.setPlayerHp(runId, game.maxHp(runId));
		String target = pending.scene().creatures().stream().filter(c -> c.condition().equals("ACTIVE")).findFirst().orElseThrow().alias();

		Reply reply = game.turn(runId, "/defend parry ; /attack " + target + " slash with weapon_1");

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		boolean anyoneLeft = false;
		for (var creature : reply.body().path("view").path("scene").path("creatures")) {
			anyoneLeft |= creature.path("condition").asString().equals("ACTIVE");
		}
		if (anyoneLeft) {
			assertThat(reply.body().path("enemyTurn").path("action").asString()).isIn("ATTACK", "HOLD");
		}
	}

	@Test
	void aFallenEnemyStaysVisibleButNeverActsAgain() {
		UUID runId = game.newRun();
		SceneInstance scene = game.placeAmongEnemies(runId, 2);
		EnemyInstance victim = game.visibleEnemies(scene).getFirst();
		String alias = game.alias(scene, victim.entityId());
		game.setEnemyHp(scene, victim.entityId(), 1);

		for (int i = 0; i < 40 && enemies.findEnemy(scene.id(), victim.entityId()).orElseThrow().currentHp() > 0; i++) {
			game.setPlayerHp(runId, game.maxHp(runId));
			assertThat(game.strike(runId, alias).status()).isEqualTo(200);
		}

		GameView view = game.view(runId);
		GameView.CreatureView fallen = view.scene().creatures().stream().filter(c -> c.alias().equals(alias)).findFirst().orElseThrow();
		assertThat(fallen.condition()).isEqualTo("FALLEN");
		int fellOnTurn = game.count("SELECT turn_number FROM run_session WHERE run_id = ?", runId);

		for (int i = 0; i < 6 && game.view(runId).status().equals("ACTIVE"); i++) {
			game.setPlayerHp(runId, game.maxHp(runId));
			String other = game.view(runId).scene().creatures().stream().filter(c -> c.condition().equals("ACTIVE"))
					.findFirst().orElseThrow().alias();
			assertThat(game.strike(runId, other).status()).isEqualTo(200);
		}
		assertThat(game.view(runId).scene().creatures()).filteredOn(c -> c.alias().equals(alias))
				.singleElement().extracting(GameView.CreatureView::condition).isEqualTo("FALLEN");
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number > ? AND enemy_entity_id = ?",
				runId, fellOnTurn, victim.entityId())).as("a fallen enemy never acts").isZero();
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number > ? AND enemy_entity_id IS NOT NULL",
				runId, fellOnTurn)).as("the others still do").isPositive();
	}

	@Test
	void reachingZeroHpEndsTheRunInDeath() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		for (int i = 0; i < 60 && game.view(runId).status().equals("ACTIVE"); i++) {
			game.untilPending(runId);
			game.setPlayerHp(runId, 1);
			Reply reply = game.turn(runId, "/defend parry");
			assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		}

		GameView dead = game.view(runId);
		assertThat(dead.status()).isEqualTo("DEAD");
		assertThat(dead.awaiting()).isEqualTo("NONE");
		assertThat(dead.character().hp()).isZero();
		assertThat(dead.lastTurn().narration().text()).contains("The run ends here");
		assertThatThrownBy(() -> game.turn(runId, "/hold"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_FINISHED));
	}

	@Test
	void fellingTheChapelGuardianIsVictory() {
		UUID runId = game.newRun();
		SceneInstance chapel = game.place(runId, s -> game.visibleEnemies(s).stream()
				.anyMatch(e -> e.definitionCode().equals(TerminalRules.GUARDIAN_CODE)));
		EnemyInstance guardian = game.visibleEnemies(chapel).stream()
				.filter(e -> e.definitionCode().equals(TerminalRules.GUARDIAN_CODE)).findFirst().orElseThrow();
		String alias = game.alias(chapel, guardian.entityId());
		game.setEnemyHp(chapel, guardian.entityId(), 1);

		for (int i = 0; i < 40 && game.view(runId).status().equals("ACTIVE"); i++) {
			game.setPlayerHp(runId, game.maxHp(runId));
			assertThat(game.strike(runId, alias).status()).isEqualTo(200);
		}

		GameView won = game.view(runId);
		assertThat(won.status()).isEqualTo("VICTORIOUS");
		assertThat(won.awaiting()).isEqualTo("NONE");
		assertThat(won.lastTurn().narration().text()).contains("falls");
		assertThatThrownBy(() -> game.turn(runId, "/hold"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.RUN_FINISHED));
	}

	@Test
	void leavingThroughAnExitTravelsToTheReturnExitAndNoEnemyFollows() {
		UUID runId = game.newRun();
		SceneInstance origin = game.place(runId, s -> !s.state().exits().isEmpty());
		SceneExit exit = origin.state().exits().getFirst();
		world.setPlayerLocation(runId, new com.leeburke.springgame.world.PlayerLocation(origin.id(), exit.zoneId()));
		String exitAlias = game.view(runId).scene().exits().stream()
				.filter(x -> x.zone().equals(game.view(runId).location().zone().alias())).findFirst().orElseThrow().alias();

		Reply reply = game.turn(runId, "/move " + exitAlias);

		assertThat(reply.status()).as(reply.raw()).isEqualTo(200);
		assertThat(reply.body().path("changes").path("enteredScene").isNull()).isFalse();
		assertThat(reply.body().path("enemyTurn").isNull()).isTrue();
		assertThat(reply.body().path("narration").path("text").asString()).startsWith("You leave through the exit")
				.doesNotContain("the The");
		var location = world.findPlayerLocation(runId).orElseThrow();
		assertThat(location.sceneId()).isEqualTo(exit.destinationSceneId());
		SceneInstance destination = world.findScene(exit.destinationSceneId()).orElseThrow();
		assertThat(destination.discovered()).isTrue();
		assertThat(destination.state().exits()).anyMatch(back -> back.destinationSceneId().equals(origin.id())
				&& back.zoneId().equals(location.zoneId()));
		assertThat(reply.body().path("view").path("location").path("scene").asString())
				.isEqualTo(reply.body().path("changes").path("enteredScene").asString());
	}

	// --- Idempotency, staleness and AI use ---

	@Test
	void aReplayReturnsTheStoredAnswerWithoutApplyingOrCallingAnything() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		CountingAi counting = counting();
		UUID key = UUID.randomUUID();

		Reply first = game.turn(runId, key, "/hold", 0);
		int callsAfterFirst = counting.total();
		Reply replay = game.turn(runId, key, "/hold", 0);

		assertThat(first.status()).isEqualTo(200);
		assertThat(replay.raw()).isEqualTo(first.raw());
		assertThat(counting.total()).isEqualTo(callsAfterFirst);
		assertThat(counting.calls(AiRole.ACTION_INTERPRETER)).as("commands never reach the interpreter").isZero();
		assertThat(counting.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(1);
		assertThat(counting.calledInsideTransaction()).isFalse();
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number IS NOT NULL", runId)).isEqualTo(1);
		assertThat(first.body().path("narration").path("text").asString()).isEqualTo(CountingAi.PROSE);
		assertThatThrownBy(() -> game.turn(runId, key, "/listen", 0))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
	}

	@Test
	void aStaleViewIsRefusedBeforeAnyAiCallAndReplaysTheSame() {
		UUID runId = game.newRun();
		CountingAi counting = counting();
		assertThat(game.turn(runId, "/hold").status()).isEqualTo(200);
		int calls = counting.total();
		UUID key = UUID.randomUUID();

		Reply stale = game.turn(runId, key, "I swing at whatever is nearest", 0);
		Reply replay = game.turn(runId, key, "I swing at whatever is nearest", 0);

		assertThat(stale.status()).isEqualTo(409);
		assertThat(stale.code()).isEqualTo("STALE_VIEW");
		assertThat(replay.raw()).isEqualTo(stale.raw());
		assertThat(counting.total()).isEqualTo(calls);
		assertThat(jdbc.queryForObject("SELECT status FROM run_turn WHERE run_id = ? AND request_key = ?", String.class, runId, key))
				.isEqualTo("STALE");
	}

	@Test
	void rejectionsAreStoredConsumeNoTurnAndCallNoModel() {
		UUID runId = game.newRun();
		CountingAi counting = counting();
		UUID key = UUID.randomUUID();

		Reply bad = game.turn(runId, key, "/fly to the moon", 0);

		assertThat(bad.status()).isEqualTo(422);
		assertThat(bad.code()).isEqualTo("INVALID_COMMAND");
		assertThat(game.turn(runId, key, "/fly to the moon", 0).raw()).isEqualTo(bad.raw());
		assertThat(counting.total()).isZero();
		assertThat(game.view(runId).stateVersion()).isZero();
	}

	@Test
	void aTransientAiFailureReleasesTheKeyForARetry() {
		UUID runId = game.newRun();
		SceneInstance scene = game.placeAmongEnemies(runId, 1);
		String alias = game.alias(scene, game.visibleEnemies(scene).getFirst().entityId());
		UUID key = UUID.randomUUID();
		ai.use(new CountingAi().failStructured(AiResponse.Failure.of(AiFailureKind.TIMEOUT)));

		assertThatThrownBy(() -> game.turn(runId, key, "I cut at the nearest one", 0))
				.isInstanceOfSatisfying(GameException.class, e -> {
					assertThat(e.code()).isEqualTo(ErrorCode.INTERPRETATION_FAILED);
					assertThat(e.body().error().reason()).isEqualTo("AI_UNAVAILABLE");
				});
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ?", runId)).isZero();

		CountingAi working = counting().answerStructured(attackDocument(alias));
		Reply retried = game.turn(runId, key, "I cut at the nearest one", 0);

		assertThat(retried.status()).as(retried.raw()).isEqualTo(200);
		assertThat(working.calls(AiRole.ACTION_INTERPRETER)).isEqualTo(1);
		assertThat(working.calledInsideTransaction()).isFalse();
	}

	// --- Leases, fencing and concurrency ---

	@Test
	void anExpiredInterpretingLeaseIsTakenOverByTheSameKey() {
		UUID runId = game.newRun();
		UUID key = UUID.randomUUID();
		turnStore.insertInterpreting(runId, key, RunTokens.sha256Hex("0\n/hold"), 0, UUID.randomUUID(),
				clock.instant().plusSeconds(60), clock.instant());

		assertThatThrownBy(() -> game.turn(runId, key, "/hold", 0))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
		clock.advance(Duration.ofMinutes(3));

		assertThat(game.turn(runId, key, "/hold", 0).status()).isEqualTo(200);
	}

	@Test
	void anotherKeysAbandonedTurnIsClearedOnlyAfterItsLeaseExpires() {
		UUID runId = game.newRun();
		UUID abandoned = UUID.randomUUID();
		turnStore.insertInterpreting(runId, abandoned, "x", 0, UUID.randomUUID(), clock.instant().plusSeconds(60), clock.instant());

		assertThatThrownBy(() -> game.turn(runId, "/hold"))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));
		clock.advance(Duration.ofMinutes(3));

		assertThat(game.turn(runId, "/hold").status()).isEqualTo(200);
		assertThat(turnStore.find(runId, abandoned)).isEmpty();
	}

	@Test
	void aRequestWhoseLeaseWasTakenOverCommitsNothing() {
		UUID runId = game.newRun();
		SceneInstance scene = game.placeAmongEnemies(runId, 1);
		String alias = game.alias(scene, game.visibleEnemies(scene).getFirst().entityId());
		UUID key = UUID.randomUUID();
		counting().answerStructured(attackDocument(alias)).beforeStructured(() -> {
			// While the slow model call runs, the lease expires and a retry takes the turn over.
			clock.advance(Duration.ofMinutes(3));
			assertThat(turnStore.takeOver(runId, key, UUID.randomUUID(), clock.instant().plusSeconds(120), clock.instant())).isTrue();
		});

		assertThatThrownBy(() -> game.turn(runId, key, "I cut at it", 0))
				.isInstanceOfSatisfying(GameException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS));

		assertThat(game.view(runId).stateVersion()).isZero();
		assertThat(turnStore.find(runId, key).orElseThrow().status()).isEqualTo(TurnStatus.INTERPRETING);
		assertThat(game.count("SELECT count(*) FROM pending_attack WHERE run_id = ?", runId)).isZero();
	}

	@Test
	void concurrentTurnsOnOneViewCommitExactlyOnce() throws Exception {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Future<Object>> results = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			Callable<Object> task = () -> {
				start.await();
				try {
					return game.turn(runId, UUID.randomUUID(), "/hold", 0).status();
				} catch (GameException e) {
					return e.code();
				}
			};
			results.add(pool.submit(task));
		}
		start.countDown();
		List<Object> outcomes = new ArrayList<>();
		for (Future<Object> result : results) {
			outcomes.add(result.get());
		}
		pool.shutdown();

		assertThat(outcomes).filteredOn(o -> o.equals(200)).hasSize(1);
		assertThat(outcomes).filteredOn(o -> !o.equals(200)).allMatch(o -> o.equals(409) || o == ErrorCode.REQUEST_IN_PROGRESS);
		assertThat(game.count("SELECT turn_number FROM run_session WHERE run_id = ?", runId)).isEqualTo(1);
		assertThat(game.count("SELECT count(*) FROM run_turn WHERE run_id = ? AND turn_number IS NOT NULL", runId)).isEqualTo(1);
	}

	// --- Finalisation and crash recovery ---

	@Test
	void aTurnCommittedBeforeACrashIsRecoveredFromItsSummaryWithTheSameKey() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		UUID key = UUID.randomUUID();
		CountingAi crashing = new CountingAi().crashingText();
		ai.use(crashing);

		assertThatThrownBy(() -> game.turn(runId, key, "/hold", 0)).isInstanceOf(IllegalStateException.class);
		assertThat(turnStore.find(runId, key).orElseThrow().status()).isEqualTo(TurnStatus.MECHANICS_COMMITTED);

		CountingAi watching = counting();
		GameView during = game.view(runId);
		assertThat(watching.total()).as("GET never calls a model").isZero();
		assertThat(during.finalizing()).isTrue();
		assertThat(during.lastTurn().narration()).isNull();
		assertThat(during.stateVersion()).isEqualTo(1);
		if (during.pendingAttack() != null) {
			assertThat(during.pendingAttack().cueText()).isNotBlank();
			assertThat(during.pendingAttack().narration()).isNull();
		}

		Reply recovered = game.turn(runId, key, "/hold", 0);

		assertThat(recovered.status()).as(recovered.raw()).isEqualTo(200);
		assertThat(recovered.body().path("turnNumber").asInt()).isEqualTo(1);
		assertThat(recovered.body().path("narration").path("text").asString()).isEqualTo(CountingAi.PROSE);
		assertThat(recovered.body().path("view").path("finalizing").asBoolean()).isFalse();
		assertThat(watching.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(1);
		assertThat(game.view(runId).stateVersion()).isEqualTo(1);
	}

	@Test
	void aNewTurnFirstFinalisesTheCrashedOne() {
		UUID runId = game.newRun();
		UUID crashedKey = UUID.randomUUID();
		ai.use(new CountingAi().crashingText());
		assertThatThrownBy(() -> game.turn(runId, crashedKey, "/hold", 0)).isInstanceOf(IllegalStateException.class);

		CountingAi working = counting();
		Reply next = game.turn(runId, UUID.randomUUID(), "/hold", 1);

		assertThat(next.status()).as(next.raw()).isEqualTo(200);
		assertThat(next.body().path("turnNumber").asInt()).isEqualTo(2);
		assertThat(turnStore.find(runId, crashedKey).orElseThrow().status()).isEqualTo(TurnStatus.COMPLETED);
		assertThat(working.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(2);
		assertThat(game.turn(runId, crashedKey, "/hold", 0).body().path("turnNumber").asInt()).isEqualTo(1);
	}

	@Test
	void concurrentFinalisationNarratesOnce() throws Exception {
		UUID runId = game.newRun();
		UUID key = UUID.randomUUID();
		ai.use(new CountingAi().crashingText());
		assertThatThrownBy(() -> game.turn(runId, key, "/hold", 0)).isInstanceOf(IllegalStateException.class);
		CountingAi slow = counting().slowText(Duration.ofMillis(400));

		ExecutorService pool = Executors.newFixedThreadPool(3);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> results = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			results.add(pool.submit(() -> {
				start.await();
				try {
					return finalizer.finalizeTurn(runId, key).status();
				} catch (GameException e) {
					return e.code();
				}
			}));
		}
		start.countDown();
		List<Object> outcomes = new ArrayList<>();
		for (Future<Object> result : results) {
			outcomes.add(result.get());
		}
		pool.shutdown();

		assertThat(outcomes).contains(200).allMatch(o -> o.equals(200) || o == ErrorCode.REQUEST_IN_PROGRESS);
		assertThat(slow.calls(AiRole.OUTCOME_NARRATOR)).isEqualTo(1);
		assertThat(turnStore.find(runId, key).orElseThrow().status()).isEqualTo(TurnStatus.COMPLETED);
	}

	@Test
	void theMechanicsSummaryIsEnoughToRebuildTheResponse() {
		UUID runId = game.newRun();
		game.placeAmongEnemies(runId, 1);
		Reply reply = game.turn(runId, "/hold");

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT mechanics_summary ->> 'turnNumber' AS turn, mechanics_summary ->> 'overall' AS overall, "
						+ "mechanics_summary -> 'narration' ->> 'mode' AS mode FROM run_turn WHERE run_id = ? AND turn_number = 1", runId);
		assertThat(row).containsEntry("turn", "1").containsEntry("overall", reply.body().path("overall").asString())
				.containsEntry("mode", "NORMAL");
		assertThat(reply.raw()).doesNotContain("runSeed").doesNotContain("difficulty").doesNotContain("attack-")
				.doesNotContain(world.findPlayerLocation(runId).orElseThrow().sceneId().toString());
	}
}
