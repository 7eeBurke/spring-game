package com.leeburke.springgame.ai;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One safe operational log line per role call: role, prompt version, model, latency, attempts,
 * outcome and token usage. Never the key, headers, player text, prompts, model output or game
 * state. Usage is logged only; it is never persisted.
 */
public final class AiCallLog {

	private static final Logger LOG = LoggerFactory.getLogger(AiCallLog.class);

	private AiCallLog() {
	}

	public static void record(AiRole role, int promptVersion, String model, Duration latency, int attempts, String outcome,
			Optional<AiUsage> usage) {
		record(role, promptVersion, model, latency, attempts, outcome, usage, Optional.empty());
	}

	/**
	 * @param repairCause why a first answer was not accepted, as categories and fixed codes only (for
	 *                    example {@code PARSE:schemaVersion} or {@code VALIDATION:SCHEMA_INVALID});
	 *                    anything outside that alphabet is dropped, so no free text can reach the log
	 */
	public static void record(AiRole role, int promptVersion, String model, Duration latency, int attempts, String outcome,
			Optional<AiUsage> usage, Optional<String> repairCause) {
		LOG.info("ai role={} promptVersion={} model={} latencyMs={} attempts={} outcome={} repairCause={} inputTokens={} "
				+ "cachedInputTokens={} outputTokens={} reasoningTokens={}", role, promptVersion, model.isBlank() ? "-" : model,
				latency.toMillis(), attempts, outcome, repairCause.map(AiCallLog::safe).filter(c -> !c.isEmpty()).orElse("-"),
				usage.map(u -> (Object) u.inputTokens()).orElse("-"),
				usage.flatMap(AiUsage::cachedInputTokens).map(n -> (Object) n).orElse("-"),
				usage.map(u -> (Object) u.outputTokens()).orElse("-"),
				usage.flatMap(AiUsage::reasoningTokens).map(n -> (Object) n).orElse("-"));
	}

	static String safe(String cause) {
		String kept = cause.replaceAll("[^A-Za-z0-9_.,:\\[\\]]", "");
		return kept.length() > 120 ? kept.substring(0, 120) : kept;
	}
}
