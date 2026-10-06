package com.leeburke.springgame.character;

import java.util.Arrays;
import java.util.List;

import com.leeburke.springgame.mechanics.StatValue;

/**
 * Player stat-spread profiles with their selection weights (percent).
 * Each predicate implements the definition in docs/CHARACTER_GENERATION.md "Stat Budget" literally;
 * every valid {@link StatShape} matches exactly one profile.
 */
public enum StatProfile {

	BALANCED(20) {
		@Override
		public boolean matches(StatShape shape) {
			return shape.max() <= 7 && shape.min() >= 4 && shape.max() - shape.min() <= 2;
		}
	},
	SPECIALIZED(70) {
		@Override
		public boolean matches(StatShape shape) {
			return shape.max() <= 9 && shape.max() - shape.min() >= 3 && !BALANCED.matches(shape);
		}
	},
	EXTREME(10) {
		@Override
		public boolean matches(StatShape shape) {
			return shape.values().contains(StatValue.MAX);
		}
	};

	private final int selectionWeight;

	StatProfile(int selectionWeight) {
		this.selectionWeight = selectionWeight;
	}

	public int selectionWeight() {
		return selectionWeight;
	}

	public abstract boolean matches(StatShape shape);

	public static StatProfile classify(StatShape shape) {
		List<StatProfile> matching = Arrays.stream(values()).filter(p -> p.matches(shape)).toList();
		if (matching.size() != 1) {
			throw new IllegalStateException("Shape " + shape.values() + " matched profiles " + matching);
		}
		return matching.getFirst();
	}
}
