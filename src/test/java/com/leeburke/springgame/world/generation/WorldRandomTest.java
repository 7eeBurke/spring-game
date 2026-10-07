package com.leeburke.springgame.world.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class WorldRandomTest {

	@Test
	void mix64MatchesSplitMix64ReferenceOutput() {
		// The first output of the SplitMix64 reference generator seeded with 1234567.
		assertThat(WorldRandom.mix64(1234567L + WorldRandom.GOLDEN_GAMMA)).isEqualTo(6457827717110365317L);
	}

	@Test
	void derivationIsStable() {
		assertThat(WorldRandom.attemptSeed(42L, 0)).isEqualTo(WorldRandom.attemptSeed(42L, 0));
		assertThat(WorldRandom.sceneSeed(99L, 3)).isEqualTo(WorldRandom.derive(99L, WorldRandom.SCENE_DOMAIN, 3));
	}

	@Test
	void attemptAndSceneSeedsDiffer() {
		Set<Long> seeds = new HashSet<>();
		for (int attempt = 0; attempt < RunWorldGenerator.MAX_ATTEMPTS; attempt++) {
			seeds.add(WorldRandom.attemptSeed(42L, attempt));
		}
		for (int scene = 0; scene < 10; scene++) {
			seeds.add(WorldRandom.sceneSeed(WorldRandom.attemptSeed(42L, 0), scene));
		}
		assertThat(seeds).hasSize(RunWorldGenerator.MAX_ATTEMPTS + 10);
	}

	@Test
	void sameSeedGivesSameSequence() {
		RandomGenerator a = WorldRandom.create(7L);
		RandomGenerator b = WorldRandom.create(7L);
		for (int i = 0; i < 50; i++) {
			assertThat(a.nextInt(1000)).isEqualTo(b.nextInt(1000));
		}
	}

	@Test
	void usesTheNamedAlgorithm() {
		assertThat(WorldRandom.ALGORITHM).isEqualTo("L64X128MixRandom");
	}
}
