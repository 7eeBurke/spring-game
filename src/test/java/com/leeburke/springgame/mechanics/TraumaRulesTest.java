package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TraumaRulesTest {

	@ParameterizedTest
	@CsvSource({ "GLANCING,-2", "SOLID,0", "CLEAN,2" })
	void contactModifiersMatchDocumentedTable(ContactQuality contactQuality, int expected) {
		assertThat(TraumaRules.contactModifier(contactQuality)).isEqualTo(expected);
	}

	@Test
	void noContactHasNoModifier() {
		assertThatIllegalArgumentException().isThrownBy(() -> TraumaRules.contactModifier(ContactQuality.NONE));
	}

	@ParameterizedTest
	@CsvSource({
			"0,GLANCING",
			"2,GLANCING",
			"3,SOLID",
			"5,SOLID",
			"6,SEVERE",
			"8,SEVERE",
			"9,DEVASTATING",
			"50,DEVASTATING",
			"2147483647,DEVASTATING" })
	void severityBoundaries(int effectiveScore, ImpactSeverity expected) {
		assertThat(TraumaRules.severityFor(effectiveScore)).isEqualTo(expected);
	}

	@Test
	void rejectsNegativeEffectiveScore() {
		assertThatIllegalArgumentException().isThrownBy(() -> TraumaRules.severityFor(-1));
	}
}
