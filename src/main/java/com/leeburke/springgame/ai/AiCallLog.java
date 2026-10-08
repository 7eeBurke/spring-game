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
		LOG.info("ai role={} promptVersion={} model={} latencyMs={} attempts={} outcome={} inputTokens={} cachedInputTokens={} "
				+ "outputTokens={} reasoningTokens={}", role, promptVersion, model.isBlank() ? "-" : model, latency.toMillis(),
				attempts, outcome, usage.map(u -> (Object) u.inputTokens()).orElse("-"),
				usage.flatMap(AiUsage::cachedInputTokens).map(n -> (Object) n).orElse("-"),
				usage.map(u -> (Object) u.outputTokens()).orElse("-"),
				usage.flatMap(AiUsage::reasoningTokens).map(n -> (Object) n).orElse("-"));
	}
}
