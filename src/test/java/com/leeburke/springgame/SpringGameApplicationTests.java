package com.leeburke.springgame;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** The full application context starts against a Flyway-migrated, Hibernate-validated PostgreSQL schema. */
@SpringBootTest
@Import(PostgresTestcontainersConfiguration.class)
class SpringGameApplicationTests {

	@Test
	void contextLoads() {
	}

}
