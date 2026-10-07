package com.leeburke.springgame.content.world;

/** An inclusive, non-negative integer range. */
public record IntRange(int min, int max) {

	public IntRange {
		if (min < 0 || max < min) {
			throw new IllegalArgumentException("Range must satisfy 0 <= min <= max, but was " + min + ".." + max);
		}
	}

	public boolean contains(int value) {
		return value >= min && value <= max;
	}
}
