package com.leeburke.springgame.ai.narration;

import java.util.Objects;

/** A run's finalised, persisted character introduction. Loaded as stored, never regenerated. */
public record CharacterIntroduction(String text, NarrationSource source, int promptVersion) {

	public CharacterIntroduction {
		Objects.requireNonNull(text, "text");
		if (text.isBlank()) {
			throw new IllegalArgumentException("Introduction text must not be blank");
		}
		Objects.requireNonNull(source, "source");
		if (promptVersion < 1) {
			throw new IllegalArgumentException("Prompt version must be at least 1");
		}
	}
}
