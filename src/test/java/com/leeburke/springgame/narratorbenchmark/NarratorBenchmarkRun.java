package com.leeburke.springgame.narratorbenchmark;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiTextRequest;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.narration.NarratorBenchmarkAccess;
import com.leeburke.springgame.ai.openai.OpenAiBenchmarkAccess;
import com.leeburke.springgame.narratorbenchmark.BenchmarkScenarios.Scenario;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.RequestOptions;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

/**
 * The narrator benchmark: 3 models × 2 prompts × 8 scenarios, written to
 * {@code benchmarks/narrator/<timestamp>/}. Opt-in twice over: tagged {@code ai-smoke} (excluded from
 * the normal build) and enabled only with {@code -Dnarrator.benchmark=true}. It reads
 * {@code OPENAI_API_KEY} from the environment and passes it to the SDK only: never printed, logged,
 * written or put in a URL. With {@code -Dnarrator.benchmark.dryRun=true} it calls no model and writes
 * the report with placeholders.
 * <p>
 * Run (PowerShell, with the key set in your own shell):
 * {@code .\mvnw.cmd test "-Dgroups=ai-smoke" "-DexcludedGroups=none" "-Dtest=NarratorBenchmarkRun" "-Dnarrator.benchmark=true"}
 * <p>
 * Isolation: no Spring context, no database, no turns; production prompts, models and settings are
 * read, never changed. Each request is the production narrator request ({@code OpenAiProvider.textParams})
 * with the production limits (400 output tokens, store=false); for reasoning models, the lowest
 * reasoning effort the model accepts is added (none, then minimal, then low). Each response goes
 * through the production extraction and the narrator's acceptance rule.
 */
@Tag("ai-smoke")
@EnabledIfSystemProperty(named = "narrator.benchmark", matches = "true")
class NarratorBenchmarkRun {

	private static final List<String> DEFAULT_MODELS = List.of("gpt-4.1-mini", "gpt-5.4-mini", "gpt-5.4");
	private static final List<String> EFFORTS = List.of("none", "minimal", "low");
	private static final int MAX_OUTPUT_TOKENS = 400;
	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	@Test
	void run() throws IOException {
		boolean dryRun = Boolean.getBoolean("narrator.benchmark.dryRun");
		String key = System.getenv("OPENAI_API_KEY");
		if (!dryRun) {
			Assumptions.assumeTrue(key != null && !key.isBlank(),
					"OPENAI_API_KEY is not set in this environment: no model was called. Set it in your own shell and run again.");
		}
		List<String> models = Optional.ofNullable(System.getProperty("narrator.benchmark.models"))
				.map(m -> Arrays.stream(m.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList()).orElse(DEFAULT_MODELS);
		PromptLibrary prompts = new PromptLibrary();
		String promptA = prompts.instructions(AiRole.OUTCOME_NARRATOR);
		String promptB = promptA + addendum();
		int version = prompts.version(AiRole.OUTCOME_NARRATOR);
		List<Scenario> scenarios = BenchmarkScenarios.build();
		List<BenchmarkResult> results = new ArrayList<>();
		List<String> compatibility = new ArrayList<>();

		OpenAIClient client = dryRun ? null : OpenAIOkHttpClient.builder().apiKey(key).maxRetries(1).timeout(TIMEOUT).build();
		try {
			for (String model : models) {
				// One compatibility request first: scenario 4, prompt A. It settles the reasoning effort the model takes.
				Optional<String> effort = Optional.empty();
				boolean usable = true;
				if (!dryRun) {
					Probe probe = compatibility(client, model, promptA, version, scenarios.get(3));
					compatibility.add(probe.note());
					effort = probe.effort();
					usable = probe.usable();
				} else {
					compatibility.add(model + ": dry run, not called");
				}
				for (Scenario scenario : scenarios) {
					for (String variant : List.of("A", "B")) {
						String instructions = variant.equals("A") ? promptA : promptB;
						if (NarratorBenchmarkAccess.toldDirectly(scenario.context())) {
							// The game tells an unchanged look itself: no model is called, as in production.
							String text = NarratorBenchmarkAccess.fallback(scenario.context());
							results.add(new BenchmarkResult(scenario.number(), model, variant, "DIRECT (no model call)", text, true,
									Optional.empty(), 0, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
									BenchmarkChecks.words(text), List.of()));
							continue;
						}
						results.add(dryRun || !usable ? placeholder(scenario, model, variant, dryRun ? "DRY_RUN" : "MODEL_UNAVAILABLE")
								: call(client, model, variant, instructions, version, scenario, effort));
					}
				}
			}
		} finally {
			if (client != null) {
				client.close();
			}
		}
		Path dir = Path.of(System.getProperty("narrator.benchmark.out", "benchmarks/narrator"))
				.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + (dryRun ? "-dry-run" : ""));
		BenchmarkReport.write(dir, scenarios, results, compatibility, dryRun);
		System.out.println("Narrator benchmark written to " + dir.toAbsolutePath());
	}

	private record Probe(boolean usable, Optional<String> effort, String note) {
	}

	/** The first request to a model: which reasoning effort it accepts, and whether it answers at all. */
	private static Probe compatibility(OpenAIClient client, String model, String instructions, int version, Scenario scenario) {
		List<Optional<String>> tries = reasoning(model) ? EFFORTS.stream().map(Optional::of).toList() : List.of(Optional.empty());
		List<String> rejected = new ArrayList<>();
		for (Optional<String> effort : tries) {
			Attempt attempt = attempt(client, model, instructions, version, scenario, effort);
			if (attempt.error().isEmpty()) {
				String outcome = attempt.response().map(r -> r instanceof AiResponse.Success ? "answered"
						: "answered, but the production extraction rejected it: " + ((AiResponse.Failure) r).kind()).orElse("no response");
				return new Probe(true, effort, model + ": " + outcome + effort.map(e -> " with reasoning effort `" + e + "`").orElse("")
						+ (rejected.isEmpty() ? "" : " (rejected first: " + String.join("; ", rejected) + ")"));
			}
			rejected.add(effort.map(e -> "effort `" + e + "` → ").orElse("") + attempt.error().get());
		}
		return new Probe(false, Optional.empty(), model + ": not usable here: " + String.join("; ", rejected));
	}

	private static boolean reasoning(String model) {
		return model.startsWith("gpt-5") || model.startsWith("o");
	}

	private record Attempt(Optional<AiResponse> response, Optional<String> error, long latencyMs) {
	}

	private static Attempt attempt(OpenAIClient client, String model, String instructions, int version, Scenario scenario,
			Optional<String> effort) {
		AiTextRequest request = new AiTextRequest(AiRole.OUTCOME_NARRATOR, version, instructions,
				NarratorBenchmarkAccess.requestJson(scenario.context()),
				new AiGenerationSettings(model, MAX_OUTPUT_TOKENS, TIMEOUT, Optional.empty()));
		ResponseCreateParams params = OpenAiBenchmarkAccess.textParams(request);
		if (effort.isPresent()) {
			params = params.toBuilder().reasoning(Reasoning.builder().effort(ReasoningEffort.of(effort.get())).build()).build();
		}
		long start = System.nanoTime();
		try {
			Response response = client.responses().create(params, RequestOptions.builder().timeout(TIMEOUT).build());
			Duration latency = Duration.ofNanos(System.nanoTime() - start);
			return new Attempt(Optional.of(OpenAiBenchmarkAccess.interpret(response, latency)), Optional.empty(), latency.toMillis());
		} catch (OpenAIServiceException e) {
			return new Attempt(Optional.empty(), Optional.of("HTTP " + e.statusCode() + ": " + safe(e.getMessage())),
					Duration.ofNanos(System.nanoTime() - start).toMillis());
		} catch (OpenAIException e) {
			return new Attempt(Optional.empty(), Optional.of(e.getClass().getSimpleName()), Duration.ofNanos(System.nanoTime() - start).toMillis());
		}
	}

	private static BenchmarkResult call(OpenAIClient client, String model, String variant, String instructions, int version,
			Scenario scenario, Optional<String> effort) {
		Attempt attempt = attempt(client, model, instructions, version, scenario, effort);
		if (attempt.error().isPresent()) {
			return new BenchmarkResult(scenario.number(), model, variant, attempt.error().get(), "", false,
					Optional.of(NarratorBenchmarkAccess.fallback(scenario.context())), attempt.latencyMs(), Optional.empty(), Optional.empty(),
					Optional.empty(), effort, 0, List.of());
		}
		AiResponse response = attempt.response().orElseThrow();
		if (response instanceof AiResponse.Success success) {
			String text = success.text().strip();
			boolean accepted = NarratorBenchmarkAccess.accepted(text);
			return new BenchmarkResult(scenario.number(), model, variant, "SUCCESS", text, accepted,
					accepted ? Optional.empty() : Optional.of(NarratorBenchmarkAccess.fallback(scenario.context())), attempt.latencyMs(),
					success.usage().map(u -> u.inputTokens()), success.usage().map(u -> u.outputTokens()),
					success.usage().flatMap(u -> u.reasoningTokens()), effort, BenchmarkChecks.words(text),
					BenchmarkChecks.flags(scenario, text));
		}
		AiResponse.Failure failure = (AiResponse.Failure) response;
		return new BenchmarkResult(scenario.number(), model, variant, failure.kind().name(), "", false,
				Optional.of(NarratorBenchmarkAccess.fallback(scenario.context())), attempt.latencyMs(), Optional.empty(), Optional.empty(),
				Optional.empty(), effort, 0, List.of());
	}

	private static BenchmarkResult placeholder(Scenario scenario, String model, String variant, String outcome) {
		return new BenchmarkResult(scenario.number(), model, variant, outcome, "", false,
				Optional.of(NarratorBenchmarkAccess.fallback(scenario.context())), 0, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), 0, List.of());
	}

	/** Provider error text, shortened, with anything key-like removed. */
	private static String safe(String message) {
		String text = message == null ? "" : message.replaceAll("sk-[A-Za-z0-9_\\-]{8,}", "[redacted]").replaceAll("\\s+", " ");
		return text.length() > 240 ? text.substring(0, 240) + "…" : text;
	}

	private static String addendum() throws IOException {
		try (InputStream in = NarratorBenchmarkRun.class.getClassLoader().getResourceAsStream("narrator-benchmark/dm-style-addendum.txt")) {
			if (in == null) {
				throw new IOException("Missing narrator-benchmark/dm-style-addendum.txt");
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
