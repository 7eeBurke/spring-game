package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DamageResultTest {

	private static final BigDecimal NEGATIVE = new BigDecimal("-0.25");

	private static DamageResult result(BigDecimal contactMultiplier, BigDecimal effectivenessMultiplier) {
		return new DamageResult(6, ContactQuality.SOLID, contactMultiplier, Effectiveness.NORMAL,
				effectivenessMultiplier, 0, 0, 0);
	}

	@Test
	void rejectsNegativeContactMultiplier() {
		assertThatIllegalArgumentException().isThrownBy(() -> result(NEGATIVE, BigDecimal.ONE));
	}

	@Test
	void rejectsNegativeEffectivenessMultiplier() {
		assertThatIllegalArgumentException().isThrownBy(() -> result(BigDecimal.ONE, NEGATIVE));
	}

	@Test
	void acceptsZeroMultipliers() {
		assertThat(result(BigDecimal.ZERO, BigDecimal.ZERO).finalDamage()).isZero();
	}
}
