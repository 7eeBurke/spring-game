package com.leeburke.springgame.content.enemy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.StatValue;
import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Static mechanical definition of an enemy kind, matching a world ENTITY code. References its
 * anatomy and weapon by code; weapon damage, trauma and type are never repeated here.
 * <p>
 * Exactly one of {@code statPriority} (a shape drawn per enemy, assigned along this order) and
 * {@code fixedStats} (an authored block) is non-empty. Cross-references are checked by
 * {@link EnemyCatalog}.
 *
 * @param baseHp     max HP before the Resolve adjustment; see {@link #maxHpAt(StatValue)}
 * @param holdWeight base behaviour weight of holding instead of attacking, at least 0
 */
public record EnemyDefinition(
		String code,
		String anatomy,
		String weapon,
		List<StatType> statPriority,
		Map<StatType, Integer> fixedStats,
		int baseHp,
		StatType defenseStat,
		List<EnemyTrait> traits,
		int holdWeight,
		List<EnemyAttackOption> attacks) {

	/** Resolve at which max HP equals base HP. */
	public static final int HP_RESOLVE_PIVOT = 6;

	public EnemyDefinition {
		DefinitionCodes.requireCode(code, "Enemy code");
		DefinitionCodes.requireCode(anatomy, "Enemy " + code + " anatomy code");
		DefinitionCodes.requireCode(weapon, "Enemy " + code + " weapon code");
		statPriority = List.copyOf(Objects.requireNonNull(statPriority, "statPriority"));
		fixedStats = Map.copyOf(Objects.requireNonNull(fixedStats, "fixedStats"));
		Objects.requireNonNull(defenseStat, "defenseStat");
		traits = List.copyOf(Objects.requireNonNull(traits, "traits"));
		attacks = List.copyOf(Objects.requireNonNull(attacks, "attacks"));

		if (statPriority.isEmpty() == fixedStats.isEmpty()) {
			throw new IllegalArgumentException("Enemy " + code + " needs exactly one of statPriority and fixedStats");
		}
		if (!fixedStats.isEmpty() && fixedStats.size() != StatType.values().length) {
			throw new IllegalArgumentException("Enemy " + code + " fixedStats must give all five stats");
		}
		EnemyStatRule rule = toRule(statPriority, fixedStats);
		if (baseHp < 1) {
			throw new IllegalArgumentException("Enemy " + code + " base HP must be at least 1, but was " + baseHp);
		}
		StatValue weakestResolve = rule instanceof EnemyStatRule.Fixed fixed
				? fixed.stats().resolve()
				: new StatValue(StatValue.MIN);
		if (maxHp(baseHp, weakestResolve) < 1) {
			throw new IllegalArgumentException("Enemy " + code + " base HP " + baseHp + " can produce max HP below 1");
		}
		if (!traits.isEmpty() && EnumSet.copyOf(traits).size() != traits.size()) {
			throw new IllegalArgumentException("Enemy " + code + " lists a trait more than once");
		}
		if (traits.contains(EnemyTrait.ADAPTIVE) && traits.contains(EnemyTrait.RELENTLESS)) {
			throw new IllegalArgumentException("Enemy " + code + " cannot be both ADAPTIVE and RELENTLESS");
		}
		if (holdWeight < 0) {
			throw new IllegalArgumentException("Enemy " + code + " hold weight cannot be negative, but was " + holdWeight);
		}
		if (attacks.isEmpty()) {
			throw new IllegalArgumentException("Enemy " + code + " needs at least one attack option");
		}
		Set<String> optionCodes = new HashSet<>();
		for (EnemyAttackOption option : attacks) {
			if (!optionCodes.add(option.code())) {
				throw new IllegalArgumentException("Enemy " + code + " has duplicate attack option " + option.code());
			}
		}
	}

	public EnemyStatRule statRule() {
		return toRule(statPriority, fixedStats);
	}

	public boolean hasTrait(EnemyTrait trait) {
		return traits.contains(Objects.requireNonNull(trait, "trait"));
	}

	public Optional<EnemyAttackOption> findAttack(String optionCode) {
		return attacks.stream().filter(option -> option.code().equals(optionCode)).findFirst();
	}

	/** The enemy max-HP rule: {@code baseHp + (RESOLVE - 6)}, with overflow-checked arithmetic. */
	public int maxHpAt(StatValue resolve) {
		return maxHp(baseHp, resolve);
	}

	private static int maxHp(int baseHp, StatValue resolve) {
		return Math.addExact(baseHp, Math.subtractExact(resolve.value(), HP_RESOLVE_PIVOT));
	}

	private static EnemyStatRule toRule(List<StatType> priority, Map<StatType, Integer> fixed) {
		if (!priority.isEmpty()) {
			return new EnemyStatRule.ShapePriority(priority);
		}
		Map<StatType, Integer> values = new EnumMap<>(fixed);
		return new EnemyStatRule.Fixed(new StatBlock(
				new StatValue(values.get(StatType.MIGHT)),
				new StatValue(values.get(StatType.AGILITY)),
				new StatValue(values.get(StatType.PERCEPTION)),
				new StatValue(values.get(StatType.ARCANA)),
				new StatValue(values.get(StatType.RESOLVE))));
	}
}
