package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.world.IntRange;
import com.leeburke.springgame.content.world.RegionDefinition;

/**
 * Draws a region's counts and layout. Draw order (fixed for reproducibility): required count,
 * branch count among those valid for it, optional count, the arrangement of forks among the middle
 * stages, then the optional scenes' hosts (without replacement; never the pre-boss scene).
 */
final class TopologyPlanner {

	private TopologyPlanner() {
	}

	static RegionTopology plan(RegionDefinition region, RandomGenerator rng) {
		int required = uniform(region.requiredScenes(), rng);

		List<Integer> validBranches = new ArrayList<>();
		for (int b = region.branches().min(); b <= region.branches().max(); b++) {
			if (required >= RegionDefinition.minimumRequiredFor(b)) {
				validBranches.add(b);
			}
		}
		int branches = validBranches.get(rng.nextInt(validBranches.size()));
		int optional = uniform(region.optionalScenes(), rng);

		int singles = required - RegionDefinition.minimumRequiredFor(branches);
		List<List<Boolean>> arrangements = new ArrayList<>();
		arrangeForks(branches + singles, branches, new ArrayList<>(), arrangements);
		List<Boolean> forks = arrangements.get(rng.nextInt(arrangements.size()));

		List<Integer> stageSizes = new ArrayList<>();
		stageSizes.add(1);
		forks.forEach(fork -> stageSizes.add(fork ? 2 : 1));
		stageSizes.add(1);

		List<Integer> eligibleHosts = new ArrayList<>();
		for (int i = 0; i < required - 1; i++) {
			eligibleHosts.add(i);
		}
		List<Integer> hosts = new ArrayList<>();
		for (int i = 0; i < optional; i++) {
			hosts.add(eligibleHosts.remove(rng.nextInt(eligibleHosts.size())));
		}
		hosts.sort(null);
		return new RegionTopology(stageSizes, hosts);
	}

	private static int uniform(IntRange range, RandomGenerator rng) {
		return range.min() + rng.nextInt(range.max() - range.min() + 1);
	}

	/** All placements of {@code forks} true values among {@code length} positions, in lexicographic order (false first). */
	private static void arrangeForks(int length, int forks, List<Boolean> prefix, List<List<Boolean>> out) {
		long placed = prefix.stream().filter(b -> b).count();
		if (prefix.size() == length) {
			if (placed == forks) {
				out.add(List.copyOf(prefix));
			}
			return;
		}
		for (boolean fork : new boolean[] { false, true }) {
			prefix.add(fork);
			arrangeForks(length, forks, prefix, out);
			prefix.removeLast();
		}
	}
}
