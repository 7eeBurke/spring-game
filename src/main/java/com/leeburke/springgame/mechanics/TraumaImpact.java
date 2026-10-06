package com.leeburke.springgame.mechanics;

import java.util.Objects;

/**
 * The trauma outcome of an actual contact.
 *
 * @param rawScore       the full signed trauma sum, preserved for the breakdown
 * @param effectiveScore {@code max(0, rawScore)}, used for the severity thresholds
 */
public record TraumaImpact(int contactModifier, int rawScore, int effectiveScore, ImpactSeverity severity) {

	public TraumaImpact {
		Objects.requireNonNull(severity, "severity");
		if (effectiveScore < 0) {
			throw new IllegalArgumentException("Effective trauma score cannot be negative, but was " + effectiveScore);
		}
	}
}
