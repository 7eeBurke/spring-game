package com.leeburke.springgame.ai;

import java.util.Objects;
import java.util.Optional;

/**
 * Token usage a provider reported for one call. Operational metadata only: logged, never persisted,
 * never game state, and free of any content. Cached-input and reasoning counts are present only
 * when the provider reports them.
 */
public record AiUsage(long inputTokens, long outputTokens, Optional<Long> cachedInputTokens, Optional<Long> reasoningTokens) {

	public AiUsage {
		if (inputTokens < 0 || outputTokens < 0) {
			throw new IllegalArgumentException("Token counts cannot be negative");
		}
		Objects.requireNonNull(cachedInputTokens, "cachedInputTokens");
		Objects.requireNonNull(reasoningTokens, "reasoningTokens");
		if (cachedInputTokens.filter(n -> n < 0).isPresent() || reasoningTokens.filter(n -> n < 0).isPresent()) {
			throw new IllegalArgumentException("Token counts cannot be negative");
		}
	}

	/** The sum of two calls' usage; an optional count is present if either call reported it. */
	public AiUsage plus(AiUsage other) {
		return new AiUsage(Math.addExact(inputTokens, other.inputTokens), Math.addExact(outputTokens, other.outputTokens),
				sum(cachedInputTokens, other.cachedInputTokens), sum(reasoningTokens, other.reasoningTokens));
	}

	/** Sums optional usage: empty only if neither is present. */
	public static Optional<AiUsage> combine(Optional<AiUsage> a, Optional<AiUsage> b) {
		if (a.isEmpty()) {
			return b;
		}
		return b.map(a.get()::plus).or(() -> a);
	}

	private static Optional<Long> sum(Optional<Long> a, Optional<Long> b) {
		if (a.isEmpty()) {
			return b;
		}
		return Optional.of(Math.addExact(a.get(), b.orElse(0L)));
	}
}
