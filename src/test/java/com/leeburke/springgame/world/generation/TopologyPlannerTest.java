package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.CONTENT;
import static com.leeburke.springgame.world.generation.GenerationTestSupport.fixed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.content.world.RegionDefinition;

class TopologyPlannerTest {

	private static final RegionDefinition CHAPEL = CONTENT.findRegion("HOLLOW_CHAPEL").orElseThrow();

	@Test
	void lowestDrawsGiveSmallestRegion() {
		RegionTopology topology = TopologyPlanner.plan(CHAPEL, fixed(0));
		assertThat(topology.requiredCount()).isEqualTo(5);
		assertThat(topology.branchCount()).isEqualTo(1);
		assertThat(topology.optionalCount()).isZero();
		// One single middle stage before the fork (lexicographic arrangement, false first).
		assertThat(topology.stageSizes()).containsExactly(1, 1, 2, 1);
		assertThat(topology.sceneCount()).isEqualTo(6);
	}

	@Test
	void highestDrawsGiveLargestRegion() {
		RegionTopology topology = TopologyPlanner.plan(CHAPEL, fixed(Integer.MAX_VALUE));
		assertThat(topology.requiredCount()).isEqualTo(7);
		assertThat(topology.branchCount()).isEqualTo(2);
		assertThat(topology.optionalCount()).isEqualTo(2);
		assertThat(topology.stageSizes()).containsExactly(1, 2, 2, 1, 1);
		// Hosts drawn from 0..5 (never the pre-boss scene 6), highest first: 5 then 4, stored ascending.
		assertThat(topology.optionalHosts()).containsExactly(4, 5);
		assertThat(topology.sceneCount()).isEqualTo(10);
	}

	@Test
	void neighboursFollowStagesForksOptionalsAndBoss() {
		RegionTopology topology = new RegionTopology(List.of(1, 2, 1, 1), List.of(0));
		// Route: 0 | 1,2 | 3 | 4(pre-boss); optional 5 on host 0; boss 6.
		assertThat(topology.neighbours()).containsExactly(
				List.of(1, 2, 5), List.of(0, 3), List.of(0, 3), List.of(1, 2, 4), List.of(3, 6), List.of(0), List.of(4));
		assertThat(topology.stages()).containsExactly(List.of(0), List.of(1, 2), List.of(3), List.of(4));
	}

	@Test
	void twoBranchesNeverOccurWithFiveRequiredScenes() {
		for (long seed = 0; seed < 300; seed++) {
			RegionTopology topology = TopologyPlanner.plan(CHAPEL, WorldRandom.create(seed));
			assertThat(topology.requiredCount() - RegionDefinition.minimumRequiredFor(topology.branchCount()))
					.as("singles for seed %d", seed).isGreaterThanOrEqualTo(0);
			if (topology.requiredCount() == 5) {
				assertThat(topology.branchCount()).isEqualTo(1);
			}
			assertThat(topology.stageSizes().getFirst()).isEqualTo(1);
			assertThat(topology.stageSizes().getLast()).isEqualTo(1);
		}
	}
}
