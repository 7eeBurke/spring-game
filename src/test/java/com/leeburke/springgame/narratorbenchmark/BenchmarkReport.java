package com.leeburke.springgame.narratorbenchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import com.leeburke.springgame.ai.narration.NarratorBenchmarkAccess;
import com.leeburke.springgame.narratorbenchmark.BenchmarkScenarios.Scenario;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the benchmark's files: report.md (facts and all six narrations per scenario, grouped, with
 * production compatibility kept apart from prose quality), blind.md (the six per scenario as A–F),
 * blind-key.md (who wrote which) and results.json (raw data).
 */
final class BenchmarkReport {

	private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
	private static final String LETTERS = "ABCDEF";

	private BenchmarkReport() {
	}

	static void write(Path dir, List<Scenario> scenarios, List<BenchmarkResult> results, List<String> compatibility, boolean dryRun)
			throws IOException {
		Files.createDirectories(dir);
		Map<Integer, List<BenchmarkResult>> byScenario = results.stream()
				.collect(Collectors.groupingBy(BenchmarkResult::scenario, LinkedHashMap::new, Collectors.toList()));
		Files.writeString(dir.resolve("report.md"), report(scenarios, byScenario, results, compatibility, dryRun), StandardCharsets.UTF_8);
		StringBuilder key = new StringBuilder("# Blind comparison key\n\nOpen this only after choosing in `blind.md`.\n\n");
		Files.writeString(dir.resolve("blind.md"), blind(scenarios, byScenario, key), StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("blind-key.md"), key.toString(), StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("results.json"), JSON.writeValueAsString(results), StandardCharsets.UTF_8);
	}

	private static String report(List<Scenario> scenarios, Map<Integer, List<BenchmarkResult>> byScenario, List<BenchmarkResult> all,
			List<String> compatibility, boolean dryRun) {
		StringBuilder md = new StringBuilder("# Narrator benchmark\n\n");
		if (dryRun) {
			md.append("> **Dry run.** No model was called; the narrations below are placeholders. The scenarios, facts and report"
					+ " layout are real.\n\n");
		}
		md.append("Prompt **A** is the production Outcome Narrator v5, unchanged. Prompt **B** is v5 with the DM-style guidance"
				+ " appended (`src/test/resources/narrator-benchmark/dm-style-addendum.txt`). Every request is the production"
				+ " request (`OpenAiProvider.textParams`, 400 max output tokens, `store=false`); every response goes through the"
				+ " production extraction (`OpenAiProvider.interpret`) and the narrator's acceptance rule (non-empty, at most "
				+ NarratorBenchmarkAccess.maxLength() + " characters).\n\n");
		md.append("Word counts are pacing guides, not targets. Automatic flags are prompts for review, not verdicts.\n\n");

		md.append("## Production compatibility\n\n");
		compatibility.forEach(line -> md.append("- ").append(line).append('\n'));
		md.append("\n| Configuration | Displayed | Rejected or failed | Notes |\n|---|---|---|---|\n");
		configurations(all).forEach((config, rows) -> {
			long shown = rows.stream().filter(BenchmarkResult::accepted).count();
			String notes = rows.stream().filter(r -> !r.accepted()).map(r -> "#" + r.scenario() + " " + r.outcome()
					+ (r.outcome().equals("SUCCESS") ? " (over " + NarratorBenchmarkAccess.maxLength() + " chars)" : ""))
					.collect(Collectors.joining(", "));
			md.append("| ").append(config).append(" | ").append(shown).append("/").append(rows.size()).append(" | ")
					.append(rows.size() - shown).append(" | ").append(notes.isEmpty() ? "—" : notes).append(" |\n");
		});

		md.append("\n## Summary by model and prompt\n\n");
		md.append("| Configuration | Median latency | Mean latency | Mean input tok | Mean output tok | Mean reasoning tok | Mean words |"
				+ " Within pace guide | Review flags |\n|---|---|---|---|---|---|---|---|---|\n");
		configurations(all).forEach((config, rows) -> {
			List<Long> latency = rows.stream().map(BenchmarkResult::latencyMs).sorted().toList();
			md.append("| ").append(config)
					.append(" | ").append(latency.isEmpty() ? "—" : latency.get(latency.size() / 2) + " ms")
					.append(" | ").append(Math.round(rows.stream().mapToLong(BenchmarkResult::latencyMs).average().orElse(0))).append(" ms")
					.append(" | ").append(mean(rows, BenchmarkResult::inputTokens))
					.append(" | ").append(mean(rows, BenchmarkResult::outputTokens))
					.append(" | ").append(mean(rows, BenchmarkResult::reasoningTokens))
					.append(" | ").append(Math.round(rows.stream().mapToInt(BenchmarkResult::words).average().orElse(0)))
					.append(" | ").append(rows.stream().filter(r -> withinPace(scenarios, r)).count()).append("/").append(rows.size())
					.append(" | ").append(rows.stream().mapToInt(r -> r.flags().size()).sum()).append(" |\n");
		});
		md.append("\n### Repetition within each configuration\n\n");
		configurations(all).forEach((config, rows) -> {
			List<String> notes = BenchmarkChecks.repetition(rows);
			md.append("- **").append(config).append("**: ").append(notes.isEmpty() ? "no repeated openings, closings or phrases"
					: String.join("; ", notes)).append('\n');
		});
		md.append("\n### Words per scenario\n\n| Scenario (pace guide) |");
		List<String> configs = new ArrayList<>(configurations(all).keySet());
		configs.forEach(c -> md.append(' ').append(c).append(" |"));
		md.append("\n|---|").append("---|".repeat(configs.size())).append('\n');
		for (Scenario s : scenarios) {
			md.append("| ").append(s.number()).append(". ").append(s.pace().kind()).append(" (").append(s.pace().minWords()).append("–")
					.append(s.pace().maxWords()).append(") |");
			for (String config : configs) {
				md.append(' ').append(byScenario.getOrDefault(s.number(), List.of()).stream().filter(r -> r.configuration().equals(config))
						.map(r -> String.valueOf(r.words())).findFirst().orElse("—")).append(" |");
			}
			md.append('\n');
		}
		md.append("\n## Manual review\n\n_To be completed after reading every narration against its facts: invented objects, discoveries,"
				+ " state changes, environmental properties or consequences, as distinct from harmless literary description of confirmed"
				+ " events._\n\n");

		for (Scenario s : scenarios) {
			md.append("## ").append(s.number()).append(". ").append(s.title()).append("\n\n");
			md.append("- Player wording: \"").append(s.wording()).append("\"\n- Produced by: ").append(s.how()).append('\n');
			md.append("- Pace guide: ").append(s.pace().kind()).append(", about ").append(s.pace().minWords()).append("–")
					.append(s.pace().maxWords()).append(" words\n\n");
			md.append("<details><summary>Confirmed facts (the exact narrator input)</summary>\n\n```json\n")
					.append(pretty(NarratorBenchmarkAccess.requestJson(s.context()))).append("\n```\n</details>\n\n");
			md.append("Production fallback (what the game shows when no narration is accepted):\n\n> ")
					.append(NarratorBenchmarkAccess.fallback(s.context()).replace("\n", "\n> ")).append("\n\n");
			for (BenchmarkResult r : byScenario.getOrDefault(s.number(), List.of())) {
				md.append("### ").append(r.configuration()).append("\n\n");
				md.append(r.text().isBlank() ? "_(no text)_" : "> " + r.text().strip().replace("\n", "\n> ")).append("\n\n");
				md.append("- Production: ").append(r.outcome()).append(r.accepted() ? ", displayed" : ", **not displayed**")
						.append(r.shownInstead().isPresent() ? " (the fallback would be shown)" : "").append('\n');
				md.append("- ").append(r.latencyMs()).append(" ms · input ").append(r.inputTokens().map(String::valueOf).orElse("—"))
						.append(" · output ").append(r.outputTokens().map(String::valueOf).orElse("—"))
						.append(r.reasoningTokens().filter(t -> t > 0).map(t -> " (reasoning " + t + ")").orElse(""))
						.append(" · ").append(r.words()).append(" words")
						.append(r.reasoningEffort().map(e -> " · effort " + e).orElse("")).append('\n');
				md.append("- Review flags: ").append(r.flags().isEmpty() ? "none" : String.join("; ", r.flags())).append("\n\n");
			}
		}
		return md.toString();
	}

	private static String blind(List<Scenario> scenarios, Map<Integer, List<BenchmarkResult>> byScenario, StringBuilder key) {
		StringBuilder md = new StringBuilder("# Blind comparison\n\nSix narrations per scenario, labelled A–F in a different order for"
				+ " each scenario, with no model or prompt names. The key is in `blind-key.md`.\n\n");
		for (Scenario s : scenarios) {
			List<BenchmarkResult> rows = new ArrayList<>(byScenario.getOrDefault(s.number(), List.of()));
			Collections.shuffle(rows, new Random(31L * s.number() + 7));
			md.append("---\n\n## Scenario ").append(s.number()).append(": ").append(s.title()).append("\n\n");
			md.append("Player: \"").append(s.wording()).append("\"\n\n");
			md.append("<details><summary>Confirmed facts</summary>\n\n```json\n").append(pretty(NarratorBenchmarkAccess.requestJson(s.context())))
					.append("\n```\n</details>\n\n");
			key.append("## Scenario ").append(s.number()).append("\n\n");
			for (int i = 0; i < rows.size(); i++) {
				BenchmarkResult r = rows.get(i);
				char letter = LETTERS.charAt(i);
				md.append("### ").append(letter).append("\n\n")
						.append(r.text().isBlank() ? "_(no text: the game would show its fallback)_" : "> " + r.text().strip().replace("\n", "\n> "))
						.append("\n\n");
				key.append("- **").append(letter).append("**: ").append(r.configuration()).append('\n');
			}
			key.append('\n');
		}
		return md.toString();
	}

	private static Map<String, List<BenchmarkResult>> configurations(List<BenchmarkResult> all) {
		return all.stream().collect(Collectors.groupingBy(BenchmarkResult::configuration, LinkedHashMap::new, Collectors.toList()));
	}

	private static boolean withinPace(List<Scenario> scenarios, BenchmarkResult r) {
		BenchmarkScenarios.Pace pace = scenarios.get(r.scenario() - 1).pace();
		return r.words() >= pace.minWords() * 0.8 && r.words() <= pace.maxWords() * 1.2;
	}

	private static String mean(List<BenchmarkResult> rows, java.util.function.Function<BenchmarkResult, Optional<Long>> field) {
		List<Long> values = rows.stream().map(field).flatMap(Optional::stream).toList();
		return values.isEmpty() ? "—" : String.valueOf(Math.round(values.stream().mapToLong(Long::longValue).average().orElse(0)));
	}

	private static String pretty(String json) {
		JsonNode node = JSON.readTree(json);
		return JSON.writeValueAsString(node);
	}
}
