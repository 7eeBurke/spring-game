package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;

/** All tests use fixed seeds, so results are deterministic rather than statistical. */
class PlayerStatGeneratorTest {

	private static final int SEED_COUNT = 1000;

	private final PlayerStatGenerator generator = new PlayerStatGenerator();

	@Test
	void everyGeneratedBlockTotals27WithValuesInRange() {
		for (long seed = 0; seed < SEED_COUNT; seed++) {
			StatBlock block = generator.generate(new SplittableRandom(seed));
			int total = 0;
			for (StatType type : StatType.values()) {
				int value = block.get(type).value();
				assertThat(value).isBetween(3, 10);
				total += value;
			}
			assertThat(total).as("seed %d", seed).isEqualTo(27);
		}
	}

	@ParameterizedTest
	@EnumSource(StatProfile.class)
	void explicitlyRequestedProfileIsAlwaysSatisfied(StatProfile profile) {
		for (long seed = 0; seed < SEED_COUNT; seed++) {
			StatBlock block = generator.generate(profile, new SplittableRandom(seed));
			assertThat(StatProfile.classify(StatShape.of(block))).as("seed %d", seed).isEqualTo(profile);
		}
	}

	@ParameterizedTest
	@EnumSource(StatProfile.class)
	void everyCatalogShapeOfProfileIsReachable(StatProfile profile) {
		Set<StatShape> produced = new HashSet<>();
		for (long seed = 0; seed < SEED_COUNT; seed++) {
			produced.add(StatShape.of(generator.generate(profile, new SplittableRandom(seed))));
		}
		assertThat(produced).containsExactlyInAnyOrderElementsOf(StatShapeCatalog.shapesFor(profile));
	}

	@Test
	void sameSeedReproducesSameSequence() {
		SplittableRandom first = new SplittableRandom(42);
		SplittableRandom second = new SplittableRandom(42);
		List<StatBlock> a = new ArrayList<>();
		List<StatBlock> b = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			a.add(generator.generate(first));
			b.add(generator.generate(second));
		}
		assertThat(a).isEqualTo(b);
	}

	@Test
	void differentSeedsProduceVariation() {
		Set<StatBlock> distinct = new HashSet<>();
		for (long seed = 0; seed < 50; seed++) {
			distinct.add(generator.generate(new SplittableRandom(seed)));
		}
		assertThat(distinct).hasSizeGreaterThan(1);
	}
}
