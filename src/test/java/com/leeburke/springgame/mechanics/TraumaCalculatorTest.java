package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TraumaCalculatorTest {

	private final TraumaCalculator calculator = new TraumaCalculator();

	private static TraumaRequest warHammer(ContactQuality contact) {
		// weapon trauma 6, anatomy +1, attack form +1 (supplied inputs; their derivation is deferred)
		return new TraumaRequest(6, contact, 0, 1, 1, 0, 0);
	}

	private TraumaImpact impactOf(TraumaRequest request) {
		return calculator.calculate(request).impact().orElseThrow();
	}

	@Test
	void warHammerSolidRetainsFullBreakdown() {
		TraumaRequest request = warHammer(ContactQuality.SOLID);

		TraumaResult result = calculator.calculate(request);

		assertThat(result.request()).isEqualTo(request);
		TraumaImpact impact = result.impact().orElseThrow();
		assertThat(impact.contactModifier()).isZero();
		assertThat(impact.rawScore()).isEqualTo(8);
		assertThat(impact.effectiveScore()).isEqualTo(8);
		assertThat(impact.severity()).isEqualTo(ImpactSeverity.SEVERE);
		assertThat(result.impactSeverity()).contains(ImpactSeverity.SEVERE);
	}

	@ParameterizedTest
	@CsvSource({ "GLANCING,-2,6,SEVERE", "CLEAN,2,10,DEVASTATING" })
	void contactModifierAppliesInContext(ContactQuality contact, int modifier, int score, ImpactSeverity severity) {
		TraumaImpact impact = impactOf(warHammer(contact));
		assertThat(impact.contactModifier()).isEqualTo(modifier);
		assertThat(impact.rawScore()).isEqualTo(score);
		assertThat(impact.severity()).isEqualTo(severity);
	}

	@Test
	void negativeRawScoreBecomesEffectiveZero() {
		// Dagger trauma 2, GLANCING -2, trauma protection 1, defensive mitigation 3
		TraumaResult result = calculator.calculate(new TraumaRequest(2, ContactQuality.GLANCING, 0, 0, 0, 1, 3));

		TraumaImpact impact = result.impact().orElseThrow();
		assertThat(impact.rawScore()).isEqualTo(-4);
		assertThat(impact.effectiveScore()).isZero();
		assertThat(impact.severity()).isEqualTo(ImpactSeverity.GLANCING);
	}

	@Test
	void everyTermContributesExactly() {
		int baseline = impactOf(new TraumaRequest(5, ContactQuality.SOLID, 0, 0, 0, 0, 0)).rawScore();
		assertThat(baseline).isEqualTo(5);
		assertThat(impactOf(new TraumaRequest(5, ContactQuality.SOLID, 3, 0, 0, 0, 0)).rawScore()).isEqualTo(8);
		assertThat(impactOf(new TraumaRequest(5, ContactQuality.SOLID, 0, -2, 0, 0, 0)).rawScore()).isEqualTo(3);
		assertThat(impactOf(new TraumaRequest(5, ContactQuality.SOLID, 0, 0, 4, 0, 0)).rawScore()).isEqualTo(9);
		assertThat(impactOf(new TraumaRequest(5, ContactQuality.SOLID, 0, 0, 0, 2, 0)).rawScore()).isEqualTo(3);
		assertThat(impactOf(new TraumaRequest(5, ContactQuality.SOLID, 0, 0, 0, 0, 1)).rawScore()).isEqualTo(4);
	}

	@Test
	void noContactHasNoImpact() {
		TraumaRequest request = new TraumaRequest(20, ContactQuality.NONE, 3, 2, 2, 0, 0);

		TraumaResult result = calculator.calculate(request);

		assertThat(result.request()).isEqualTo(request);
		assertThat(result.impact()).isEmpty();
		assertThat(result.impactSeverity()).isEmpty();
	}

	@Test
	void resultRejectsImpactWithoutContact() {
		TraumaImpact impact = new TraumaImpact(0, 5, 5, ImpactSeverity.SOLID);
		TraumaRequest noContact = new TraumaRequest(5, ContactQuality.NONE, 0, 0, 0, 0, 0);
		assertThatIllegalArgumentException().isThrownBy(() -> new TraumaResult(noContact, Optional.of(impact)));
	}

	@Test
	void resultRequiresImpactWhenContactOccurred() {
		TraumaRequest contact = new TraumaRequest(5, ContactQuality.SOLID, 0, 0, 0, 0, 0);
		assertThatIllegalArgumentException().isThrownBy(() -> new TraumaResult(contact, Optional.empty()));
	}

	@Test
	void positiveOverflowThrows() {
		TraumaRequest request = new TraumaRequest(Integer.MAX_VALUE, ContactQuality.CLEAN, 0, 0, 0, 0, 0);
		assertThatThrownBy(() -> calculator.calculate(request)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void negativeOverflowThrows() {
		TraumaRequest request = new TraumaRequest(0, ContactQuality.SOLID, 0, 0, Integer.MIN_VALUE, 0, 1);
		assertThatThrownBy(() -> calculator.calculate(request)).isInstanceOf(ArithmeticException.class);
	}
}
