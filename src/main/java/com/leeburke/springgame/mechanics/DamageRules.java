package com.leeburke.springgame.mechanics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V1 HP-damage tables. See docs/GAME_RULES.md "Damage".
 * <p>
 * Multipliers are exact decimals (all are whole quarters), always at scale 2, so arithmetic is
 * exact and record equality on results is consistent.
 */
public final class DamageRules {

	/** Nearest whole HP; an exact half rounds up (inputs are non-negative). */
	public static final RoundingMode HP_ROUNDING = RoundingMode.HALF_UP;

	private static final BigDecimal ZERO = new BigDecimal("0.00");
	private static final BigDecimal QUARTER = new BigDecimal("0.25");
	private static final BigDecimal HALF = new BigDecimal("0.50");
	private static final BigDecimal ONE = new BigDecimal("1.00");
	private static final BigDecimal ONE_AND_QUARTER = new BigDecimal("1.25");
	private static final BigDecimal ONE_AND_HALF = new BigDecimal("1.50");

	private DamageRules() {
	}

	public static BigDecimal contactMultiplier(ContactQuality contactQuality) {
		return switch (contactQuality) {
			case NONE -> ZERO;
			case GLANCING -> HALF;
			case SOLID -> ONE;
			case CLEAN -> ONE_AND_QUARTER;
		};
	}

	public static BigDecimal effectivenessMultiplier(Effectiveness effectiveness) {
		return switch (effectiveness) {
			case VERY_LOW -> QUARTER;
			case LOW -> HALF;
			case NORMAL -> ONE;
			case HIGH -> ONE_AND_QUARTER;
			case VERY_HIGH -> ONE_AND_HALF;
		};
	}
}
