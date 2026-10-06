package com.leeburke.springgame.mechanics;

/**
 * How well an approach suits an action. {@code TERRIBLE} is the lowest level that still
 * permits a roll; {@code IMPOSSIBLE} is rejected before any check. See docs/GAME_RULES.md "Suitability".
 */
public enum Suitability {
	EXCELLENT,
	GOOD,
	FAIR,
	POOR,
	TERRIBLE,
	IMPOSSIBLE
}
