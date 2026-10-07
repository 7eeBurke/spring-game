package com.leeburke.springgame.content.enemy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.StatValue;

/** Rules each definition enforces on its own; cross-references are covered by the loader test. */
class EnemyDefinitionTest {

	private static final List<StatType> PRIORITY = List.of(StatType.AGILITY, StatType.PERCEPTION, StatType.MIGHT,
			StatType.RESOLVE, StatType.ARCANA);
	private static final Map<StatType, Integer> FIXED = Map.of(StatType.MIGHT, 10, StatType.AGILITY, 9,
			StatType.RESOLVE, 8, StatType.PERCEPTION, 5, StatType.ARCANA, 4);
	private static final EnemyAttackOption SLASH = new EnemyAttackOption("TEST_SLASH", WeaponMethod.SLASH,
			AttackTemplate.QUICK_SLASH, 10);

	private static EnemyDefinition define(List<StatType> priority, Map<StatType, Integer> fixed, int baseHp,
			List<EnemyTrait> traits, int holdWeight, List<EnemyAttackOption> attacks) {
		return new EnemyDefinition("HOLLOW_ACOLYTE", "HUMANOID", "DAGGER", priority, fixed, baseHp, StatType.AGILITY,
				traits, holdWeight, attacks);
	}

	private static EnemyDefinition valid() {
		return define(PRIORITY, Map.of(), 12, List.of(), 0, List.of(SLASH));
	}

	@Test
	void validDefinitionExposesItsRules() {
		assertThat(valid().statRule()).isEqualTo(new EnemyStatRule.ShapePriority(PRIORITY));
		assertThat(valid().findAttack("TEST_SLASH")).contains(SLASH);
		assertThat(valid().findAttack("HOLD")).isEmpty();
	}

	@Test
	void exactlyOneStatRuleIsRequired() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, FIXED, 12, List.of(), 0, List.of(SLASH)));
		assertThatIllegalArgumentException().isThrownBy(() -> define(List.of(), Map.of(), 12, List.of(), 0, List.of(SLASH)));
	}

	@Test
	void priorityMustNameEachStatOnce() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(List.of(StatType.AGILITY, StatType.AGILITY,
				StatType.MIGHT, StatType.RESOLVE, StatType.ARCANA), Map.of(), 12, List.of(), 0, List.of(SLASH)));
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY.subList(0, 4), Map.of(), 12, List.of(), 0,
				List.of(SLASH)));
	}

	@Test
	void fixedStatsMustBeCompleteAndInRange() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(List.of(), Map.of(StatType.MIGHT, 10), 12, List.of(), 0,
				List.of(SLASH)));
		Map<StatType, Integer> tooHigh = Map.of(StatType.MIGHT, 11, StatType.AGILITY, 9, StatType.RESOLVE, 8,
				StatType.PERCEPTION, 5, StatType.ARCANA, 4);
		assertThatIllegalArgumentException().isThrownBy(() -> define(List.of(), tooHigh, 12, List.of(), 0, List.of(SLASH)));
	}

	@Test
	void baseHpMustKeepMaxHpPositiveForTheWeakestResolve() {
		// Shape-priority enemies can roll RESOLVE 3: base 3 would give 0.
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 3, List.of(), 0, List.of(SLASH)));
		assertThat(define(PRIORITY, Map.of(), 4, List.of(), 0, List.of(SLASH)).maxHpAt(new StatValue(3))).isEqualTo(1);
		// A fixed block only needs its own RESOLVE (8): base 1 gives 3.
		assertThat(define(List.of(), FIXED, 1, List.of(), 0, List.of(SLASH)).maxHpAt(new StatValue(8))).isEqualTo(3);
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 0, List.of(), 0, List.of(SLASH)));
	}

	@Test
	void maxHpIsBasePlusResolveOffset() {
		assertThat(valid().maxHpAt(new StatValue(6))).isEqualTo(12);
		assertThat(valid().maxHpAt(new StatValue(10))).isEqualTo(16);
		assertThat(valid().maxHpAt(new StatValue(3))).isEqualTo(9);
	}

	@Test
	void hpArithmeticIsOverflowChecked() {
		EnemyDefinition huge = define(PRIORITY, Map.of(), Integer.MAX_VALUE, List.of(), 0, List.of(SLASH));
		assertThat(huge.maxHpAt(new StatValue(6))).isEqualTo(Integer.MAX_VALUE);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> huge.maxHpAt(new StatValue(7)))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void traitsAreUniqueAndAdaptiveExcludesRelentless() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 12,
				List.of(EnemyTrait.CAUTIOUS, EnemyTrait.CAUTIOUS), 0, List.of(SLASH)));
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 12,
				List.of(EnemyTrait.ADAPTIVE, EnemyTrait.RELENTLESS), 0, List.of(SLASH)));
	}

	@Test
	void holdWeightCannotBeNegative() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 12, List.of(), -1, List.of(SLASH)));
	}

	@Test
	void attacksAreRequiredAndUnique() {
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 12, List.of(), 0, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> define(PRIORITY, Map.of(), 12, List.of(), 0,
				List.of(SLASH, new EnemyAttackOption("TEST_SLASH", WeaponMethod.THRUST, AttackTemplate.THRUST, 5))));
	}

	@Test
	void attackOptionsRejectBadCodesAndWeights() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyAttackOption("HOLD", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH, 10));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyAttackOption("quick slash", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH, 10));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new EnemyAttackOption("TEST_SLASH", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH, 0));
	}

	@Test
	void anatomyRejectsDuplicateAndEmptyParts() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AnatomyDefinition("HUMANOID", List.of()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new AnatomyDefinition("HUMANOID", List.of(BodyPart.HEAD, BodyPart.HEAD)));
		assertThat(new AnatomyDefinition("HEADLESS", List.of(BodyPart.CHEST)).has(BodyPart.HEAD)).isFalse();
	}
}
