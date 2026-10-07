package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.character.GeneratedCharacter;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.run.GameRun;

/**
 * GameRunStore against real PostgreSQL. Every query is keyed by run ID or explicitly ordered, so no
 * test depends on database row order or on rows written by other tests.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class GameRunStoreIntegrationTest {

	private static final long RUN_SEED = 12345L;

	@Autowired
	private GameRunStore store;

	@Autowired
	private GameContentCatalog catalog;

	@Autowired
	private JdbcTemplate jdbc;

	private GeneratedCharacter character;

	@BeforeEach
	void generateDeterministicCharacter() {
		character = new PlayerCharacterGenerator(catalog, new PlayerStatGenerator()).generate(new SplittableRandom(42));
	}

	@Test
	void createdRunLoadsAsEquivalentState() {
		GameRun created = store.createRun(RUN_SEED, character);

		GameRun loaded = store.findRun(created.id()).orElseThrow();

		assertThat(loaded).isEqualTo(new GameRun(created.id(), RUN_SEED, PlayerCharacterState.from(character)));
		assertThat(loaded).isEqualTo(created);
	}

	@Test
	void rowsHoldStableNamesAndDefinitionCodes() {
		UUID runId = store.createRun(RUN_SEED, character).id();

		assertThat(jdbc.queryForObject("SELECT run_seed FROM game_run WHERE id = ?", Long.class, runId)).isEqualTo(RUN_SEED);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM player_character WHERE run_id = ?", runId);
		assertThat(row).containsEntry("name", character.name())
				.containsEntry("might", character.stats().might().value())
				.containsEntry("agility", character.stats().agility().value())
				.containsEntry("perception", character.stats().perception().value())
				.containsEntry("arcana", character.stats().arcana().value())
				.containsEntry("resolve", character.stats().resolve().value())
				.containsEntry("fated", character.fated().value())
				.containsEntry("max_hp", character.maxHp())
				.containsEntry("current_hp", character.currentHp())
				.containsEntry("passive_code", character.passive().code())
				.containsEntry("ability_code", character.ability().code());

		List<Map<String, Object>> body = jdbc.queryForList(
				"SELECT body_part, severity FROM player_body_part WHERE run_id = ?", runId);
		assertThat(body).hasSize(BodyPart.values().length).allSatisfy(part -> assertThat(part).containsEntry("severity", "HEALTHY"));
		assertThat(body).extracting(part -> part.get("body_part"))
				.containsExactlyInAnyOrder((Object[]) Arrays.stream(BodyPart.values()).map(Enum::name).toArray(String[]::new));

		List<Map<String, Object>> belt = jdbc.queryForList(
				"SELECT slot_index, entry_kind, definition_code FROM player_tool_belt_entry WHERE run_id = ? ORDER BY slot_index",
				runId);
		assertThat(belt).extracting(entry -> entry.get("slot_index")).containsExactly(0, 1, 2);
		assertThat(belt).extracting(entry -> entry.get("entry_kind")).containsExactly("WEAPON", "ITEM", "ITEM");
		assertThat(belt).extracting(entry -> entry.get("definition_code")).containsExactly(
				character.weapon().code(), character.recoveryItem().code(), character.utilityItem().code());
	}

	@Test
	void unknownRunIsAbsent() {
		assertThat(store.findRun(UUID.randomUUID())).isEmpty();
	}

	@Test
	void runWithoutPlayerCharacterIsCorruptNotAbsent() {
		UUID runId = UUID.randomUUID();
		jdbc.update("INSERT INTO game_run (id, run_seed) VALUES (?, ?)", runId, 1L);

		assertThatThrownBy(() -> store.findRun(runId))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining(runId.toString())
				.hasMessageContaining("no player character");
	}

	@Test
	void runCannotOwnASecondPlayerCharacter() {
		UUID runId = store.createRun(RUN_SEED, character).id();

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO player_character (run_id, name, might, agility, perception, arcana, resolve, fated,
				                              max_hp, current_hp, passive_code, ability_code)
				VALUES (?, 'Second', 6, 6, 5, 5, 5, 0, 25, 25, 'CLEAR_MIND', 'STONEBLOOD')
				""", runId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void runCreationIsAtomic() {
		Long runsBefore = jdbc.queryForObject("SELECT count(*) FROM game_run", Long.class);
		jdbc.execute("""
				CREATE FUNCTION reject_player_character() RETURNS trigger AS $$
				BEGIN RAISE EXCEPTION 'player_character insert rejected by test'; END;
				$$ LANGUAGE plpgsql
				""");
		jdbc.execute("""
				CREATE TRIGGER reject_player_character BEFORE INSERT ON player_character
				FOR EACH ROW EXECUTE FUNCTION reject_player_character()
				""");
		try {
			assertThatThrownBy(() -> store.createRun(RUN_SEED, character)).isInstanceOf(RuntimeException.class);
		} finally {
			jdbc.execute("DROP TRIGGER reject_player_character ON player_character");
			jdbc.execute("DROP FUNCTION reject_player_character()");
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM game_run", Long.class)).isEqualTo(runsBefore);
	}

	@Test
	void loadsNonStartingStateAsPlayerCharacterState() {
		UUID runId = store.createRun(RUN_SEED, character).id();
		jdbc.update("UPDATE player_character SET current_hp = 5 WHERE run_id = ?", runId);

		PlayerCharacterState loaded = store.findRun(runId).orElseThrow().playerCharacter();

		assertThat(loaded.currentHp()).isEqualTo(5);
		assertThat(loaded.maxHp()).isEqualTo(character.maxHp());
	}

	@Test
	void removedContentCodeFailsToLoadClearly() {
		UUID runId = store.createRun(RUN_SEED, character).id();
		jdbc.update("UPDATE player_character SET passive_code = 'REMOVED_PASSIVE' WHERE run_id = ?", runId);

		assertThatThrownBy(() -> store.findRun(runId))
				.isInstanceOf(PersistedStateException.class)
				.hasMessageContaining("REMOVED_PASSIVE");
	}

	@Test
	void databaseRejectsCurrentHpAboveMax() {
		UUID runId = store.createRun(RUN_SEED, character).id();

		assertThatThrownBy(() -> jdbc.update("UPDATE player_character SET current_hp = max_hp + 1 WHERE run_id = ?", runId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
