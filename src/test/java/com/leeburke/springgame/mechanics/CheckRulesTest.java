package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class CheckRulesTest {

	@ParameterizedTest
	@ValueSource(ints = { 1, 20 })
	void acceptsD20BoundaryRolls(int roll) {
		assertThatNoException().isThrownBy(() -> CheckRules.requireValidD20Roll(roll));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 21 })
	void rejectsOutOfRangeD20Rolls(int roll) {
		assertThatIllegalArgumentException().isThrownBy(() -> CheckRules.requireValidD20Roll(roll));
	}

	@ParameterizedTest
	@CsvSource({ "3,-3", "4,-2", "5,-1", "6,0", "7,1", "8,2", "9,3", "10,4" })
	void statModifierMatchesDocumentedTable(int statValue, int expectedModifier) {
		assertThat(CheckRules.statModifier(new StatValue(statValue))).isEqualTo(expectedModifier);
	}

	@ParameterizedTest
	@CsvSource({ "EXCELLENT,-2", "GOOD,-1", "FAIR,0", "POOR,1", "TERRIBLE,3" })
	void suitabilityAdjustmentMatchesDocumentedTable(Suitability suitability, int expectedAdjustment) {
		assertThat(CheckRules.permitsRoll(suitability)).isTrue();
		assertThat(CheckRules.suitabilityDcAdjustment(suitability)).isEqualTo(expectedAdjustment);
	}

	@Test
	void impossibleSuitabilityPermitsNoRollAndHasNoNumericAdjustment() {
		assertThat(CheckRules.permitsRoll(Suitability.IMPOSSIBLE)).isFalse();
		assertThatIllegalArgumentException()
				.isThrownBy(() -> CheckRules.suitabilityDcAdjustment(Suitability.IMPOSSIBLE));
	}

	@ParameterizedTest
	@EnumSource(value = Suitability.class, names = "IMPOSSIBLE", mode = EnumSource.Mode.EXCLUDE)
	void everyOtherSuitabilityPermitsRoll(Suitability suitability) {
		assertThat(CheckRules.permitsRoll(suitability)).isTrue();
	}

	@ParameterizedTest
	@CsvSource({ "7,5", "6,5", "5,5", "4,4", "0,0", "-4,-4", "-5,-5", "-6,-5", "-9,-5" })
	void adjustmentTotalIsClampedToCap(int total, int expected) {
		assertThat(CheckRules.clampAdjustmentTotal(total)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
			"100,CRITICAL_SUCCESS",
			"5,CRITICAL_SUCCESS",
			"4,SUCCESS",
			"0,SUCCESS",
			"-1,PARTIAL_SUCCESS",
			"-4,PARTIAL_SUCCESS",
			"-5,FAILURE",
			"-100,FAILURE" })
	void degreeBoundaries(int margin, DegreeOfSuccess expected) {
		assertThat(CheckRules.degreeFor(margin)).isEqualTo(expected);
	}
}
