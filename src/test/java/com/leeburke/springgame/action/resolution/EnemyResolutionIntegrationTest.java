package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ATTACK;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.SWORD_DAMAGE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyteAt;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.attack;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.defending;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.evade;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.enemy.EnemyCombatant;
import com.leeburke.springgame.enemy.EnemyFixtures;
import com.leeburke.springgame.enemy.EnemyTargetProfiles;
import com.leeburke.springgame.enemy.ScriptedRandom;
import com.leeburke.springgame.enemy.behavior.EnemyBehavior;
import com.leeburke.springgame.enemy.behavior.EnemyDecision;
import com.leeburke.springgame.enemy.behavior.EnemyDecisionContext;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageCalculator;
import com.leeburke.springgame.mechanics.DamageRequest;
import com.leeburke.springgame.mechanics.Effectiveness;

/**
 * Stage 12 feeding Stage 11 unchanged: enemy profiles for player attacks, and enemy-chosen attacks
 * for player defenses. The fixture scene's acolyte_1 is a HOLLOW_ACOLYTE, matching the enemy below
 * (AGILITY 9, so defense DC 13 and slash difficulty 13). The player slashes and evades with +1.
 */
class EnemyResolutionIntegrationTest {

	private final Setup setup = new Setup();

	private void useEnemyProfiles(EnemyCombatant enemy) {
		setup.profiles.clear();
		setup.profiles.putAll(EnemyTargetProfiles.profiles(enemy));
	}

	@Test
	void playerAttackResolvesAgainstTheEnemysProfile() {
		useEnemyProfiles(EnemyFixtures.combatant(EnemyFixtures.acolyte()));

		StepOutcome hit = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyte())), new FixedRolls(12)).steps().getFirst();
		StepResult.AttackResult result = (StepResult.AttackResult) hit.result().orElseThrow();

		assertThat(hit.check().orElseThrow().baseDc()).isEqualTo(13);
		assertThat(result.contact()).isEqualTo(ContactQuality.SOLID);
		assertThat(result.damage()).isEqualTo(new DamageCalculator()
				.calculate(new DamageRequest(SWORD_DAMAGE, ContactQuality.SOLID, Effectiveness.NORMAL, 0)));
		assertThat(hit.effects()).containsExactly(new OutcomeEffect.TargetDamaged("acolyte_1", result.damage().finalDamage(),
				java.util.Optional.empty(), result.trauma().impactSeverity()));

		StepOutcome miss = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyte())), new FixedRolls(7)).steps().getFirst();
		assertThat(miss.check().orElseThrow().degree()).isNotEqualTo(com.leeburke.springgame.mechanics.DegreeOfSuccess.SUCCESS);
	}

	@Test
	void aimedAttackUsesTheTargetedPartsCurrentSeverity() {
		useEnemyProfiles(EnemyFixtures.combatant(
				EnemyFixtures.withPart(EnemyFixtures.acolyte(), BodyPart.HEAD, BodySeverity.INJURED)));

		StepOutcome head = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.HEAD))), new FixedRolls(12))
				.steps().getFirst();
		StepOutcome leg = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.LEFT_LEG))), new FixedRolls(12))
				.steps().getFirst();

		assertThat(((StepResult.AttackResult) head.result().orElseThrow()).trauma().request().existingInjuryModifier())
				.isEqualTo(1);
		assertThat(((StepResult.AttackResult) leg.result().orElseThrow()).trauma().request().existingInjuryModifier())
				.isZero();
		assertThat(head.check().orElseThrow().baseDc()).isEqualTo(13);
	}

	@Test
	void attackOnADestroyedPartIsUnavailableWithoutRolling() {
		useEnemyProfiles(EnemyFixtures.combatant(
				EnemyFixtures.withPart(EnemyFixtures.acolyte(), BodyPart.HEAD, BodySeverity.DESTROYED)));
		FixedRolls rolls = new FixedRolls();

		ResolvedOutcome outcome = setup.resolve(intent(attack(WeaponMethod.SLASH, acolyteAt(BodyPart.HEAD))), rolls);

		assertThat(outcome.steps().getFirst().unavailable()).contains(UnavailableReason.MISSING_TARGET_PROFILE);
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void enemyChoosesTheAttackAndStageElevenResolvesTheDefense() {
		EnemyCombatant enemy = EnemyFixtures.combatant(EnemyFixtures.acolyte());
		// Acolyte weights: quick slash 55, stab 45, hold 0. A draw of 0 picks the quick slash.
		EnemyDecision decision = new EnemyBehavior().decide(new EnemyDecisionContext(enemy, List.of(), ATTACK),
				new ScriptedRandom(0));
		IncomingAttack incoming = ((EnemyDecision.Attack) decision).attack();
		setup.attacks.put(ATTACK, incoming);

		ResolvedOutcome evaded = setup.resolve(defending(evade()), new FixedRolls(12));
		StepOutcome step = evaded.steps().getFirst();

		assertThat(((EnemyDecision.Attack) decision).optionCode()).isEqualTo("ACOLYTE_QUICK_SLASH");
		assertThat(step.check().orElseThrow().baseDc()).isEqualTo(13);
		assertThat(((StepResult.DefenseResult) step.result().orElseThrow()).incomingContact()).isEqualTo(ContactQuality.NONE);

		ResolvedOutcome failed = setup.resolve(defending(evade()), new FixedRolls(2));
		StepResult.DefenseResult hit = (StepResult.DefenseResult) failed.steps().getFirst().result().orElseThrow();
		assertThat(hit.incomingContact()).isEqualTo(ContactQuality.SOLID);
		assertThat(hit.damage().baseDamage()).isEqualTo(EnemyFixtures.CONTENT.findWeapon("DAGGER").orElseThrow().baseDamage());
	}
}
