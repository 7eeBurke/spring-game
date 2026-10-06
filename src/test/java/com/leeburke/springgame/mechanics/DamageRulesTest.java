package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DamageRulesTest {

	@ParameterizedTest
	@CsvSource({ "NONE,0", "GLANCING,0.5", "SOLID,1", "CLEAN,1.25" })
	void contactMultipliersMatchDocumentedTable(ContactQuality contactQuality, BigDecimal expected) {
		assertThat(DamageRules.contactMultiplier(contactQuality)).isEqualByComparingTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "VERY_LOW,0.25", "LOW,0.5", "NORMAL,1", "HIGH,1.25", "VERY_HIGH,1.5" })
	void effectivenessMultipliersMatchDocumentedTable(Effectiveness effectiveness, BigDecimal expected) {
		assertThat(DamageRules.effectivenessMultiplier(effectiveness)).isEqualByComparingTo(expected);
	}
}
