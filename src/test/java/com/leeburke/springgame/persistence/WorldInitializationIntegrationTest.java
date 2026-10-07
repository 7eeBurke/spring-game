package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.run.initialization.RunInitialization;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.SceneKind;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.generation.GeneratedRegion;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.IdSource;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/** Whole-world initialization against real PostgreSQL. Each test creates its own run. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class WorldInitializationIntegrationTest {

	private static final List<String> WORLD_TABLES =
			List.of("run_generation_context", "region_instance", "scene_instance", "run_world_state");

	@Autowired
	private WorldStore world;

	@Autowired
	private GameRunStore runs;

	@Autowired
	private GameContentCatalog content;

	@Autowired
	private WorldContentCatalog worldContent;

	@Autowired
	private EnemyCatalog enemies;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID newRun(long seed) {
		return runs.createRun(seed, new PlayerCharacterGenerator(content, new PlayerStatGenerator())
				.generate(new SplittableRandom(seed))).id();
	}

	private GeneratedRunWorld generate(UUID runId, long seed, GenerationContextSnapshot context) {
		return new RunWorldGenerator(worldContent, IdSource.random()).generate(runId, seed, context);
	}

	private RunInitialization withEnemies(GeneratedRunWorld generated) {
		return new RunInitialization(generated, new EnemyRosterGenerator(enemies).generate(generated));
	}

	private long enemyRows(GeneratedRunWorld generated) {
		List<UUID> sceneIds = generated.region().scenes().stream().map(SceneInstance::id).toList();
		return jdbc.queryForObject("SELECT count(*) FROM enemy_instance WHERE scene_id = ANY (?)", Long.class,
				(Object) sceneIds.toArray(UUID[]::new));
	}

	private long rows(String table, UUID runId) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE run_id = ?", Long.class, runId);
	}

	private void assertNoWorldRows(UUID runId) {
		for (String table : WORLD_TABLES) {
			assertThat(rows(table, runId)).as(table).isZero();
		}
	}

	@Test
	void completeWorldPersistsAndLoadsUnchanged() {
		UUID runId = newRun(101);
		GenerationContextSnapshot context = new GenerationContextSnapshot(List.of("CLOISTER", "SACRISTY"));
		GeneratedRunWorld generated = generate(runId, 101, context);

		world.initializeWorld(withEnemies(generated));

		assertThat(world.findScene(generated.hub().id())).contains(generated.hub());
		for (SceneInstance scene : generated.region().scenes()) {
			assertThat(world.findScene(scene.id())).contains(scene);
		}
		assertThat(world.findRegion(generated.region().region().id())).contains(generated.region().region());
		assertThat(rows("scene_instance", runId)).isEqualTo(generated.region().scenes().size() + 1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM scene_instance WHERE run_id = ? AND revision <> 0",
				Long.class, runId)).isZero();
		assertThat(world.findGenerationContext(runId)).contains(context);
	}

	@Test
	void hubPersistsAsHubAndRegionScenesAsRegionScenes() {
		UUID runId = newRun(102);
		GeneratedRunWorld generated = generate(runId, 102, GenerationContextSnapshot.empty());
		world.initializeWorld(withEnemies(generated));

		Map<String, Object> hub = jdbc.queryForMap(
				"SELECT scene_kind, region_id, scene_seed, definition_code FROM scene_instance WHERE id = ?", generated.hub().id());
		assertThat(hub).containsEntry("scene_kind", "HUB").containsEntry("definition_code", "THE_LAST_LANTERN");
		assertThat(hub.get("region_id")).isNull();
		assertThat(hub.get("scene_seed")).isNull();

		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM scene_instance
				WHERE run_id = ? AND scene_kind = 'REGION' AND region_id = ? AND scene_seed IS NOT NULL
				""", Long.class, runId, generated.region().region().id())).isEqualTo(generated.region().scenes().size());
	}

	@Test
	void playerStartsInTheHubWhoseStoredExitLeadsToTheEntry() {
		UUID runId = newRun(103);
		GeneratedRunWorld generated = generate(runId, 103, GenerationContextSnapshot.empty());
		world.initializeWorld(withEnemies(generated));

		assertThat(world.findPlayerLocation(runId)).contains(new PlayerLocation(generated.hub().id(), "lantern_hearth"));
		SceneInstance storedHub = world.findScene(generated.hub().id()).orElseThrow();
		assertThat(storedHub.kind()).isEqualTo(SceneKind.HUB);
		assertThat(storedHub.state().exits()).extracting(SceneExit::destinationSceneId)
				.containsExactly(generated.region().entrySceneId());
	}

	@Test
	void aRunCannotBeInitializedTwice() {
		UUID runId = newRun(104);
		world.initializeWorld(withEnemies(generate(runId, 104, GenerationContextSnapshot.empty())));
		long scenesBefore = rows("scene_instance", runId);

		assertThatThrownBy(() -> world.initializeWorld(withEnemies(generate(runId, 999, GenerationContextSnapshot.empty()))))
				.isInstanceOf(WorldAlreadyInitializedException.class);
		assertThat(rows("scene_instance", runId)).isEqualTo(scenesBefore);
		assertThat(rows("run_generation_context", runId)).isEqualTo(1);
	}

	@Test
	void failureAfterEarlierInsertsLeavesNoPartialWorld() {
		UUID runId = newRun(105);
		GeneratedRunWorld generated = generate(runId, 105, GenerationContextSnapshot.empty());
		jdbc.execute("""
				CREATE FUNCTION reject_world_state() RETURNS trigger AS $$
				BEGIN RAISE EXCEPTION 'run_world_state insert rejected by test'; END;
				$$ LANGUAGE plpgsql
				""");
		jdbc.execute("""
				CREATE TRIGGER reject_world_state BEFORE INSERT ON run_world_state
				FOR EACH ROW EXECUTE FUNCTION reject_world_state()
				""");
		try {
			assertThatThrownBy(() -> world.initializeWorld(withEnemies(generated))).isInstanceOf(RuntimeException.class);
		} finally {
			jdbc.execute("DROP TRIGGER reject_world_state ON run_world_state");
			jdbc.execute("DROP FUNCTION reject_world_state()");
		}
		assertNoWorldRows(runId);
		assertThat(enemyRows(generated)).isZero();
	}

	@Test
	void handBuiltInvalidWorldIsRejectedBeforeAnyWrite() {
		UUID runId = newRun(106);
		GeneratedRunWorld generated = generate(runId, 106, GenerationContextSnapshot.empty());
		GeneratedRegion region = generated.region();
		SceneInstance boss = region.scene(region.bossSceneId()).orElseThrow();
		SceneState s = boss.state();
		SceneState noGuardian = new SceneState(s.zones(), s.connections(), List.of(), s.objects(), s.hazards(), s.exits(),
				s.activeEvents(), s.environmentFlags(), s.hiddenContent(), s.discoveredFacts());
		SceneInstance brokenBoss = new SceneInstance(boss.id(), boss.runId(), boss.definitionCode(), boss.placement(),
				boss.discovered(), 0, noGuardian);
		List<SceneInstance> scenes = region.scenes().stream().map(sc -> sc.id().equals(boss.id()) ? brokenBoss : sc).toList();
		GeneratedRunWorld broken = new GeneratedRunWorld(runId, generated.context(), generated.hub(),
				new GeneratedRegion(region.region(), region.routeStages(), region.optionalSceneIds(), region.bossSceneId(), scenes),
				generated.start());

		assertThatIllegalArgumentException().isThrownBy(() -> world.initializeWorld(withEnemies(broken)))
				.withMessageContaining("CHAPEL_GUARDIAN");
		assertNoWorldRows(runId);
	}

	@Test
	void corruptOrUnsupportedContextFailsClearly() {
		UUID runId = newRun(107);
		world.initializeWorld(withEnemies(generate(runId, 107, GenerationContextSnapshot.empty())));

		jdbc.update("UPDATE run_generation_context SET schema_version = 2 WHERE run_id = ?", runId);
		assertThatThrownBy(() -> world.findGenerationContext(runId))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("unsupported generation-context schema version 2");

		jdbc.update("UPDATE run_generation_context SET schema_version = 1, snapshot = '{\"recentOpeningArchetypeCodes\": 5}'::jsonb WHERE run_id = ?", runId);
		assertThatThrownBy(() -> world.findGenerationContext(runId))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(runId.toString());
	}

	@Test
	void runWithoutWorldHasNoContext() {
		assertThat(world.findGenerationContext(newRun(108))).isEmpty();
	}
}
