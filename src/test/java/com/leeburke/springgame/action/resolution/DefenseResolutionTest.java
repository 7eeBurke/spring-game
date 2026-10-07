package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ATTACK;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.INCOMING;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.bodyWith;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.defend;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.defending;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.evade;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.player;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.related;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.slash;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.CheckResult;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageCalculator;
import com.leeburke.springgame.mechanics.DamageRequest;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.Suitability;
import com.leeburke.springgame.mechanics.TraumaCalculator;
import com.leeburke.springgame.mechanics.TraumaRequest;

/** The incoming attack: difficulty 12, 5 damage, 3 trauma, NORMAL effectiveness. Evading uses AGILITY 7 (+1). */
class DefenseResolutionTest {

	private final Setup setup = new Setup();

	private StepOutcome evadeWith(int face) {
		return setup.resolve(defending(evade()), new FixedRolls(face)).steps().getFirst();
	}

	private static StepResult.DefenseResult defenseResult(StepOutcome step) {
		return (StepResult.DefenseResult) step.result().orElseThrow();
	}

	@ParameterizedTest
	@CsvSource({
			"16, NONE, SUCCESS",
			"11, NONE, SUCCESS",
			"10, GLANCING, PARTIAL",
			"7, GLANCING, PARTIAL",
			"6, SOLID, FAILURE" })
	void defenderDegreeDecidesIncomingContact(int face, ContactQuality contact, StepSuccess success) {
		StepOutcome step = evadeWith(face);

		assertThat(step.status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(defenseResult(step).incomingContact()).isEqualTo(contact);
		assertThat(defenseResult(step).attackRef()).isEqualTo(ATTACK);
		assertThat(step.success()).contains(success);
	}

	@Test
	void dcIsTheAttackDifficultyAndStatComesFromTheMethod() {
		CheckResult evaded = evadeWith(10).check().orElseThrow();
		CheckResult blocked = setup.resolve(defending(defend(DefenseMethod.BLOCK)), new FixedRolls(10))
				.steps().getFirst().check().orElseThrow();

		assertThat(evaded.baseDc()).isEqualTo(INCOMING.difficulty());
		assertThat(evaded.suitability()).contains(Suitability.FAIR);
		assertThat(evaded.stat()).isEqualTo(StatType.AGILITY);
		assertThat(blocked.stat()).isEqualTo(StatType.MIGHT);
		assertThat(blocked.statModifier()).isEqualTo(2);
	}

	@ParameterizedTest
	@CsvSource({ "11, NONE", "7, GLANCING", "6, SOLID" })
	void damageAndTraumaEqualTheStageFourCalculatorsWithZeroPlayerProtection(int face, ContactQuality contact) {
		StepResult.DefenseResult result = defenseResult(evadeWith(face));

		assertThat(result.damage()).isEqualTo(new DamageCalculator().calculate(
				new DamageRequest(INCOMING.baseDamage(), contact, INCOMING.effectiveness(), 0)));
		assertThat(result.trauma()).isEqualTo(new TraumaCalculator().calculate(
				new TraumaRequest(INCOMING.weaponTrauma(), contact, 0, 0, 0, 0, 0)));
	}

	@Test
	void failedDefenseProducesPlayerDamaged() {
		StepOutcome step = evadeWith(6);
		StepResult.DefenseResult result = defenseResult(step);

		assertThat(step.effects()).containsExactly(new OutcomeEffect.PlayerDamaged(result.damage().finalDamage(),
				Optional.empty(), result.trauma().impactSeverity()));
		assertThat(result.damage().finalDamage()).isPositive();
	}

	@Test
	void successfulDefenseProducesNoEffect() {
		StepOutcome step = evadeWith(11);

		assertThat(defenseResult(step).damage().finalDamage()).isZero();
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void incomingAttackValuesAreUsedAsSupplied() {
		setup.attacks.put(ATTACK, new IncomingAttack(ATTACK, "acolyte_1", AttackTemplate.THRUST, 15, 8, 4,
				Effectiveness.HIGH, 2, 1, Optional.of(BodyPart.CHEST)));
		StepOutcome step = evadeWith(6);
		StepResult.DefenseResult result = defenseResult(step);

		assertThat(step.check().orElseThrow().baseDc()).isEqualTo(15);
		assertThat(result.damage().baseDamage()).isEqualTo(8);
		assertThat(result.damage().effectiveness()).isEqualTo(Effectiveness.HIGH);
		assertThat(result.trauma().request()).isEqualTo(new TraumaRequest(4, ContactQuality.SOLID, 0, 1, 2, 0, 0));
		assertThat(((OutcomeEffect.PlayerDamaged) step.effects().getFirst()).bodyPart()).contains(BodyPart.CHEST);
	}

	@Test
	void existingInjuryComesFromThePlayersTargetedBodyPart() {
		setup.attacks.put(ATTACK, attackAt(BodyPart.LEFT_ARM));
		setup.player = player(20, bodyWith(BodyPart.LEFT_ARM, BodySeverity.WOUNDED));

		assertThat(defenseResult(evadeWith(6)).trauma().request().existingInjuryModifier()).isEqualTo(2);
	}

	@Test
	void untargetedAttackHasNoExistingInjuryModifier() {
		setup.player = player(20, bodyWith(BodyPart.LEFT_ARM, BodySeverity.CRIPPLED));

		assertThat(defenseResult(evadeWith(6)).trauma().request().existingInjuryModifier()).isZero();
	}

	@Test
	void destroyedTargetedPartIsUnavailableWithoutRolling() {
		setup.attacks.put(ATTACK, attackAt(BodyPart.LEFT_ARM));
		setup.player = player(20, bodyWith(BodyPart.LEFT_ARM, BodySeverity.DESTROYED));
		FixedRolls rolls = new FixedRolls();
		ResolvedOutcome outcome = setup.resolve(defending(evade()), rolls);

		assertThat(outcome.steps().getFirst().unavailable()).contains(UnavailableReason.UNDEFINED_INJURY_MODIFIER);
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void defenseWithoutAnIncomingAttackIsUnavailable() {
		FixedRolls rolls = new FixedRolls();
		ResolvedOutcome outcome = setup.resolve(intent(evade()), rolls);

		assertThat(outcome.steps().getFirst().unavailable()).contains(UnavailableReason.NO_INCOMING_ATTACK);
		assertThat(outcome.overall()).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void secondDefenseAgainstAResolvedAttackIsUnavailable() {
		FixedRolls rolls = new FixedRolls(11);
		ResolvedOutcome outcome = setup.resolve(defending(evade(), defend(DefenseMethod.BLOCK)), rolls);

		assertThat(outcome.steps().get(0).status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(outcome.steps().get(1).unavailable()).contains(UnavailableReason.INCOMING_ATTACK_ALREADY_RESOLVED);
		assertThat(rolls.drawn()).isEqualTo(1);
	}

	@Test
	void unavailableDefenseDoesNotUseUpTheAttack() {
		ResolvedOutcome outcome = setup.resolve(related(Optional.of(ATTACK), List.of(StepRelation.WHILE, StepRelation.THEN),
				slash(), evade(), defend(DefenseMethod.PARRY)), new FixedRolls(11, 11));

		assertThat(outcome.steps().get(1).unavailable()).contains(UnavailableReason.SIMULTANEOUS_ACTION);
		assertThat(outcome.steps().get(2).status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(defenseResult(outcome.steps().get(2)).attackRef()).isEqualTo(ATTACK);
	}

	@Test
	void cancelledDefenseDoesNotUseUpTheAttack() {
		ResolvedOutcome outcome = setup.resolve(related(Optional.of(ATTACK),
				List.of(StepRelation.IF_PREVIOUS_SUCCEEDS, StepRelation.THEN), slash(), evade(), defend(DefenseMethod.BLOCK)),
				new FixedRolls(6, 11));

		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
		assertThat(outcome.steps().get(2).status()).isEqualTo(StepStatus.RESOLVED);
	}

	private static IncomingAttack attackAt(BodyPart part) {
		return new IncomingAttack(ATTACK, "acolyte_1", AttackTemplate.OVERHEAD_STRIKE, 12, 5, 3, Effectiveness.NORMAL, 0, 0,
				Optional.of(part));
	}
}
