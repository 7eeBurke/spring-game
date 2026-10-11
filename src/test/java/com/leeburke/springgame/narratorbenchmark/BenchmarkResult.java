package com.leeburke.springgame.narratorbenchmark;

import java.util.List;
import java.util.Optional;

/**
 * One narration in the benchmark: which model and prompt produced it, how the production
 * extraction and acceptance path judged it, what it cost, and the automatic review flags.
 *
 * @param variant          "A" (production narrator v5) or "B" (v5 plus DM-style guidance)
 * @param outcome          SUCCESS, a production failure kind (TRUNCATED, REFUSED, MALFORMED_RESPONSE, ...),
 *                         an HTTP problem, or DRY_RUN
 * @param text             the extracted text (empty on failure)
 * @param accepted         whether the production narrator would display it (non-empty, within the length limit)
 * @param shownInstead     what the game would show when not accepted (its deterministic fallback)
 * @param reasoningEffort  the effort sent, for models that take one
 */
record BenchmarkResult(int scenario, String model, String variant, String outcome, String text, boolean accepted,
		Optional<String> shownInstead, long latencyMs, Optional<Long> inputTokens, Optional<Long> outputTokens,
		Optional<Long> reasoningTokens, Optional<String> reasoningEffort, int words, List<String> flags) {

	String configuration() {
		return model + " · " + (variant.equals("A") ? "A (production v5)" : "B (DM style)");
	}
}
