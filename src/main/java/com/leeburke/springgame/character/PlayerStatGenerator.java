package com.leeburke.springgame.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

/**
 * Generates a player {@link StatBlock}: choose a profile by weight, choose one canonical shape
 * uniformly within that profile, then shuffle its values across the five stats.
 * <p>
 * Randomness is supplied by the caller, so the same random state reproduces the same block.
 * Draw order is fixed (profile, shape, shuffle); changing it changes seeded results.
 */
public final class PlayerStatGenerator {

	public StatBlock generate(RandomGenerator rng) {
		Objects.requireNonNull(rng, "rng");
		return generate(chooseProfile(rng), rng);
	}

	public StatBlock generate(StatProfile profile, RandomGenerator rng) {
		Objects.requireNonNull(profile, "profile");
		Objects.requireNonNull(rng, "rng");
		List<StatShape> shapes = StatShapeCatalog.shapesFor(profile);
		StatShape shape = shapes.get(rng.nextInt(shapes.size()));

		List<Integer> values = new ArrayList<>(shape.values());
		Collections.shuffle(values, rng);
		return new StatBlock(
				new StatValue(values.get(0)),
				new StatValue(values.get(1)),
				new StatValue(values.get(2)),
				new StatValue(values.get(3)),
				new StatValue(values.get(4)));
	}

	private static StatProfile chooseProfile(RandomGenerator rng) {
		int totalWeight = 0;
		for (StatProfile profile : StatProfile.values()) {
			totalWeight += profile.selectionWeight();
		}
		int roll = rng.nextInt(totalWeight);
		for (StatProfile profile : StatProfile.values()) {
			roll -= profile.selectionWeight();
			if (roll < 0) {
				return profile;
			}
		}
		throw new IllegalStateException("Unreachable: weights exhausted");
	}
}
