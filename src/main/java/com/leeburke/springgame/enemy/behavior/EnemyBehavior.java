package com.leeburke.springgame.enemy.behavior;

import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.ADAPTIVE_REPETITION_MULTIPLIER;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.AGGRESSIVE_HOLD;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.CAUTIOUS_HOLD;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.PRESSURE_HOLD_PER_POINT;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.PRESSURE_RESOLVE_THRESHOLD;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.REPETITION_PENALTY;
import static com.leeburke.springgame.enemy.behavior.EnemyBehaviorRules.REPETITION_WINDOW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.enemy.EnemyAttackOption;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.content.enemy.EnemyTrait;
import com.leeburke.springgame.enemy.EnemyAttacks;
import com.leeburke.springgame.enemy.EnemyInstance;

/**
 * Java weighted-utility enemy decisions. Candidates are the enemy's attack options in authored
 * order, then HOLD. Each final weight is {@code max(0, base + trait + pressure - repetition)},
 * computed in overflow-checked {@code long} arithmetic. One draw, {@code rng.nextLong(total)},
 * picks the first candidate whose cumulative weight exceeds it; a zero-weight candidate is never
 * chosen. If every weight is 0 the enemy holds, with no draw, so no attack is ever fabricated.
 * <p>
 * Stateless and deterministic for a given context and generator. No AI, no Fated, and no player
 * information (see {@link EnemyDecisionContext}).
 */
public final class EnemyBehavior {

	/** The final weights, in candidate order. */
	public List<WeightedCandidate> weigh(EnemyDecisionContext context) {
		Objects.requireNonNull(context, "context");
		EnemyInstance self = context.self().instance();
		if (self.currentHp() == 0) {
			throw new IllegalArgumentException("Enemy " + self.entityId() + " is down and cannot act");
		}
		EnemyDefinition definition = context.self().definition();
		List<String> window = context.recentChoices().subList(
				Math.max(0, context.recentChoices().size() - REPETITION_WINDOW), context.recentChoices().size());

		List<WeightedCandidate> candidates = new ArrayList<>();
		for (EnemyAttackOption option : definition.attacks()) {
			long weight = Math.subtractExact(option.weight(), repetition(definition, window, option.code()));
			candidates.add(new WeightedCandidate(option.code(), Math.max(0, weight)));
		}
		long hold = Math.addExact(definition.holdWeight(), holdTraitModifier(definition));
		hold = Math.addExact(hold, pressure(definition, self));
		hold = Math.subtractExact(hold, repetition(definition, window, EnemyAttackOption.HOLD_CODE));
		candidates.add(new WeightedCandidate(EnemyAttackOption.HOLD_CODE, Math.max(0, hold)));
		return List.copyOf(candidates);
	}

	public EnemyDecision decide(EnemyDecisionContext context, RandomGenerator rng) {
		Objects.requireNonNull(rng, "rng");
		List<WeightedCandidate> weights = weigh(context);
		long total = 0;
		for (WeightedCandidate candidate : weights) {
			total = Math.addExact(total, candidate.weight());
		}
		if (total == 0) {
			return new EnemyDecision.Hold(weights);
		}
		long roll = rng.nextLong(total);
		long cumulative = 0;
		for (WeightedCandidate candidate : weights) {
			cumulative = Math.addExact(cumulative, candidate.weight());
			if (roll < cumulative) {
				return toDecision(candidate.code(), context, weights);
			}
		}
		throw new IllegalStateException("Roll " + roll + " outside total weight " + total);
	}

	private static EnemyDecision toDecision(String code, EnemyDecisionContext context, List<WeightedCandidate> weights) {
		if (code.equals(EnemyAttackOption.HOLD_CODE)) {
			return new EnemyDecision.Hold(weights);
		}
		EnemyAttackOption option = context.self().definition().findAttack(code).orElseThrow();
		return new EnemyDecision.Attack(code, EnemyAttacks.build(context.attackRef(), context.self(), option), weights);
	}

	private static long holdTraitModifier(EnemyDefinition definition) {
		long modifier = 0;
		if (definition.hasTrait(EnemyTrait.AGGRESSIVE)) {
			modifier = Math.addExact(modifier, AGGRESSIVE_HOLD);
		}
		if (definition.hasTrait(EnemyTrait.CAUTIOUS)) {
			modifier = Math.addExact(modifier, CAUTIOUS_HOLD);
		}
		return modifier;
	}

	/** Desperate: at or below half HP. A desperate, non-RECKLESS enemy with low RESOLVE leans towards holding. */
	private static long pressure(EnemyDefinition definition, EnemyInstance self) {
		boolean desperate = Math.multiplyExact((long) self.currentHp(), 2) <= self.maxHp();
		if (!desperate || definition.hasTrait(EnemyTrait.RECKLESS)) {
			return 0;
		}
		long shortfall = Math.max(0, PRESSURE_RESOLVE_THRESHOLD - self.stats().resolve().value());
		return Math.multiplyExact(PRESSURE_HOLD_PER_POINT, shortfall);
	}

	private static long repetition(EnemyDefinition definition, List<String> window, String code) {
		if (definition.hasTrait(EnemyTrait.RELENTLESS)) {
			return 0;
		}
		long perOccurrence = definition.hasTrait(EnemyTrait.ADAPTIVE)
				? Math.multiplyExact(REPETITION_PENALTY, ADAPTIVE_REPETITION_MULTIPLIER)
				: REPETITION_PENALTY;
		long occurrences = window.stream().filter(code::equals).count();
		return Math.multiplyExact(perOccurrence, occurrences);
	}
}
