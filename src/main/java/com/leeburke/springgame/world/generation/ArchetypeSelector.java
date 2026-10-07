package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Anti-repetition archetype selection, with exact integer weights.
 * <p>
 * Opening scene: every normal archetype has weight {@value #BASE_WEIGHT}, except those in the
 * snapshot's recent openings, weighted by their earliest position: most recent
 * {@code RECENT_WEIGHTS[0]}, then {@code [1]}, then {@code [2]}. No weight is zero, so selection
 * always succeeds; codes from other regions have no effect.
 * <p>
 * Other scenes: exclude archetypes already assigned to graph neighbours, keep only the least-used
 * remaining archetypes within the region, then choose uniformly.
 */
final class ArchetypeSelector {

	static final int BASE_WEIGHT = 8;
	static final List<Integer> RECENT_WEIGHTS = List.of(1, 3, 5);

	private ArchetypeSelector() {
	}

	/** Opening weights in the region's authored archetype order. */
	static List<Integer> openingWeights(List<String> normalArchetypes, GenerationContextSnapshot context) {
		List<String> recent = context.recentOpeningArchetypeCodes();
		List<Integer> weights = new ArrayList<>();
		for (String archetype : normalArchetypes) {
			int position = recent.indexOf(archetype);
			weights.add(position < 0 ? BASE_WEIGHT : RECENT_WEIGHTS.get(position));
		}
		return List.copyOf(weights);
	}

	static String pickOpening(List<String> normalArchetypes, GenerationContextSnapshot context, RandomGenerator rng) {
		List<Integer> weights = openingWeights(normalArchetypes, context);
		int total = weights.stream().mapToInt(Integer::intValue).sum();
		return openingForRoll(normalArchetypes, weights, rng.nextInt(total));
	}

	/** Walks the cumulative weights in authored order. */
	static String openingForRoll(List<String> normalArchetypes, List<Integer> weights, int roll) {
		int remaining = roll;
		for (int i = 0; i < normalArchetypes.size(); i++) {
			remaining -= weights.get(i);
			if (remaining < 0) {
				return normalArchetypes.get(i);
			}
		}
		throw new IllegalArgumentException("Roll " + roll + " exceeds the total opening weight");
	}

	static String pickNext(List<String> normalArchetypes, Set<String> neighbourArchetypes, Map<String, Integer> usage,
			RandomGenerator rng) {
		List<String> allowed = normalArchetypes.stream().filter(a -> !neighbourArchetypes.contains(a)).toList();
		if (allowed.isEmpty()) {
			throw new IllegalStateException("Every archetype is used by a neighbour: " + neighbourArchetypes);
		}
		int leastUsed = allowed.stream().mapToInt(a -> usage.getOrDefault(a, 0)).min().orElseThrow();
		List<String> pool = allowed.stream().filter(a -> usage.getOrDefault(a, 0) == leastUsed).toList();
		return pool.get(rng.nextInt(pool.size()));
	}
}
