package com.leeburke.springgame.action.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.Suitability;

class ResolutionRulesTest {

	@ParameterizedTest
	@CsvSource({ "SLASH, AGILITY", "THRUST, AGILITY", "HOOK, AGILITY", "SMASH, MIGHT", "POMMEL_STRIKE, MIGHT",
			"PROJECT, ARCANA" })
	void attackStatFollowsTheMethod(WeaponMethod method, StatType stat) {
		assertThat(ResolutionRules.attackStat(method)).isEqualTo(stat);
	}

	@ParameterizedTest
	@CsvSource({ "EVADE, AGILITY", "PARRY, AGILITY", "TAKE_COVER, AGILITY", "BLOCK, MIGHT", "BRACE, MIGHT" })
	void defenseStatFollowsTheMethod(DefenseMethod method, StatType stat) {
		assertThat(ResolutionRules.defenseStat(method)).isEqualTo(stat);
	}

	@ParameterizedTest
	@CsvSource({ "CRITICAL_SUCCESS, SUCCESS", "SUCCESS, SUCCESS", "PARTIAL_SUCCESS, PARTIAL", "FAILURE, FAILURE" })
	void degreeMapsToStepSuccess(DegreeOfSuccess degree, StepSuccess success) {
		assertThat(ResolutionRules.successFor(degree)).isEqualTo(success);
	}

	@Test
	void baselinesAreNeutral() {
		assertThat(ResolutionRules.BASELINE_SUITABILITY).isEqualTo(Suitability.FAIR);
		assertThat(ResolutionRules.ATTACK_FORM_BASELINE).isZero();
		assertThat(ResolutionRules.PLAYER_PROTECTION).isZero();
		assertThat(ResolutionRules.PLAYER_TRAUMA_PROTECTION).isZero();
		assertThat(ResolutionRules.DEFENSIVE_MITIGATION_BASELINE).isZero();
	}
}
