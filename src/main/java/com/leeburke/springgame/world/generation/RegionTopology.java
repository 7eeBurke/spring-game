package com.leeburke.springgame.world.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Generation-only layout of a region before any scene exists.
 * <p>
 * Route scenes are numbered 0..R-1 in stage order (the top scene of a fork first). Stage 0 is the
 * entry and the last stage is the pre-boss scene; both hold one scene, and a stage of two scenes is
 * a branch (route fork). Optional scenes are dead ends attached to {@code optionalHosts} (route
 * scene indexes, ascending). Generation order is route scenes, then optional scenes, then the boss.
 */
record RegionTopology(List<Integer> stageSizes, List<Integer> optionalHosts) {

	RegionTopology {
		stageSizes = List.copyOf(Objects.requireNonNull(stageSizes, "stageSizes"));
		optionalHosts = List.copyOf(Objects.requireNonNull(optionalHosts, "optionalHosts"));
	}

	int requiredCount() {
		return stageSizes.stream().mapToInt(Integer::intValue).sum();
	}

	int branchCount() {
		return (int) stageSizes.stream().filter(size -> size == 2).count();
	}

	int optionalCount() {
		return optionalHosts.size();
	}

	int sceneCount() {
		return requiredCount() + optionalCount() + 1;
	}

	int bossIndex() {
		return requiredCount() + optionalCount();
	}

	/** Route scene indexes of each stage. */
	List<List<Integer>> stages() {
		List<List<Integer>> stages = new ArrayList<>();
		int next = 0;
		for (int size : stageSizes) {
			List<Integer> stage = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				stage.add(next++);
			}
			stages.add(List.copyOf(stage));
		}
		return List.copyOf(stages);
	}

	/**
	 * Undirected links, as sorted neighbour indexes for each scene index: every scene of a stage
	 * links to every scene of the next stage, the pre-boss scene links to the boss, and each
	 * optional scene links only to its host.
	 */
	List<List<Integer>> neighbours() {
		List<SortedSet<Integer>> links = new ArrayList<>();
		for (int i = 0; i < sceneCount(); i++) {
			links.add(new TreeSet<>());
		}
		List<List<Integer>> stages = stages();
		for (int s = 0; s + 1 < stages.size(); s++) {
			for (int a : stages.get(s)) {
				for (int b : stages.get(s + 1)) {
					links.get(a).add(b);
					links.get(b).add(a);
				}
			}
		}
		int preBoss = requiredCount() - 1;
		links.get(preBoss).add(bossIndex());
		links.get(bossIndex()).add(preBoss);
		for (int o = 0; o < optionalHosts.size(); o++) {
			int optional = requiredCount() + o;
			links.get(optional).add(optionalHosts.get(o));
			links.get(optionalHosts.get(o)).add(optional);
		}
		return links.stream().map(List::copyOf).toList();
	}
}
