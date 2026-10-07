package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContactRulesTest {

	@ParameterizedTest
	@CsvSource({ "CRITICAL_SUCCESS, CLEAN", "SUCCESS, SOLID", "PARTIAL_SUCCESS, GLANCING", "FAILURE, NONE" })
	void attackerDegreeMapsToContact(DegreeOfSuccess degree, ContactQuality contact) {
		assertThat(ContactRules.attackContact(degree)).isEqualTo(contact);
	}

	@ParameterizedTest
	@CsvSource({ "CRITICAL_SUCCESS, NONE", "SUCCESS, NONE", "PARTIAL_SUCCESS, GLANCING", "FAILURE, SOLID" })
	void defenderDegreeMapsToIncomingContact(DegreeOfSuccess degree, ContactQuality contact) {
		assertThat(ContactRules.incomingContact(degree)).isEqualTo(contact);
	}
}
