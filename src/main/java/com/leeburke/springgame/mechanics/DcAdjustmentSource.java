package com.leeburke.springgame.mechanics;

/**
 * Where a numeric DC adjustment came from. Matches the categories listed in docs/GAME_RULES.md
 * "Checks" (Final DC). Only labels adjustments for the result breakdown; no logic switches on it,
 * so new sources can be added safely. Names should be treated as stable once results are persisted.
 */
public enum DcAdjustmentSource {
	SUITABILITY,
	INJURY,
	POSITION,
	ENVIRONMENT,
	PASSIVE,
	TARGETING,
	SIMULTANEOUS_ACTION_COMPLEXITY
}
