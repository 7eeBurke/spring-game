package com.leeburke.springgame.action;

import java.util.Objects;
import java.util.Optional;

/**
 * A phrase the interpreter could not confidently resolve to anything it was shown. Carries no
 * guessed ID. Any unresolved reference makes the intent non-executable until reinterpreted.
 *
 * @param stepId the affected step, if known
 * @param phrase the player's own words
 */
public record UnresolvedReference(Optional<String> stepId, String phrase) {

	public UnresolvedReference {
		Objects.requireNonNull(stepId, "stepId");
		stepId.ifPresent(id -> Refs.require(id, "Unresolved reference step id"));
		Objects.requireNonNull(phrase, "phrase");
		if (phrase.isBlank()) {
			throw new IllegalArgumentException("Unresolved phrase must not be blank");
		}
	}
}
