package com.leeburke.springgame.mechanics;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Computes {@code max(0, round(baseDamage x contact x effectiveness) - protection)} exactly.
 * There is deliberately no minimum-one-damage rule; contact may deal 0 HP.
 * Results too large for an {@code int} throw {@link ArithmeticException} rather than wrapping.
 */
public final class DamageCalculator {

	public DamageResult calculate(DamageRequest request) {
		Objects.requireNonNull(request, "request");
		BigDecimal contactMultiplier = DamageRules.contactMultiplier(request.contactQuality());
		BigDecimal effectivenessMultiplier = DamageRules.effectivenessMultiplier(request.effectiveness());

		int damageBeforeProtection = BigDecimal.valueOf(request.baseDamage())
				.multiply(contactMultiplier)
				.multiply(effectivenessMultiplier)
				.setScale(0, DamageRules.HP_ROUNDING)
				.intValueExact();
		int finalDamage = Math.max(0, Math.subtractExact(damageBeforeProtection, request.protection()));

		return new DamageResult(
				request.baseDamage(),
				request.contactQuality(),
				contactMultiplier,
				request.effectiveness(),
				effectivenessMultiplier,
				damageBeforeProtection,
				request.protection(),
				finalDamage);
	}
}
