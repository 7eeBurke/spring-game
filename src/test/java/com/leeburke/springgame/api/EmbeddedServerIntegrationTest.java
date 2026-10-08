package com.leeburke.springgame.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;

/**
 * Starts the real embedded server (MockMvc does not), so servlet-container wiring such as filter
 * registration is exercised exactly as in production.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestcontainersConfiguration.class)
class EmbeddedServerIntegrationTest {

	@LocalServerPort
	private int port;

	@Test
	void theServerStartsAndAnswersWithApiErrors() throws Exception {
		HttpClient client = HttpClient.newHttpClient();

		HttpResponse<String> unauthorized = client.send(HttpRequest.newBuilder(
				URI.create("http://localhost:" + port + "/api/v1/runs/" + UUID.randomUUID())).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> tooLarge = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/runs"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("x".repeat(9000))).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(unauthorized.statusCode()).isEqualTo(401);
		assertThat(unauthorized.body()).contains("\"UNAUTHORIZED\"");
		assertThat(tooLarge.statusCode()).isEqualTo(400);
		assertThat(tooLarge.body()).contains("\"INVALID_REQUEST\"");
	}
}
