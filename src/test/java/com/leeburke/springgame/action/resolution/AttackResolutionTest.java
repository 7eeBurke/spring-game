package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ACOLYTE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ACOLYTE_PROFILE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.SWORD;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.SWORD_DAMAGE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.SWORD_TRAUMA;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyteAt;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.attack;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.slash;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.CheckResult;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageCalculator;
import com.leeburke.springgame.mechanics.DamageRequest;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.Suitability;
import com.leeburke.springgame.mechanics.TraumaCalculator;
import com.leeburke.springgame.mechanics.TraumaRequest;

/** The acolyte's whole-body profile: DC 12, NORMAL effectiveness, protection 1. Slashing uses AGILITY 7 (+1). */
class AttackResolutionTest {

	private final Setup setup = new Setup();

	private StepOutcome slashWith(int face) {
		return setup.resolve(intent(slash()), new FixedRolls(face)).steps().getFirst();
	}

	private static StepResult.AttackResult attackResult(StepOutcome step) {
		return (StepResult.AttackResult) step.result().orElseThrow();
	}

	@ParameterizedTest
	@CsvSource({
			"16, CRITICAL_SUCCESS, CLEAN, SUCCESS",
			"11, SUCCESS, SOLID, SUCCESS",
			"7, PARTIAL_SUCCESS, GLANCING, PARTIAL",
			"6, FAILURE, NONE, FAILURE" })
	void rollDecidesDegreeContactAndStepSuccess(int face, DegreeOfSuccess degree, ContactQuality contact, StepSuccess success) {
		StepOutcome step = slashWith(face);

		assertThat(step.status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(step.check().orElseThrow().degree()).isEqualTo(degree);
		assertThat(attackResult(step).contact()).isEqualTo(contact);
		assertThat(step.success()).contains(success);
	}

	@Test
	void checkKeepsTheStageThreeBreakdown() {
		CheckResult check = slashWith(11).check().orElseThrow();

		assertThat(check.stat()).isEqualTo(StatType.AGILITY);
		assertThat(check.statModifier()).isEqualTo(1);
		assertThat(check.rawRoll()).isEqualTo(11);
		assertThat(check.rollTotal()).isEqualTo(12);
		assertThat(check.baseDc()).isEqualTo(12);
		assertThat(check.suitability()).contains(Suitability.FAIR);
		assertThat(check.finalDc()).isEqualTo(12);
		assertThat(check.margin()).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "16, CLEAN", "11, SOLID", "7, GLANCING", "6, NONE" })
	void damageAndTraumaEqualTheStageFourCalculators(int face, ContactQuality contact) {
		StepResult.AttackResult result = attackResult(slashWith(face));

		assertThat(result.damage()).isEqualTo(new DamageCalculator().calculate(
				new DamageRequest(SWORD_DAMAGE, contact, ACOLYTE_PROFILE.effectiveness(), ACOLYTE_PROFILE.protection())));
		assertThat(result.trauma()).isEqualTo(new TraumaCalculator().calculate(new TraumaRequest(SWORD_TRAUMA, contact,
				ACOLYTE_PROFILE.existingInjuryModifier(), ACOLYTE_PROFILE.anatomyInteractionModifier(),
				ResolutionRules.ATTACK_FORM_BASELINE, ACOLYTE_PROFILE.traumaProtection(),
				ACOLYTE_PROFILE.defensiveMitigation())));
		assertThat(result.weaponRef()).isEqualTo(SWORD);
		assertThat(result.weaponCode()).isEqualTo("LONGSWORD");
	}

	@Test
	void hitProducesTargetDamagedWithTheCalculatedValues() {
		StepOutcome step = slashWith(16);
		StepResult.AttackResult result = attackResult(step);

		assertThat(step.effects()).containsExactly(new OutcomeEffect.TargetDamaged(ACOLYTE, result.damage().finalDamage(),
				Optional.empty(), result.trauma().impactSeverity()));
		assertThat(result.damage().finalDamage()).isPositive();
	}

	@Test
	void missDealsNoDamageAndProducesNoEffect() {
		StepOutcome step = slashWith(6);

		assertThat(attackResult(step).damage().finalDamage()).isZero();
		assertThat(attackResult(step).trauma().impactSeverity()).isEmpty();
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void profileValuesDriveTheCalculation() {
		setup.profiles.put(TargetProfileKey.wholeTarget(ACOLYTE),
				new TargetCombatProfile(12, Effectiveness.HIGH, 3, 2, 2, 1, 1));
		StepResult.AttackResult result = attackResult(slashWith(11));

		assertThat(result.damage().baseDamage()).isEqualTo(SWORD_DAMAGE);
		assertThat(result.damage().contactQuality()).isEqualTo(ContactQuality.SOLID);
		assertThat(result.damage().effectiveness()).isEqualTo(Effectiveness.HIGH);
		assertThat(result.damage().protection()).isEqualTo(3);
		assertThat(result.trauma().request()).isEqualTo(new TraumaRequest(SWORD_TRAUMA, ContactQuality.SOLID, 2, 1, 0, 2, 1));
	}

	@Test
	void naturalOneIsNotSpecial() {
		setup.profiles.put(TargetProfileKey.wholeTarget(ACOLYTE), new TargetCombatProfile(2, Effectiveness.NORMAL, 0, 0, 0, 0, 0));
		StepOutcome step = setup.resolve(intent(attack(WeaponMethod.SMASH, acolyte())), new FixedRolls(1)).steps().getFirst();

		assertThat(step.check().orElseThrow().degree()).isEqualTo(DegreeOfSuccess.SUCCESS);
	}

	@Test
	void statComesFromTheMethod() {
		CheckResult smash = setup.resolve(intent(attack(WeaponMethod.SMASH, acolyte())), new FixedRolls(10))
				.steps().getFirst().check().orElseThrow();
		CheckResult project = setup.resolve(intent(attack(WeaponMethod.PROJECT, acolyte())), new FixedRolls(10))
				.steps().getFirst().check().orElseThrow();

		assertThat(smash.stat()).isEqualTo(StatType.MIGHT);
		assertThat(smash.statModifier()).isEqualTo(2);
		assertThat(project.stat()).isEqualTo(StatType.ARCANA);
		assertThat(project.statModifier()).isEqualTo(-1);
	}

	@Test
	void namedBodyPartIsKeptAndNoneIsInventedOtherwise() {
		setup.profiles.put(TargetProfileKey.at(ACOLYTE, BodyPart.HEAD), ACOLYTE_PROFILE);
		StepOutcome aimed = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.HEAD))), new FixedRolls(11))
				.steps().getFirst();
		StepOutcome plain = slashWith(11);

		assertThat(attackResult(aimed).targetedBodyPart()).contains(BodyPart.HEAD);
		assertThat(((OutcomeEffect.TargetDamaged) aimed.effects().getFirst()).bodyPart()).contains(BodyPart.HEAD);
		assertThat(attackResult(plain).targetedBodyPart()).isEmpty();
		assertThat(((OutcomeEffect.TargetDamaged) plain.effects().getFirst()).bodyPart()).isEmpty();
	}

	@Test
	void bodyPartProfilesAreDistinct() {
		setup.profiles.put(TargetProfileKey.at(ACOLYTE, BodyPart.HEAD), new TargetCombatProfile(15, Effectiveness.HIGH, 0, 0, 0, 2, 0));
		setup.profiles.put(TargetProfileKey.at(ACOLYTE, BodyPart.LEFT_LEG),
				new TargetCombatProfile(10, Effectiveness.LOW, 2, 1, 0, 0, 0));

		StepOutcome head = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.HEAD))), new FixedRolls(14))
				.steps().getFirst();
		StepOutcome leg = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.LEFT_LEG))), new FixedRolls(14))
				.steps().getFirst();

		assertThat(head.check().orElseThrow().baseDc()).isEqualTo(15);
		assertThat(leg.check().orElseThrow().baseDc()).isEqualTo(10);
		assertThat(attackResult(head).damage().effectiveness()).isEqualTo(Effectiveness.HIGH);
		assertThat(attackResult(leg).damage().effectiveness()).isEqualTo(Effectiveness.LOW);
		assertThat(attackResult(leg).damage().protection()).isEqualTo(2);
		assertThat(attackResult(head).trauma().request().anatomyInteractionModifier()).isEqualTo(2);
	}

	@Test
	void aimedAttackWithOnlyAWholeBodyProfileIsUnavailableWithoutRolling() {
		FixedRolls rolls = new FixedRolls();
		ResolvedOutcome outcome = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.HEAD))), rolls);

		assertUnavailable(outcome, UnavailableReason.MISSING_TARGET_PROFILE, rolls);
	}

	@Test
	void untargetedAttackWithOnlyAPartProfileIsUnavailable() {
		setup.profiles.clear();
		setup.profiles.put(TargetProfileKey.at(ACOLYTE, BodyPart.HEAD), ACOLYTE_PROFILE);
		FixedRolls rolls = new FixedRolls();

		assertUnavailable(setup.resolve(intent(slash()), rolls), UnavailableReason.MISSING_TARGET_PROFILE, rolls);
	}

	@Test
	void objectAndHazardTargetsAreNotImplemented() {
		FixedRolls rolls = new FixedRolls();
		AttackPayload pew = attack(WeaponMethod.SMASH, new ActionTarget.ObjectTarget("pew_1", TargetSpecificity.EXPLICIT));
		AttackPayload fire = attack(WeaponMethod.SLASH, new ActionTarget.HazardTarget("fire_1", TargetSpecificity.EXPLICIT));

		assertUnavailable(setup.resolve(intent(pew), rolls), UnavailableReason.ACTION_NOT_IMPLEMENTED, rolls);
		assertUnavailable(setup.resolve(intent(fire), rolls), UnavailableReason.ACTION_NOT_IMPLEMENTED, rolls);
	}

	@Test
	void nonDamagePurposeIsNotImplemented() {
		FixedRolls rolls = new FixedRolls();
		AttackPayload trip = new AttackPayload(SWORD, WeaponMethod.HOOK, AttackTemplate.LOW_SWEEP, acolyte(),
				ActionApproach.NORMAL, AttackPurpose.TRIP);

		assertUnavailable(setup.resolve(intent(trip), rolls), UnavailableReason.ACTION_NOT_IMPLEMENTED, rolls);
	}

	private static void assertUnavailable(ResolvedOutcome outcome, UnavailableReason reason, FixedRolls rolls) {
		StepOutcome step = outcome.steps().getFirst();
		assertThat(step.status()).isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
		assertThat(step.unavailable()).contains(reason);
		assertThat(step.effects()).isEmpty();
		assertThat(outcome.overall()).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
		assertThat(outcome.metadata().rollsConsumed()).isZero();
		assertThat(rolls.drawn()).isZero();
	}
}
