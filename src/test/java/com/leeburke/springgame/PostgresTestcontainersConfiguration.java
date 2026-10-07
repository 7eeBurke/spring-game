package com.leeburke.springgame;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL for integration tests, matching the image in compose.yaml.
 * <p>
 * Spring manages the container's lifecycle and {@link ServiceConnection} points the datasource at
 * it. Test classes that import this configuration share one cached application context, and
 * therefore one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfiguration {

	public static final String POSTGRES_IMAGE = "postgres:18.4";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}
}
