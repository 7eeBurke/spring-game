package com.leeburke.springgame.character;

/**
 * A character's Fated value, 0 to 5 inclusive. Visible to the player; it is not luck.
 * Only the number is modelled: qualitative bands and Fated consequences are deferred.
 * See docs/CHARACTER_GENERATION.md "Fated".
 */
public record Fated(int value) {

	public static final int MIN = 0;
	public static final int MAX = 5;

	public Fated {
		if (value < MIN || value > MAX) {
			throw new IllegalArgumentException("Fated must be between " + MIN + " and " + MAX + " inclusive, but was " + value);
		}
	}
}
