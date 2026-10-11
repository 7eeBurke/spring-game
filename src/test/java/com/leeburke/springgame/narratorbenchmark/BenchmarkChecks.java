package com.leeburke.springgame.narratorbenchmark;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.WORLD;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.NarratorBenchmarkAccess;
import com.leeburke.springgame.narratorbenchmark.BenchmarkScenarios.Scenario;

/**
 * Automatic review flags for one narration. These are prompts for a human reader, never verdicts:
 * a flag marks something worth checking against the facts (a map label, a catalogue name the facts
 * do not contain, a stock closing, a length well outside the pacing guide, useful information
 * apparently missing). Harmless literary description of confirmed events is not flagged.
 */
final class BenchmarkChecks {

	private static final Pattern INTERNAL = Pattern.compile("\\b(zone|object|entity|exit|hazard|item|weapon)_\\d+\\b|first_find|lantern_road|"
			+ "nave_entrance|central_aisle|[A-Z]{3,}_[A-Z]{3,}");
	private static final List<String> STOCK = List.of("remains silent", "remain silent", "the darkness waits", "darkness waits",
			"your next move", "prepare for", "you prepare", "what will you do", "awaits your", "the silence", "silence settles",
			"hangs heavy", "hang heavy", "you steel yourself", "for now");
	/** In-world names the narrator may use: the lore's public names. */
	private static final Set<String> IN_WORLD_NAMES = Set.of("hollow chapel", "the last lantern", "last lantern", "chapel road");

	private BenchmarkChecks() {
	}

	static int words(String text) {
		String stripped = text.strip();
		return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
	}

	static List<String> flags(Scenario scenario, String text) {
		List<String> flags = new ArrayList<>();
		if (text.isBlank()) {
			return flags;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		String facts = NarratorBenchmarkAccess.requestJson(scenario.context()).toLowerCase(Locale.ROOT);

		Matcher internal = INTERNAL.matcher(text);
		if (internal.find()) {
			flags.add("internal identifier: `" + internal.group() + "`");
		}
		for (String label : labels(scenario)) {
			String l = label.toLowerCase(Locale.ROOT);
			if (!IN_WORLD_NAMES.contains(l) && lower.contains(l)) {
				flags.add("map label used as a name: \"" + label + "\"");
			}
		}
		for (String name : catalogueNames()) {
			String n = name.toLowerCase(Locale.ROOT);
			if (Pattern.compile("\\b" + Pattern.quote(n) + "\\b").matcher(lower).find() && !facts.contains(n)) {
				flags.add("names something not in the facts: \"" + name + "\" (check)");
			}
		}
		for (String stock : STOCK) {
			if (lower.contains(stock)) {
				flags.add("stock phrase: \"" + stock + "\"");
			}
		}
		int words = words(text);
		BenchmarkScenarios.Pace pace = scenario.pace();
		if (words > pace.maxWords() * 1.5) {
			flags.add("long for its importance: " + words + " words (guide " + pace.minWords() + "–" + pace.maxWords() + ")");
		} else if (words < pace.minWords() * 0.5) {
			flags.add("very brief: " + words + " words (guide " + pace.minWords() + "–" + pace.maxWords() + ")");
		}
		usefulness(scenario, lower).ifPresent(flags::add);
		return flags;
	}

	/** Whether the gameplay-relevant information in the facts reached the text. */
	private static java.util.Optional<String> usefulness(Scenario scenario, String lower) {
		NarrationFact fact = scenario.context().facts().getFirst();
		return switch (fact) {
			case NarrationFact.OpenedContainer opened -> opened.contents().stream().map(NarrationFact.Found::item)
					.filter(item -> !lower.contains(lastWord(item))).findFirst()
					.map(item -> "does not say what the crate holds (" + item + ")");
			case NarrationFact.ContainerOpened opened -> opened.contents().stream()
					.filter(item -> !lower.contains(lastWord(item))).findFirst()
					.map(item -> "does not say what the crate holds (" + item + ")");
			case NarrationFact.TookItem took -> {
				if (!lower.contains(lastWord(took.item())) && !lower.contains("vial")) {
					yield java.util.Optional.of("does not name what was taken");
				}
				if (lower.matches("(?s).*\\b(still|remains?) (in|inside)\\b.*")) {
					yield java.util.Optional.of("may say the item is still in the crate (check)");
				}
				yield java.util.Optional.empty();
			}
			case NarrationFact.SoughtWays ways -> {
				List<String> missed = ways.unexplored().stream().map(lead -> keyWord(lead.what()))
						.filter(word -> !lower.contains(word)).toList();
				yield missed.isEmpty() ? java.util.Optional.empty()
						: java.util.Optional.of("does not mention an unexplored way (" + String.join(", ", missed) + ")");
			}
			case NarrationFact.CrossedInto crossed -> crossed.behind().filter(back -> !lower.contains(keyWord(back)))
					.map(back -> "does not mention the way back in (" + back + ")");
			case NarrationFact.PlayerAttacked hit -> lower.contains(lastWord(hit.targetName())) ? java.util.Optional.empty()
					: java.util.Optional.of("does not name the foe");
			default -> java.util.Optional.empty();
		};
	}

	/** The map labels in the facts: zone labels and scene names (the narrator is told not to use them). */
	private static Set<String> labels(Scenario scenario) {
		Set<String> labels = new LinkedHashSet<>();
		Matcher m = Pattern.compile("\"label\":\"([^\"]+)\"").matcher(NarratorBenchmarkAccess.requestJson(scenario.context()));
		while (m.find()) {
			labels.add(m.group(1));
		}
		scenario.context().facts().forEach(f -> {
			if (f instanceof NarrationFact.CrossedInto crossed) {
				labels.add(crossed.scene());
			}
		});
		return labels;
	}

	private static Set<String> catalogueNames() {
		Set<String> names = new LinkedHashSet<>();
		CONTENT.items().forEach(i -> names.add(i.displayName()));
		WORLD.elements().forEach(e -> names.add(e.displayName()));
		return names;
	}

	private static String lastWord(String phrase) {
		String[] parts = phrase.toLowerCase(Locale.ROOT).split("\\s+");
		return parts[parts.length - 1];
	}

	/** The most telling noun of a passage phrase: its longest word. */
	private static String keyWord(String phrase) {
		String best = "";
		for (String word : phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").split("\\s+")) {
			if (word.length() > best.length() && !Set.of("behind", "through", "beside", "where", "broken", "chapel").contains(word)) {
				best = word;
			}
		}
		return best;
	}

	/** Opening words and closing words of each text in a configuration, repeated across its scenarios. */
	static List<String> repetition(List<BenchmarkResult> sameConfiguration) {
		List<String> notes = new ArrayList<>();
		java.util.Map<String, Integer> openings = new java.util.HashMap<>();
		java.util.Map<String, Integer> closings = new java.util.HashMap<>();
		java.util.Map<String, Integer> trigrams = new java.util.HashMap<>();
		for (BenchmarkResult result : sameConfiguration) {
			String[] w = result.text().toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").trim().split("\\s+");
			if (w.length < 4) {
				continue;
			}
			openings.merge(String.join(" ", w[0], w[1]), 1, Integer::sum);
			closings.merge(String.join(" ", w[w.length - 3], w[w.length - 2], w[w.length - 1]), 1, Integer::sum);
			Set<String> seen = new java.util.HashSet<>();
			for (int i = 0; i + 2 < w.length; i++) {
				String tri = w[i] + " " + w[i + 1] + " " + w[i + 2];
				if (seen.add(tri) && !tri.matches("(the|a|of|to|and|in|you|your|is|it) (the|a|of|to|and|in|you|your|is|it) .*")) {
					trigrams.merge(tri, 1, Integer::sum);
				}
			}
		}
		openings.forEach((k, v) -> {
			if (v >= 3) {
				notes.add("opens with \"" + k + "\" in " + v + " of 8");
			}
		});
		closings.forEach((k, v) -> {
			if (v >= 2) {
				notes.add("ends with \"" + k + "\" in " + v + " of 8");
			}
		});
		trigrams.entrySet().stream().filter(e -> e.getValue() >= 3).sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
				.forEach(e -> notes.add("phrase \"" + e.getKey() + "\" in " + e.getValue() + " of 8"));
		return notes;
	}
}
