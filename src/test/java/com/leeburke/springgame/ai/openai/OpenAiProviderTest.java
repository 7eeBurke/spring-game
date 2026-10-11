package com.leeburke.springgame.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiStructuredRequest;
import com.leeburke.springgame.ai.AiTextRequest;
import com.leeburke.springgame.ai.AiUsage;
import com.leeburke.springgame.ai.interpreter.ActionDocumentSchema;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.openai.core.JsonValue;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * The adapter's request mapping, response handling and error translation, against a local JDK
 * HTTP server only. No external network and no real key.
 */
class OpenAiProviderTest {

	private static final String KEY = "sk-test-secret-key";
	private static final AiGenerationSettings SETTINGS = new AiGenerationSettings("test-model", 321, Duration.ofSeconds(5),
			Optional.empty());

	private HttpServer server;
	private final List<String> requestBodies = new CopyOnWriteArrayList<>();
	private final List<String> authorizations = new CopyOnWriteArrayList<>();
	private volatile int status = 200;
	private volatile String body = "";
	private volatile long delayMillis;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
			try {
				Thread.sleep(delayMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			} catch (IOException ignored) {
				// the client may have given up (timeout test)
			}
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private OpenAiProvider provider() {
		return OpenAiProvider.create(KEY, Optional.of("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), 0,
				Duration.ofSeconds(5));
	}

	private static String response(String contentJson, String status) {
		return response(contentJson, status, "");
	}

	private static String response(String contentJson, String status, String usageJson) {
		return """
				{"id":"resp_1","object":"response","created_at":1,"model":"test-model","status":"%s",
				 "output":[{"type":"message","id":"msg_1","role":"assistant","status":"completed","content":[%s]}],
				 "parallel_tool_calls":false,"tool_choice":"auto","tools":[]%s}
				""".formatted(status, contentJson, usageJson);
	}

	private static final String USAGE = """
			,"usage":{"input_tokens":120,"input_tokens_details":{"cached_tokens":64},"output_tokens":45,
			 "output_tokens_details":{"reasoning_tokens":30},"total_tokens":165}""";

	private static String text(String text) {
		return "{\"type\":\"output_text\",\"text\":\"" + text + "\",\"annotations\":[]}";
	}

	private static AiTextRequest textRequest() {
		return new AiTextRequest(AiRole.OUTCOME_NARRATOR, 1, "SYSTEM RULES", "{\"outcome\":\"PLAYER TEXT\"}", SETTINGS);
	}

	private static AiStructuredRequest structuredRequest() {
		return new AiStructuredRequest(AiRole.ACTION_INTERPRETER, 1, "SYSTEM RULES", "{\"playerInput\":\"hit it\"}",
				ActionDocumentSchema.NAME, ActionDocumentSchema.json(), SETTINGS);
	}

	// --- Pure request mapping ---

	@Test
	void textParamsKeepInstructionsAndInputSeparate() {
		ResponseCreateParams params = OpenAiProvider.textParams(textRequest());
		assertThat(params.instructions()).contains("SYSTEM RULES");
		assertThat(params.input().orElseThrow().asText()).isEqualTo("{\"outcome\":\"PLAYER TEXT\"}");
		assertThat(params.store()).contains(false);
		assertThat(params.maxOutputTokens()).contains(321L);
		assertThat(params.model().orElseThrow().toString()).contains("test-model");
		assertThat(params.temperature()).isEmpty();
		assertThat(params.text()).isEmpty();
	}

	@Test
	void temperatureIsSentOnlyWhenConfigured() {
		AiGenerationSettings warm = new AiGenerationSettings("m", 10, Duration.ofSeconds(1), Optional.of(0.7));
		assertThat(OpenAiProvider.textParams(new AiTextRequest(AiRole.OUTCOME_NARRATOR, 1, "I", "{}", warm)).temperature())
				.contains(0.7);
	}

	@Test
	void reasoningEffortIsSentOnlyWhenConfigured() {
		assertThat(OpenAiProvider.textParams(textRequest()).reasoning()).isEmpty();
		AiGenerationSettings fast = new AiGenerationSettings("gpt-5.4-mini", 400, Duration.ofSeconds(1), Optional.empty(),
				Optional.of("none"));
		ResponseCreateParams params = OpenAiProvider.textParams(new AiTextRequest(AiRole.OUTCOME_NARRATOR, 6, "I", "{}", fast));
		assertThat(params.reasoning().orElseThrow().effort().orElseThrow().toString()).isEqualTo("none");
		assertThat(params.temperature()).isEmpty();
	}

	@Test
	void structuredParamsUseAStrictJsonSchema() {
		ResponseFormatTextJsonSchemaConfig format = OpenAiProvider.structuredParams(structuredRequest()).text().orElseThrow()
				.format().orElseThrow().jsonSchema().orElseThrow();
		assertThat(format.name()).isEqualTo(ActionDocumentSchema.NAME);
		assertThat(format.strict()).contains(true);
		assertThat(format.schema()._additionalProperties()).containsEntry("type", JsonValue.from("object"))
				.containsEntry("additionalProperties", JsonValue.from(false))
				.containsKeys("properties", "required");
	}

	// --- Contract against a local server ---

	@Test
	void textResponseIsReturnedAndTheRequestIsShapedCorrectly() {
		body = response(text("The blade bites."), "completed");
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(textRequest())).isInstanceOfSatisfying(AiResponse.Success.class,
					success -> assertThat(success.text()).isEqualTo("The blade bites."));
		}
		String request = requestBodies.getFirst();
		assertThat(request).contains("\"instructions\":\"SYSTEM RULES\"", "\"store\":false", "\"model\":\"test-model\"",
				"\"max_output_tokens\":321");
		assertThat(authorizations.getFirst()).isEqualTo("Bearer " + KEY);
	}

	@Test
	void structuredRequestSendsTheStrictSchema() {
		body = response(text("{}"), "completed");
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateStructured(structuredRequest())).isInstanceOf(AiResponse.Success.class);
		}
		assertThat(requestBodies.getFirst()).contains("\"type\":\"json_schema\"", "\"strict\":true",
				"\"name\":\"" + ActionDocumentSchema.NAME + "\"");
	}

	@Test
	void reportedUsageIsReturnedWithCachedAndReasoningTokens() {
		body = response(text("ok"), "completed", USAGE);
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(textRequest())).isInstanceOfSatisfying(AiResponse.Success.class,
					success -> assertThat(success.usage()).contains(new AiUsage(120, 45, Optional.of(64L), Optional.of(30L))));
		}
	}

	@Test
	void missingUsageIsEmpty() {
		body = response(text("ok"), "completed");
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(textRequest())).isInstanceOfSatisfying(AiResponse.Success.class,
					success -> assertThat(success.usage()).isEmpty());
		}
	}

	@Test
	void usageWithoutDetailsHasOnlyTheTotals() {
		body = response(text("ok"), "completed", ",\"usage\":{\"input_tokens\":10,\"output_tokens\":5,\"total_tokens\":15}");
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(textRequest())).isInstanceOfSatisfying(AiResponse.Success.class,
					success -> assertThat(success.usage()).contains(new AiUsage(10, 5, Optional.empty(), Optional.empty())));
		}
	}

	@Test
	void refusalIncompleteAndEmptyOutputAreFailures() {
		try (OpenAiProvider provider = provider()) {
			body = response("{\"type\":\"refusal\",\"refusal\":\"I won't.\"}", "completed");
			assertThat(provider.generateText(textRequest())).isEqualTo(AiResponse.Failure.of(AiFailureKind.REFUSED));
			body = response(text("cut o"), "incomplete");
			assertThat(provider.generateText(textRequest())).isEqualTo(AiResponse.Failure.of(AiFailureKind.TRUNCATED));
			body = response("", "completed");
			assertThat(provider.generateText(textRequest())).isEqualTo(AiResponse.Failure.of(AiFailureKind.MALFORMED_RESPONSE));
		}
	}

	@Test
	void httpErrorsAreTranslatedWithoutBodiesOrKeys() {
		String errorBody = "{\"error\":{\"message\":\"secret body text " + KEY + "\",\"type\":\"x\"}}";
		try (OpenAiProvider provider = provider()) {
			for (int[] expectation : new int[][] { { 401 }, { 403 }, { 429 }, { 500 }, { 400 } }) {
				status = expectation[0];
				body = errorBody;
				AiResponse response = provider.generateText(textRequest());
				AiFailureKind expected = switch (status) {
					case 401, 403 -> AiFailureKind.AUTHENTICATION;
					case 429 -> AiFailureKind.RATE_LIMITED;
					default -> AiFailureKind.PROVIDER_ERROR;
				};
				assertThat(response).isEqualTo(new AiResponse.Failure(expected, "HTTP " + status));
				assertThat(response.toString()).doesNotContain(KEY, "secret body text");
			}
		}
	}

	@Test
	void slowResponseIsATimeout() {
		body = response(text("late"), "completed");
		delayMillis = 1500;
		AiGenerationSettings impatient = new AiGenerationSettings("test-model", 10, Duration.ofMillis(200), Optional.empty());
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(new AiTextRequest(AiRole.OUTCOME_NARRATOR, 1, "I", "{}", impatient)))
					.isEqualTo(AiResponse.Failure.of(AiFailureKind.TIMEOUT));
		}
	}

	@Test
	void missingModelIsNotConfiguredWithoutACall() {
		AiGenerationSettings none = new AiGenerationSettings("", 10, Duration.ofSeconds(1), Optional.empty());
		try (OpenAiProvider provider = provider()) {
			assertThat(provider.generateText(new AiTextRequest(AiRole.OUTCOME_NARRATOR, 1, "I", "{}", none)))
					.isEqualTo(AiResponse.Failure.of(AiFailureKind.NOT_CONFIGURED));
		}
		assertThat(requestBodies).isEmpty();
	}

	@Test
	void logsNeverContainTheKeyPlayerTextOrModelOutput() {
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		root.addAppender(appender);
		try (OpenAiProvider provider = provider()) {
			status = 200;
			body = response(text("MODEL OUTPUT SENTINEL"), "completed", USAGE);
			new ActionInterpreter(provider, "SYSTEM RULES", 1, SETTINGS).interpret("PLAYER SENTINEL TEXT",
					InterpreterFixtures.setup());
		} finally {
			root.detachAppender(appender);
		}
		assertThat(appender.list).isNotEmpty();
		assertThat(appender.list).anySatisfy(event -> assertThat(event.getFormattedMessage())
				.contains("inputTokens=240", "cachedInputTokens=128", "outputTokens=90", "reasoningTokens=60"));
		for (ILoggingEvent event : appender.list) {
			assertThat(event.getFormattedMessage()).doesNotContain(KEY, "PLAYER SENTINEL TEXT", "MODEL OUTPUT SENTINEL",
					"SYSTEM RULES");
		}
	}
}
