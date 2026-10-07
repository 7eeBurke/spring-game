package com.leeburke.springgame.enemy;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.ENEMIES;
import static com.leeburke.springgame.enemy.EnemyFixtures.acolyte;
import static com.leeburke.springgame.enemy.EnemyFixtures.combatant;
import static com.leeburke.springgame.enemy.EnemyFixtures.definition;
import static com.leeburke.springgame.enemy.EnemyFixtures.guardian;
import static com.leeburke.springgame.enemy.EnemyFixtures.penitent;
import static com.leeburke.springgame.enemy.EnemyFixtures.warden;
import static com.leeburke.springgame.enemy.EnemyFixtures.withPart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.TargetCombatProfile;
import com.leeburke.springgame.action.resolution.TargetProfileKey;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.content.enemy.AnatomyDefinition;
import com.leeburke.springgame.content.enemy.EnemyAttackOption;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.Effectiveness;

class EnemyCombatProfilesTest {

	// --- Coherence ---

	@Test
	void combatantAcceptsMatchingParts() {
		EnemyCombatant acolyte = combatant(acolyte());
		assertThat(acolyte.definition().code()).isEqualTo("HOLLOW_ACOLYTE");
		assertThat(acolyte.anatomy().code()).isEqualTo("HUMANOID");
		assertThat(acolyte.weapon().code()).isEqualTo("DAGGER");
	}

	@Test
	void combatantRejectsAnotherEnemysDefinition() {
		EnemyInstance acolyte = acolyte();
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyCombatant(acolyte, definition("BONE_WARDEN"),
				ENEMIES.findAnatomy("HUMANOID").orElseThrow(), CONTENT.findWeapon("DAGGER").orElseThrow()));
	}

	@Test
	void combatantRejectsTheWrongWeapon() {
		EnemyInstance acolyte = acolyte();
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyCombatant(acolyte, definition("HOLLOW_ACOLYTE"),
				ENEMIES.findAnatomy("HUMANOID").orElseThrow(), CONTENT.findWeapon("LONGSWORD").orElseThrow()));
	}

	@Test
	void combatantRejectsAnotherAnatomyOrAMismatchedBody() {
		EnemyInstance acolyte = acolyte();
		AnatomyDefinition other = new AnatomyDefinition("SERPENT", List.of(BodyPart.HEAD, BodyPart.CHEST));
		assertThatIllegalArgumentException().isThrownBy(() -> new EnemyCombatant(acolyte, definition("HOLLOW_ACOLYTE"),
				other, CONTENT.findWeapon("DAGGER").orElseThrow()));

		Map<BodyPart, BodySeverity> noHeart = new EnumMap<>(acolyte.body().severities());
		noHeart.remove(BodyPart.HEART);
		EnemyInstance heartless = new EnemyInstance("acolyte_1", "HOLLOW_ACOLYTE", acolyte.stats(), acolyte.maxHp(),
				acolyte.currentHp(), new EnemyBody(noHeart), "DAGGER");
		assertThatIllegalArgumentException().isThrownBy(() -> combatant(heartless));
	}

	@Test
	void instanceRejectsImpossibleHp() {
		EnemyInstance a = acolyte();
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyInstance("a", a.definitionCode(), a.stats(), 0, 0, a.body(), a.weaponCode()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyInstance("a", a.definitionCode(), a.stats(), 9, 10, a.body(), a.weaponCode()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyInstance("a", a.definitionCode(), a.stats(), 9, -1, a.body(), a.weaponCode()));
		assertThat(EnemyFixtures.withHp(a, 0).currentHp()).isZero();
	}

	// --- Target profiles ---

	@ParameterizedTest
	@CsvSource({ "acolyte, 13", "warden, 14", "penitent, 7", "guardian, 14" })
	void defenseDcIsTenPlusTheDefensiveStatModifier(String who, int dc) {
		EnemyInstance enemy = switch (who) {
			case "acolyte" -> acolyte();
			case "warden" -> warden();
			case "penitent" -> penitent();
			default -> guardian();
		};
		assertThat(EnemyTargetProfiles.profile(combatant(enemy), Optional.empty()).orElseThrow().defenseDc()).isEqualTo(dc);
	}

	@Test
	void untargetedProfileUsesBaselinesAndNoInjury() {
		EnemyInstance hurt = withPart(acolyte(), BodyPart.HEAD, BodySeverity.CRIPPLED);
		assertThat(EnemyTargetProfiles.profile(combatant(hurt), Optional.empty()))
				.contains(new TargetCombatProfile(13, Effectiveness.NORMAL, 0, 0, 0, 0, 0));
	}

	@ParameterizedTest
	@CsvSource({ "HEALTHY, 0", "INJURED, 1", "WOUNDED, 2", "CRIPPLED, 3" })
	void targetedProfileUsesThatPartsSeverity(BodySeverity severity, int modifier) {
		EnemyInstance enemy = withPart(acolyte(), BodyPart.LEFT_LEG, severity);
		assertThat(EnemyTargetProfiles.profile(combatant(enemy), Optional.of(BodyPart.LEFT_LEG)))
				.contains(new TargetCombatProfile(13, Effectiveness.NORMAL, 0, 0, modifier, 0, 0));
		assertThat(EnemyTargetProfiles.profile(combatant(enemy), Optional.of(BodyPart.HEAD)).orElseThrow()
				.existingInjuryModifier()).isZero();
	}

	@Test
	void destroyedPartHasNoProfileAndNoFallback() {
		EnemyCombatant enemy = combatant(withPart(acolyte(), BodyPart.RIGHT_ARM, BodySeverity.DESTROYED));
		assertThat(EnemyTargetProfiles.profile(enemy, Optional.of(BodyPart.RIGHT_ARM))).isEmpty();

		Map<TargetProfileKey, TargetCombatProfile> profiles = EnemyTargetProfiles.profiles(enemy);
		assertThat(profiles).containsKey(TargetProfileKey.wholeTarget("acolyte_1"))
				.doesNotContainKey(TargetProfileKey.at("acolyte_1", BodyPart.RIGHT_ARM))
				.hasSize(1 + BodyPart.values().length - 1);
	}

	@Test
	void partMissingFromTheAnatomyHasNoProfile() {
		AnatomyDefinition serpent = new AnatomyDefinition("HUMANOID", List.of(BodyPart.HEAD, BodyPart.CHEST));
		EnemyInstance base = acolyte();
		EnemyInstance limbless = new EnemyInstance("acolyte_1", "HOLLOW_ACOLYTE", base.stats(), base.maxHp(), base.maxHp(),
				EnemyBody.healthy(serpent), "DAGGER");
		EnemyCombatant enemy = new EnemyCombatant(limbless, definition("HOLLOW_ACOLYTE"), serpent,
				CONTENT.findWeapon("DAGGER").orElseThrow());

		assertThat(EnemyTargetProfiles.profile(enemy, Optional.of(BodyPart.LEFT_LEG))).isEmpty();
		assertThat(EnemyTargetProfiles.profiles(enemy)).hasSize(3);
	}

	// --- Incoming attacks ---

	@Test
	void incomingAttackUsesWeaponValuesAndMethodStat() {
		EnemyCombatant penitent = combatant(penitent());
		EnemyAttackOption bolt = penitent.definition().findAttack("PENITENT_EMBER_BOLT").orElseThrow();
		EnemyAttackOption rod = penitent.definition().findAttack("PENITENT_ROD_STRIKE").orElseThrow();
		WeaponDefinition emberRod = CONTENT.findWeapon("EMBER_ROD").orElseThrow();

		assertThat(EnemyAttacks.build("attack_1", penitent, bolt)).isEqualTo(new IncomingAttack("attack_1", "penitent_1",
				AttackTemplate.PROJECTED_ATTACK, 13, emberRod.baseDamage(), emberRod.trauma(), Effectiveness.NORMAL, 0, 0,
				Optional.empty()));
		// The rod strike is a SMASH, so it uses the penitent's weak MIGHT 3 (-3).
		assertThat(EnemyAttacks.build("attack_1", penitent, rod).difficulty()).isEqualTo(7);
	}

	@Test
	void guardianSwordAttacksUseItsAgility() {
		EnemyCombatant guardian = combatant(guardian());
		for (EnemyAttackOption option : guardian.definition().attacks()) {
			assertThat(EnemyAttacks.build("attack_1", guardian, option).difficulty()).as(option.code()).isEqualTo(13);
		}
	}

	@Test
	void attackOptionMustBelongToTheEnemy() {
		EnemyAttackOption foreign = definition("BONE_WARDEN").attacks().getFirst();
		EnemyAttackOption lookalike = new EnemyAttackOption("ACOLYTE_STAB", WeaponMethod.SMASH, AttackTemplate.HEAVY_SMASH, 45);
		assertThatIllegalArgumentException().isThrownBy(() -> EnemyAttacks.build("attack_1", combatant(acolyte()), foreign));
		assertThatIllegalArgumentException().isThrownBy(() -> EnemyAttacks.build("attack_1", combatant(acolyte()), lookalike));
	}

	@Test
	void attackReferenceMustBeClean() {
		EnemyCombatant acolyte = combatant(acolyte());
		EnemyAttackOption stab = acolyte.definition().attacks().getLast();
		assertThatIllegalArgumentException().isThrownBy(() -> EnemyAttacks.build(" attack_1", acolyte, stab));
		assertThatIllegalArgumentException().isThrownBy(() -> EnemyAttacks.build("", acolyte, stab));
	}
}
