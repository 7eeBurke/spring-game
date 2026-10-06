package com.leeburke.springgame.mechanics;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable snapshot of an HP-damage calculation and its breakdown. Produced by {@link DamageCalculator}.
 *
 * @param damageBeforeProtection {@code round(baseDamage x contactMultiplier x effectivenessMultiplier)}
 * @param finalDamage            {@code max(0, damageBeforeProtection - protection)}
 */
public record DamageResult(
		int baseDamage,
		ContactQuality contactQuality,
		BigDecimal contactMultiplier,
		Effectiveness effectiveness,
		BigDecimal effectivenessMultiplier,
		int damageBeforeProtection,
		int protection,
		int finalDamage) {

	public DamageResult {
		Objects.requireNonNull(contactQuality, "contactQuality");
		Objects.requireNonNull(contactMultiplier, "contactMultiplier");
		Objects.requireNonNull(effectiveness, "effectiveness");
		Objects.requireNonNull(effectivenessMultiplier, "effectivenessMultiplier");
		requireNonNegative(contactMultiplier, "contactMultiplier");
		requireNonNegative(effectivenessMultiplier, "effectivenessMultiplier");
		requireNonNegative(baseDamage, "baseDamage");
		requireNonNegative(damageBeforeProtection, "damageBeforeProtection");
		requireNonNegative(protection, "protection");
		requireNonNegative(finalDamage, "finalDamage");
	}

	private static void requireNonNegative(BigDecimal value, String name) {
		if (value.signum() < 0) {
			throw new IllegalArgumentException(name + " cannot be negative, but was " + value);
		}
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " cannot be negative, but was " + value);
		}
	}
}
