package com.leeburke.springgame.mechanics;

/**
 * A single stat score, guaranteed to lie within the documented range of 3 to 10 inclusive.
 * See docs/CHARACTER_GENERATION.md "Stat Budget" and docs/GAME_RULES.md "Core Stats".
 */
public record StatValue(int value) {

	public static final int MIN = 3;
	public static final int MAX = 10;

	public StatValue {
		if (value < MIN || value > MAX) {
			throw new IllegalArgumentException(
					"Stat value must be between " + MIN + " and " + MAX + " inclusive, but was " + value);
		}
	}
}
