package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DamageCalculatorTest {

	private final DamageCalculator calculator = new DamageCalculator();

	private DamageResult calculate(int base, ContactQuality contact, Effectiveness effectiveness, int protection) {
		return calculator.calculate(new DamageRequest(base, contact, effectiveness, protection));
	}

	@Test
	void longswordSolidNormalWithProtectionOne() {
		DamageResult result = calculate(6, ContactQuality.SOLID, Effectiveness.NORMAL, 1);
		assertThat(result.damageBeforeProtection()).isEqualTo(6);
		assertThat(result.finalDamage()).isEqualTo(5);
	}

	@Test
	void warHammerSolidHighRetainsFullBreakdown() {
		DamageResult result = calculate(7, ContactQuality.SOLID, Effectiveness.HIGH, 0);

		assertThat(result.baseDamage()).isEqualTo(7);
		assertThat(result.contactQuality()).isEqualTo(ContactQuality.SOLID);
		assertThat(result.contactMultiplier()).isEqualByComparingTo("1.00");
		assertThat(result.effectiveness()).isEqualTo(Effectiveness.HIGH);
		assertThat(result.effectivenessMultiplier()).isEqualByComparingTo("1.25");
		assertThat(result.damageBeforeProtection()).isEqualTo(9); // 8.75 rounds to 9
		assertThat(result.protection()).isZero();
		assertThat(result.finalDamage()).isEqualTo(9);
	}

	@Test
	void weakGlancingVeryLowContactDealsZero() {
		DamageResult result = calculate(3, ContactQuality.GLANCING, Effectiveness.VERY_LOW, 0); // 0.375
		assertThat(result.damageBeforeProtection()).isZero();
		assertThat(result.finalDamage()).isZero();
	}

	@ParameterizedTest
	@CsvSource({
			"5,GLANCING,NORMAL,3",   // 2.5    exact half rounds up
			"4,GLANCING,VERY_LOW,1", // 0.5    exact half rounds up
			"7,GLANCING,LOW,2",      // 1.75   rounds up
			"5,CLEAN,VERY_LOW,2",    // 1.5625 rounds up
			"9,GLANCING,LOW,2" })    // 2.25   rounds down
	void roundsToNearestWithHalfUp(int base, ContactQuality contact, Effectiveness effectiveness, int expected) {
		assertThat(calculate(base, contact, effectiveness, 0).damageBeforeProtection()).isEqualTo(expected);
	}

	@Test
	void noContactDealsZero() {
		DamageResult result = calculate(100, ContactQuality.NONE, Effectiveness.VERY_HIGH, 0);
		assertThat(result.contactMultiplier()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(result.damageBeforeProtection()).isZero();
		assertThat(result.finalDamage()).isZero();
	}

	@Test
	void protectionIsSubtracted() {
		assertThat(calculate(6, ContactQuality.SOLID, Effectiveness.NORMAL, 2).finalDamage()).isEqualTo(4);
	}

	@Test
	void protectionAboveDamageGivesZeroNotNegative() {
		DamageResult result = calculate(6, ContactQuality.SOLID, Effectiveness.NORMAL, 10);
		assertThat(result.damageBeforeProtection()).isEqualTo(6);
		assertThat(result.finalDamage()).isZero();
	}

	@Test
	void protectionEqualToDamageGivesZero() {
		assertThat(calculate(6, ContactQuality.SOLID, Effectiveness.NORMAL, 6).finalDamage()).isZero();
	}

	@Test
	void overflowThrowsInsteadOfWrapping() {
		assertThatThrownBy(() -> calculate(Integer.MAX_VALUE, ContactQuality.CLEAN, Effectiveness.VERY_HIGH, 0))
				.isInstanceOf(ArithmeticException.class);
	}
}
