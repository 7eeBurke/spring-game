package com.leeburke.springgame.mechanics;

/**
 * Result band of a check. There is deliberately no critical failure: it is contextual,
 * not a degree. See docs/GAME_RULES.md "Checks".
 */
public enum DegreeOfSuccess {
	CRITICAL_SUCCESS,
	SUCCESS,
	PARTIAL_SUCCESS,
	FAILURE
}
