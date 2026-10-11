package com.leeburke.springgame.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-role generation settings. {@code model} may be blank when AI is not configured; a provider
 * then reports {@link AiFailureKind#NOT_CONFIGURED}. Temperature is sent only when configured,
 * because some models reject it; so is {@code reasoningEffort} (for reasoning models only, such as
 * "none" for a fast narrator). Output is never assumed deterministic, whatever the settings.
 */
public record AiGenerationSettings(String model, int maxOutputTokens, Duration timeout, Optional<Double> temperature,
		Optional<String> reasoningEffort) {

	/** Settings with no reasoning effort (every non-reasoning model). */
	public AiGenerationSettings(String model, int maxOutputTokens, Duration timeout, Optional<Double> temperature) {
		this(model, maxOutputTokens, timeout, temperature, Optional.empty());
	}

	public AiGenerationSettings {
		Objects.requireNonNull(model, "model");
		if (maxOutputTokens < 1) {
			throw new IllegalArgumentException("maxOutputTokens must be at least 1");
		}
		Objects.requireNonNull(timeout, "timeout");
		if (timeout.isNegative() || timeout.isZero()) {
			throw new IllegalArgumentException("timeout must be positive");
		}
		Objects.requireNonNull(temperature, "temperature");
		Objects.requireNonNull(reasoningEffort, "reasoningEffort");
		reasoningEffort = reasoningEffort.map(String::strip).filter(effort -> !effort.isEmpty());
	}
}
