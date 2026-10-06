package com.leeburke.springgame.mechanics;

/**
 * V1 check-resolution policy tables. See docs/GAME_RULES.md "Core Stats", "Checks" and "Suitability".
 * <p>
 * Kept separate from the vocabulary types ({@link StatValue}, {@link Suitability}) so that rule
 * numbers live in one place.
 */
public final class CheckRules {

	public static final int D20_MIN = 1;
	public static final int D20_MAX = 20;

	/** The sum of ordinary numeric DC adjustments is clamped to plus or minus this value. */
	public static final int ADJUSTMENT_CAP = 5;

	/** Minimum margin for {@link DegreeOfSuccess#CRITICAL_SUCCESS}. */
	public static final int CRITICAL_SUCCESS_MARGIN = 5;

	/** Minimum margin for {@link DegreeOfSuccess#SUCCESS}. */
	public static final int SUCCESS_MARGIN = 0;

	/** Minimum margin for {@link DegreeOfSuccess#PARTIAL_SUCCESS}. */
	public static final int PARTIAL_SUCCESS_MARGIN = -4;

	private CheckRules() {
	}

	public static void requireValidD20Roll(int rawRoll) {
		if (rawRoll < D20_MIN || rawRoll > D20_MAX) {
			throw new IllegalArgumentException("A d20 roll must be between 1 and 20, but was " + rawRoll);
		}
	}

	public static int statModifier(StatValue statValue) {
		return switch (statValue.value()) {
			case 3 -> -3;
			case 4 -> -2;
			case 5 -> -1;
			case 6 -> 0;
			case 7 -> 1;
			case 8 -> 2;
			case 9 -> 3;
			case 10 -> 4;
			default -> throw new IllegalStateException("No modifier for stat value " + statValue.value());
		};
	}

	/** {@code IMPOSSIBLE} is rejected before any check; every other level permits a roll. */
	public static boolean permitsRoll(Suitability suitability) {
		return suitability != Suitability.IMPOSSIBLE;
	}

	/**
	 * DC adjustment for a rollable suitability. {@code IMPOSSIBLE} has no numeric value and is
	 * refused rather than represented as a large DC.
	 */
	public static int suitabilityDcAdjustment(Suitability suitability) {
		return switch (suitability) {
			case EXCELLENT -> -2;
			case GOOD -> -1;
			case FAIR -> 0;
			case POOR -> 1;
			case TERRIBLE -> 3;
			case IMPOSSIBLE -> throw new IllegalArgumentException(
					"IMPOSSIBLE suitability has no DC adjustment; no check is rolled");
		};
	}

	public static int clampAdjustmentTotal(int total) {
		return Math.clamp(total, -ADJUSTMENT_CAP, ADJUSTMENT_CAP);
	}

	/** A natural 1 has no special treatment; only the margin matters. */
	public static DegreeOfSuccess degreeFor(int margin) {
		if (margin >= CRITICAL_SUCCESS_MARGIN) {
			return DegreeOfSuccess.CRITICAL_SUCCESS;
		}
		if (margin >= SUCCESS_MARGIN) {
			return DegreeOfSuccess.SUCCESS;
		}
		if (margin >= PARTIAL_SUCCESS_MARGIN) {
			return DegreeOfSuccess.PARTIAL_SUCCESS;
		}
		return DegreeOfSuccess.FAILURE;
	}
}
