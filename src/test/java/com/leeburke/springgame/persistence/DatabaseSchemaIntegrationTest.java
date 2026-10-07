package com.leeburke.springgame.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;

/**
 * Flyway migrates a fresh PostgreSQL container; the context starting proves Hibernate validated the
 * schema. Assertions are about what Stage 7 created, so later migrations do not break this test.
 */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class DatabaseSchemaIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	private List<String> publicTables() {
		return jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
	}

	@Test
	void migrationV1Succeeded() {
		Boolean success = jdbc.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);
		assertThat(success).isTrue();
	}

	@Test
	void stage7TablesExist() {
		assertThat(publicTables()).contains(
				"flyway_schema_history", "game_run", "player_character", "player_body_part", "player_tool_belt_entry");
	}

	@ParameterizedTest
	@ValueSource(strings = { "weapon", "passive", "abilit", "item", "character_name", "content" })
	void noStaticContentDefinitionTables(String contentTypeFragment) {
		assertThat(publicTables())
				.filteredOn(table -> !table.equals("player_tool_belt_entry"))
				.noneMatch(table -> table.contains(contentTypeFragment));
	}
}
