package com.leeburke.springgame.mechanics;

import java.util.OptionalInt;

/**
 * V1 trauma tables. See docs/GAME_RULES.md "Trauma".
 */
public final class TraumaRules {

	/** Minimum effective score for {@link ImpactSeverity#SOLID}. */
	public static final int SOLID_THRESHOLD = 3;

	/** Minimum effective score for {@link ImpactSeverity#SEVERE}. */
	public static final int SEVERE_THRESHOLD = 6;

	/** Minimum effective score for {@link ImpactSeverity#DEVASTATING}; there is no upper limit. */
	public static final int DEVASTATING_THRESHOLD = 9;

	private TraumaRules() {
	}

	/** {@code NONE} has no modifier: without contact there is no trauma calculation. */
	public static int contactModifier(ContactQuality contactQuality) {
		return switch (contactQuality) {
			case GLANCING -> -2;
			case SOLID -> 0;
			case CLEAN -> 2;
			case NONE -> throw new IllegalArgumentException("No contact occurred, so there is no trauma contact modifier");
		};
	}

	/**
	 * Existing-injury trauma modifier for the struck body part's current severity: healthy 0,
	 * injured +1, wounded +2, crippled +3. Empty for DESTROYED, whose value is not yet defined.
	 */
	public static OptionalInt existingInjuryModifier(BodySeverity severity) {
		return switch (severity) {
			case HEALTHY -> OptionalInt.of(0);
			case INJURED -> OptionalInt.of(1);
			case WOUNDED -> OptionalInt.of(2);
			case CRIPPLED -> OptionalInt.of(3);
			case DESTROYED -> OptionalInt.empty();
		};
	}

	public static ImpactSeverity severityFor(int effectiveScore) {
		if (effectiveScore < 0) {
			throw new IllegalArgumentException("Effective trauma score cannot be negative, but was " + effectiveScore);
		}
		if (effectiveScore >= DEVASTATING_THRESHOLD) {
			return ImpactSeverity.DEVASTATING;
		}
		if (effectiveScore >= SEVERE_THRESHOLD) {
			return ImpactSeverity.SEVERE;
		}
		if (effectiveScore >= SOLID_THRESHOLD) {
			return ImpactSeverity.SOLID;
		}
		return ImpactSeverity.GLANCING;
	}
}
