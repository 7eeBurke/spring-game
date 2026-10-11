package com.leeburke.springgame.ai.openai;

import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiStructuredRequest;
import com.leeburke.springgame.ai.AiTextRequest;
import com.leeburke.springgame.ai.AiUsage;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.RequestOptions;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseTextConfig;

/**
 * The OpenAI adapter: the only class that imports the vendor SDK. Uses the Responses API with the
 * role prompt as {@code instructions} and the JSON input as the single user message, never
 * concatenated. Structured calls use a strict JSON-schema text format. Responses are not stored
 * by the provider ({@code store=false}).
 * <p>
 * Every provider-side problem becomes an {@link AiResponse.Failure} carrying only a failure kind and
 * an HTTP status; keys, headers and bodies never appear in results or logs.
 */
public final class OpenAiProvider implements AiProvider, AutoCloseable {

	private static final ObjectMapper SCHEMA_READER = new ObjectMapper();

	private final OpenAIClient client;

	public OpenAiProvider(OpenAIClient client) {
		this.client = Objects.requireNonNull(client, "client");
	}

	/** Builds the SDK client. The key is passed to the SDK only and never logged. */
	public static OpenAiProvider create(String apiKey, Optional<String> baseUrl, int maxRetries, Duration timeout) {
		OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
				.apiKey(Objects.requireNonNull(apiKey, "apiKey"))
				.maxRetries(maxRetries)
				.timeout(timeout);
		baseUrl.ifPresent(builder::baseUrl);
		return new OpenAiProvider(builder.build());
	}

	@Override
	public AiResponse generateStructured(AiStructuredRequest request) {
		return call(structuredParams(request), request.settings());
	}

	@Override
	public AiResponse generateText(AiTextRequest request) {
		return call(textParams(request), request.settings());
	}

	@Override
	public void close() {
		client.close();
	}

	static ResponseCreateParams textParams(AiTextRequest request) {
		return base(request.settings(), request.instructions(), request.inputJson()).build();
	}

	static ResponseCreateParams structuredParams(AiStructuredRequest request) {
		ResponseFormatTextJsonSchemaConfig format = ResponseFormatTextJsonSchemaConfig.builder()
				.name(request.schemaName())
				.schema(schema(request.schemaJson()))
				.strict(true)
				.build();
		return base(request.settings(), request.instructions(), request.inputJson())
				.text(ResponseTextConfig.builder().format(format).build())
				.build();
	}

	private static ResponseCreateParams.Builder base(AiGenerationSettings settings, String instructions, String inputJson) {
		ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
				.model(settings.model())
				.instructions(instructions)
				.input(inputJson)
				.maxOutputTokens(settings.maxOutputTokens())
				.store(false);
		settings.temperature().ifPresent(builder::temperature);
		settings.reasoningEffort().ifPresent(effort -> builder.reasoning(
				com.openai.models.Reasoning.builder().effort(com.openai.models.ReasoningEffort.of(effort)).build()));
		return builder;
	}

	private static ResponseFormatTextJsonSchemaConfig.Schema schema(String schemaJson) {
		Map<String, Object> schema;
		try {
			schema = SCHEMA_READER.readValue(schemaJson, new TypeReference<>() {
			});
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Structured-output schema is not valid JSON", e);
		}
		ResponseFormatTextJsonSchemaConfig.Schema.Builder builder = ResponseFormatTextJsonSchemaConfig.Schema.builder();
		schema.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
		return builder.build();
	}

	private AiResponse call(ResponseCreateParams params, AiGenerationSettings settings) {
		if (settings.model().isBlank()) {
			return AiResponse.Failure.of(AiFailureKind.NOT_CONFIGURED);
		}
		long start = System.nanoTime();
		try {
			Response response = client.responses().create(params, RequestOptions.builder().timeout(settings.timeout()).build());
			return interpret(response, Duration.ofNanos(System.nanoTime() - start));
		} catch (UnauthorizedException | PermissionDeniedException e) {
			return failure(AiFailureKind.AUTHENTICATION, e);
		} catch (RateLimitException e) {
			return failure(AiFailureKind.RATE_LIMITED, e);
		} catch (OpenAIServiceException e) {
			return failure(AiFailureKind.PROVIDER_ERROR, e);
		} catch (OpenAIIoException e) {
			return AiResponse.Failure.of(isTimeout(e) ? AiFailureKind.TIMEOUT : AiFailureKind.NETWORK);
		} catch (OpenAIInvalidDataException e) {
			return AiResponse.Failure.of(AiFailureKind.MALFORMED_RESPONSE);
		} catch (OpenAIException e) {
			return AiResponse.Failure.of(AiFailureKind.PROVIDER_ERROR);
		}
	}

	/** Collects the output text; a refusal, an incomplete response or no text is a failure. */
	static AiResponse interpret(Response response, Duration latency) {
		if (response.status().filter(ResponseStatus.INCOMPLETE::equals).isPresent()) {
			return AiResponse.Failure.of(AiFailureKind.TRUNCATED);
		}
		StringBuilder text = new StringBuilder();
		for (ResponseOutputItem item : response.output()) {
			Optional<ResponseOutputMessage> message = item.message();
			if (message.isEmpty()) {
				continue;
			}
			for (ResponseOutputMessage.Content content : message.get().content()) {
				if (content.refusal().isPresent()) {
					return AiResponse.Failure.of(AiFailureKind.REFUSED);
				}
				content.outputText().ifPresent(output -> text.append(output.text()));
			}
		}
		if (text.isEmpty()) {
			return AiResponse.Failure.of(AiFailureKind.MALFORMED_RESPONSE);
		}
		return new AiResponse.Success(text.toString(), latency, usage(response));
	}

	/** Token counts from the response; optional details are omitted when absent or malformed. */
	static Optional<AiUsage> usage(Response response) {
		try {
			return response.usage().map(usage -> new AiUsage(usage.inputTokens(), usage.outputTokens(),
					detail(() -> usage.inputTokensDetails().cachedTokens()),
					detail(() -> usage.outputTokensDetails().reasoningTokens())));
		} catch (OpenAIInvalidDataException e) {
			return Optional.empty();
		}
	}

	private static Optional<Long> detail(java.util.function.LongSupplier value) {
		try {
			return Optional.of(value.getAsLong());
		} catch (OpenAIInvalidDataException e) {
			return Optional.empty();
		}
	}

	private static AiResponse failure(AiFailureKind kind, OpenAIServiceException e) {
		return new AiResponse.Failure(kind, "HTTP " + e.statusCode());
	}

	private static boolean isTimeout(Throwable e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof InterruptedIOException) {
				return true;
			}
		}
		return false;
	}
}
