package com.leeburke.springgame.persistence;

import static com.leeburke.springgame.world.WorldFixtures.ALTAR;
import static com.leeburke.springgame.world.WorldFixtures.CRYPT;
import static com.leeburke.springgame.world.WorldFixtures.ENTRANCE;
import static com.leeburke.springgame.world.WorldFixtures.hubScene;
import static com.leeburke.springgame.world.WorldFixtures.minimalScene;
import static com.leeburke.springgame.world.WorldFixtures.regionScene;
import static com.leeburke.springgame.world.WorldFixtures.richScene;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.RegionInstance;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;
import com.leeburke.springgame.world.SceneState;

/**
 * WorldStore against real PostgreSQL. Each test creates its own runs and queries by key, so tests
 * do not depend on row order or on each other's data.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class WorldStoreIntegrationTest {

	@Autowired
	private WorldStore world;

	@Autowired
	private GameRunStore runs;

	@Autowired
	private GameContentCatalog catalog;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private UUID runA;
	private UUID runB;

	private UUID newRun() {
		return runs.createRun(7L, new PlayerCharacterGenerator(catalog, new PlayerStatGenerator())
				.generate(new SplittableRandom(7))).id();
	}

	@BeforeEach
	void createRuns() {
		runA = newRun();
		runB = newRun();
	}

	private RegionInstance persistedRegion(UUID runId) {
		RegionInstance region = new RegionInstance(UUID.randomUUID(), runId, "HOLLOW_CHAPEL");
		world.persistRegion(region);
		return region;
	}

	private SceneInstance persistedRegionScene(UUID runId, SceneState state) {
		SceneInstance scene = regionScene(UUID.randomUUID(), runId, persistedRegion(runId).id(), 991L, state);
		world.persistScene(scene);
		return scene;
	}

	// --- Round trips ---

	@Test
	void regionRoundTrips() {
		RegionInstance region = persistedRegion(runA);
		assertThat(world.findRegion(region.id())).contains(region);
		assertThat(world.findRegion(UUID.randomUUID())).isEmpty();
	}

	@Test
	void hubSceneRoundTripsWithoutRegionOrSeed() {
		SceneInstance hub = hubScene(UUID.randomUUID(), runA, minimalScene());
		world.persistScene(hub);

		assertThat(world.findScene(hub.id())).contains(hub);
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT scene_kind, region_id, scene_seed, definition_code FROM scene_instance WHERE id = ?", hub.id());
		assertThat(row).containsEntry("scene_kind", "HUB").containsEntry("definition_code", "THE_LAST_LANTERN");
		assertThat(row.get("region_id")).isNull();
		assertThat(row.get("scene_seed")).isNull();
	}

	@Test
	void regionSceneRoundTripsWithRegionSeedAndRichState() {
		SceneInstance scene = persistedRegionScene(runA, richScene());

		SceneInstance loaded = world.findScene(scene.id()).orElseThrow();

		assertThat(loaded).isEqualTo(scene);
		assertThat(loaded.kind()).isEqualTo(SceneKind.REGION);
		assertThat(loaded.sceneSeed()).hasValue(991L);
		assertThat(loaded.state()).isEqualTo(richScene());
	}

	@Test
	void unknownSceneIsAbsent() {
		assertThat(world.findScene(UUID.randomUUID())).isEmpty();
	}

	// --- Revisions ---

	@Test
	void newSceneStartsAtRevisionZero() {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());
		assertThat(world.findScene(scene.id()).orElseThrow().revision()).isZero();
	}

	@Test
	void persistingANonZeroRevisionIsRejected() {
		SceneInstance hub = hubScene(UUID.randomUUID(), runA, minimalScene());
		SceneInstance revised = new SceneInstance(hub.id(), hub.runId(), hub.definitionCode(), hub.placement(),
				hub.discovered(), 3, hub.state());
		assertThatIllegalArgumentException().isThrownBy(() -> world.persistScene(revised));
	}

	@Test
	void updatesReplaceStateAndIncrementRevision() {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());

		SceneInstance first = world.updateSceneState(scene.id(), 0, richScene());
		assertThat(first.revision()).isEqualTo(1);
		assertThat(first.state()).isEqualTo(richScene());
		assertThat(world.findScene(scene.id())).contains(first);

		SceneInstance second = world.updateSceneState(scene.id(), 1, minimalScene());
		assertThat(second.revision()).isEqualTo(2);
		assertThat(world.findScene(scene.id()).orElseThrow().state()).isEqualTo(minimalScene());
	}

	@Test
	void staleExpectedRevisionFailsWithoutOverwriting() {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());
		world.updateSceneState(scene.id(), 0, richScene());

		assertThatThrownBy(() -> world.updateSceneState(scene.id(), 0, minimalScene()))
				.isInstanceOf(StaleSceneStateException.class)
				.hasMessageContaining(scene.id().toString());

		SceneInstance stored = world.findScene(scene.id()).orElseThrow();
		assertThat(stored.revision()).isEqualTo(1);
		assertThat(stored.state()).isEqualTo(richScene());
	}

	/**
	 * Two writers resolve against the same revision. The first holds its transaction open after
	 * flushing; the second then tries to update. Whether the second sees the stale revision on read
	 * or loses the versioned UPDATE at flush, it must fail with StaleSceneStateException, and only
	 * the first update may survive.
	 */
	@Test
	void concurrentUpdatesFromSameRevisionCannotBothCommit() throws Exception {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());
		TransactionTemplate tx = new TransactionTemplate(transactionManager);
		CountDownLatch firstFlushed = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<SceneInstance> first = executor.submit(() -> tx.execute(status -> {
				SceneInstance updated = world.updateSceneState(scene.id(), 0, richScene());
				firstFlushed.countDown();
				await(releaseFirst);
				return updated;
			}));
			assertThat(firstFlushed.await(30, TimeUnit.SECONDS)).isTrue();

			Future<SceneInstance> second = executor.submit(() -> world.updateSceneState(scene.id(), 0, minimalScene()));
			Thread.sleep(500); // let the second writer read revision 0 and block on the row lock
			releaseFirst.countDown();

			assertThat(first.get(30, TimeUnit.SECONDS).revision()).isEqualTo(1);
			assertThatThrownBy(() -> second.get(30, TimeUnit.SECONDS))
					.isInstanceOf(ExecutionException.class)
					.hasCauseInstanceOf(StaleSceneStateException.class);
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}

		SceneInstance stored = world.findScene(scene.id()).orElseThrow();
		assertThat(stored.revision()).isEqualTo(1);
		assertThat(stored.state()).isEqualTo(richScene());
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	@Test
	void updatingAnUnknownSceneIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> world.updateSceneState(UUID.randomUUID(), 0, minimalScene()));
	}

	// --- Same-run integrity ---

	@Test
	void sceneCannotUseAnotherRunsRegion() {
		RegionInstance regionOfA = persistedRegion(runA);
		SceneInstance sceneOfB = regionScene(UUID.randomUUID(), runB, regionOfA.id(), 1L, minimalScene());

		assertThatIllegalArgumentException().isThrownBy(() -> world.persistScene(sceneOfB));
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO scene_instance (id, run_id, scene_kind, region_id, definition_code, scene_seed, discovered,
				                            revision, state_schema_version, state)
				VALUES (?, ?, 'REGION', ?, 'CLOISTER', 1, false, 0, 1, '{}'::jsonb)
				""", UUID.randomUUID(), runB, regionOfA.id())).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsInvalidHubRegionShapes() {
		UUID region = persistedRegion(runA).id();
		String insert = """
				INSERT INTO scene_instance (id, run_id, scene_kind, region_id, definition_code, scene_seed, discovered,
				                            revision, state_schema_version, state)
				VALUES (?, ?, ?, ?, 'SOME_SCENE', ?, false, 0, 1, '{}'::jsonb)
				""";
		assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), runA, "HUB", region, null))
				.as("HUB with region").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), runA, "HUB", null, 5L))
				.as("HUB with seed").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), runA, "REGION", null, 5L))
				.as("REGION without region").isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), runA, "REGION", region, null))
				.as("REGION without seed").isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Player location ---

	@Test
	void runWithoutWorldStateHasNoLocation() {
		assertThat(world.findPlayerLocation(runA)).isEmpty();
	}

	@Test
	void playerLocationRoundTripsAndUpdates() {
		SceneInstance scene = persistedRegionScene(runA, richScene());

		world.setPlayerLocation(runA, new PlayerLocation(scene.id(), ENTRANCE));
		assertThat(world.findPlayerLocation(runA)).contains(new PlayerLocation(scene.id(), ENTRANCE));

		world.setPlayerLocation(runA, new PlayerLocation(scene.id(), ALTAR));
		assertThat(world.findPlayerLocation(runA)).contains(new PlayerLocation(scene.id(), ALTAR));
	}

	@Test
	void locationZoneMustExistInScene() {
		SceneInstance scene = persistedRegionScene(runA, richScene());
		assertThatIllegalArgumentException()
				.isThrownBy(() -> world.setPlayerLocation(runA, new PlayerLocation(scene.id(), "bell_tower")))
				.withMessageContaining("bell_tower");
	}

	@Test
	void locationCannotBeAHiddenZone() {
		SceneInstance scene = persistedRegionScene(runA, richScene());
		world.setPlayerLocation(runA, new PlayerLocation(scene.id(), ENTRANCE));

		assertThatIllegalArgumentException()
				.isThrownBy(() -> world.setPlayerLocation(runA, new PlayerLocation(scene.id(), CRYPT)))
				.withMessageContaining("hidden");
		assertThat(world.findPlayerLocation(runA)).contains(new PlayerLocation(scene.id(), ENTRANCE));
	}

	@Test
	void locationCannotPointAtAnotherRunsScene() {
		SceneInstance sceneOfA = persistedRegionScene(runA, richScene());

		assertThatIllegalArgumentException()
				.isThrownBy(() -> world.setPlayerLocation(runB, new PlayerLocation(sceneOfA.id(), ENTRANCE)));
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO run_world_state (run_id, current_scene_id, current_zone_id) VALUES (?, ?, ?)",
				runB, sceneOfA.id(), ENTRANCE)).isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- Corrupt stored state ---

	@Test
	void corruptStateDocumentFailsAsPersistedStateCorruption() {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());
		jdbc.update("UPDATE scene_instance SET state = '{\"zones\": 5}'::jsonb WHERE id = ?", scene.id());

		assertThatThrownBy(() -> world.findScene(scene.id()))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(scene.id().toString());
	}

	@Test
	void unsupportedStateSchemaVersionFailsClearly() {
		SceneInstance scene = persistedRegionScene(runA, minimalScene());
		jdbc.update("UPDATE scene_instance SET state_schema_version = 4 WHERE id = ?", scene.id());

		assertThatThrownBy(() -> world.findScene(scene.id()))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(scene.id().toString())
				.hasMessageContaining("unsupported scene-state schema version 4");
	}
}
