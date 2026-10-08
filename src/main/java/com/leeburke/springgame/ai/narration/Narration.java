package com.leeburke.springgame.ai.narration;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.ai.AiFailureKind;

/**
 * Presentation text from a narrator. It is never parsed back into mechanics or applied as state.
 *
 * @param fallbackReason why the deterministic fallback was used; empty for AI text
 */
public record Narration(String text, NarrationSource source, int promptVersion, Optional<AiFailureKind> fallbackReason) {

	public Narration {
		Objects.requireNonNull(text, "text");
		if (text.isBlank()) {
			throw new IllegalArgumentException("Narration text must not be blank");
		}
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(fallbackReason, "fallbackReason");
		if ((source == NarrationSource.FALLBACK) == fallbackReason.isEmpty()) {
			throw new IllegalArgumentException("Exactly fallback narrations carry a fallback reason");
		}
	}
}
