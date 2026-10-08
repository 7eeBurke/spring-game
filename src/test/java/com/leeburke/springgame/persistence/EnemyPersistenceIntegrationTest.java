package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
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
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.enemy.GeneratedEnemyRoster;
import com.leeburke.springgame.enemy.SceneEnemy;
import com.leeburke.springgame.run.initialization.RunInitialization;
import com.leeburke.springgame.run.initialization.RunInitializer;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.IdSource;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/** Enemy state is persisted with the world, before play, and loaded as stored. Real PostgreSQL. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class EnemyPersistenceIntegrationTest {

	@Autowired
	private WorldStore world;

	@Autowired
	private EnemyStore enemyStore;

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

	private RunInitialization initialization(UUID runId, long seed) {
		return new RunInitializer(new RunWorldGenerator(worldContent, IdSource.random()), new EnemyRosterGenerator(enemies))
				.initialize(runId, seed, GenerationContextSnapshot.empty());
	}

	private long enemyRows(UUID runId) {
		return jdbc.queryForObject("""
				SELECT count(*) FROM enemy_instance e JOIN scene_instance s ON s.id = e.scene_id WHERE s.run_id = ?
				""", Long.class, runId);
	}

	@Test
	void flywayAppliedV4() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '4' AND success",
				Long.class)).isEqualTo(1);
	}

	@Test
	void everyEnemyExistsBeforeAnySceneIsEnteredAndLoadsUnchanged() {
		UUID runId = newRun(201);
		RunInitialization init = initialization(runId, 201);
		world.initializeWorld(init);

		assertThat(init.enemies().enemies()).isNotEmpty();
		assertThat(enemyRows(runId)).isEqualTo(init.enemies().enemies().size());
		for (SceneInstance scene : init.world().region().scenes()) {
			List<EnemyInstance> stored = enemyStore.findEnemies(scene.id());
			assertThat(stored).extracting(EnemyInstance::entityId)
					.containsExactlyInAnyOrderElementsOf(scene.state().entities().stream().map(SceneEntity::id).toList());
			for (EnemyInstance enemy : stored) {
				assertThat(init.enemies().find(scene.id(), enemy.entityId())).contains(enemy);
				assertThat(enemyStore.findEnemy(scene.id(), enemy.entityId())).contains(enemy);
			}
			assertThat(stored).isSortedAccordingTo(java.util.Comparator.comparing(EnemyInstance::entityId));
		}
		assertThat(enemyStore.findEnemies(init.world().hub().id())).isEmpty();
	}

	@Test
	void objectsAndHazardsGetNoEnemyRows() {
		UUID runId = newRun(202);
		RunInitialization init = initialization(runId, 202);
		world.initializeWorld(init);

		for (SceneInstance scene : init.world().region().scenes()) {
			List<String> entityIds = scene.state().entities().stream().map(SceneEntity::id).toList();
			List<String> rows = jdbc.queryForList("SELECT entity_local_id FROM enemy_instance WHERE scene_id = ?",
					String.class, scene.id());
			assertThat(entityIds).containsAll(rows);
		}
	}

	@Test
	void valuesAreStoredByStableNames() {
		UUID runId = newRun(203);
		RunInitialization init = initialization(runId, 203);
		world.initializeWorld(init);
		SceneEnemy guardian = init.enemies().enemies().stream()
				.filter(e -> e.enemy().definitionCode().equals("CHAPEL_GUARDIAN")).findFirst().orElseThrow();

		assertThat(jdbc.queryForMap("""
				SELECT definition_code, might, agility, perception, arcana, resolve, max_hp, current_hp, weapon_code
				FROM enemy_instance WHERE scene_id = ? AND entity_local_id = ?
				""", guardian.sceneId(), guardian.enemy().entityId()))
				.containsEntry("definition_code", "CHAPEL_GUARDIAN").containsEntry("might", 10).containsEntry("agility", 9)
				.containsEntry("perception", 5).containsEntry("arcana", 4).containsEntry("resolve", 8)
				.containsEntry("max_hp", 42).containsEntry("current_hp", 42).containsEntry("weapon_code", "LONGSWORD");
		assertThat(jdbc.queryForList("SELECT DISTINCT severity FROM enemy_body_part WHERE scene_id = ?", String.class,
				guardian.sceneId())).containsExactly("HEALTHY");
		assertThat(jdbc.queryForList("SELECT body_part FROM enemy_body_part WHERE scene_id = ? AND entity_local_id = ?",
				String.class, guardian.sceneId(), guardian.enemy().entityId())).contains("HEAD", "HEART", "LEFT_LEG");
	}

	@Test
	void staticEnemyContentHasNoTables() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
		// pending_attack (Stage 14) is run state: the one attack a run is waiting to defend against.
		assertThat(tables).contains("enemy_instance", "enemy_body_part")
				.filteredOn(t -> !t.equals("pending_attack"))
				.noneMatch(t -> t.contains("definition") || t.contains("anatomy") || t.contains("trait")
						|| t.contains("attack") || t.contains("behavio"));
	}

	@Test
	void storedStateIsNeverRegenerated() {
		UUID runId = newRun(204);
		RunInitialization init = initialization(runId, 204);
		world.initializeWorld(init);
		SceneEnemy enemy = init.enemies().enemies().getFirst();

		jdbc.update("UPDATE enemy_instance SET current_hp = 1 WHERE scene_id = ? AND entity_local_id = ?",
				enemy.sceneId(), enemy.enemy().entityId());
		jdbc.update("UPDATE enemy_body_part SET severity = 'WOUNDED' WHERE scene_id = ? AND entity_local_id = ? AND body_part = 'HEAD'",
				enemy.sceneId(), enemy.enemy().entityId());

		EnemyInstance loaded = enemyStore.findEnemy(enemy.sceneId(), enemy.enemy().entityId()).orElseThrow();
		assertThat(loaded.currentHp()).isEqualTo(1);
		assertThat(loaded.body().severity(com.leeburke.springgame.mechanics.BodyPart.HEAD))
				.contains(com.leeburke.springgame.mechanics.BodySeverity.WOUNDED);
		assertThat(loaded.stats()).isEqualTo(enemy.enemy().stats());
	}

	@Test
	void corruptRowsFailClearly() {
		UUID runId = newRun(205);
		RunInitialization init = initialization(runId, 205);
		world.initializeWorld(init);
		List<SceneEnemy> stored = init.enemies().enemies();
		SceneEnemy first = stored.getFirst();
		SceneEnemy second = stored.get(1);

		jdbc.update("UPDATE enemy_instance SET definition_code = 'CRYPT_HOUND' WHERE scene_id = ? AND entity_local_id = ?",
				first.sceneId(), first.enemy().entityId());
		assertThatThrownBy(() -> enemyStore.findEnemy(first.sceneId(), first.enemy().entityId()))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(first.enemy().entityId());

		jdbc.update("DELETE FROM enemy_body_part WHERE scene_id = ? AND entity_local_id = ? AND body_part = 'HEART'",
				second.sceneId(), second.enemy().entityId());
		assertThatThrownBy(() -> enemyStore.findEnemy(second.sceneId(), second.enemy().entityId()))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("anatomy");
	}

	@Test
	void invalidRosterIsRejectedBeforeAnyWrite() {
		UUID runId = newRun(206);
		RunInitialization init = initialization(runId, 206);
		List<SceneEnemy> missingOne = init.enemies().enemies().subList(1, init.enemies().enemies().size());
		RunInitialization broken = new RunInitialization(init.world(), new GeneratedEnemyRoster(missingOne));

		assertThatIllegalArgumentException().isThrownBy(() -> world.initializeWorld(broken))
				.withMessageContaining("has no enemy state");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM scene_instance WHERE run_id = ?", Long.class, runId)).isZero();
		assertThat(world.findGenerationContext(runId)).isEmpty();
	}

	@Test
	void failureWritingEnemiesLeavesNoWorld() {
		UUID runId = newRun(207);
		RunInitialization init = initialization(runId, 207);
		jdbc.execute("""
				CREATE FUNCTION reject_enemy_body() RETURNS trigger AS $$
				BEGIN RAISE EXCEPTION 'enemy_body_part insert rejected by test'; END;
				$$ LANGUAGE plpgsql
				""");
		jdbc.execute("""
				CREATE TRIGGER reject_enemy_body BEFORE INSERT ON enemy_body_part
				FOR EACH ROW EXECUTE FUNCTION reject_enemy_body()
				""");
		try {
			assertThatThrownBy(() -> world.initializeWorld(init)).isInstanceOf(RuntimeException.class);
		} finally {
			jdbc.execute("DROP TRIGGER reject_enemy_body ON enemy_body_part");
			jdbc.execute("DROP FUNCTION reject_enemy_body()");
		}
		assertThat(enemyRows(runId)).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM scene_instance WHERE run_id = ?", Long.class, runId)).isZero();
		assertThat(world.findPlayerLocation(runId)).isEmpty();
	}
}
