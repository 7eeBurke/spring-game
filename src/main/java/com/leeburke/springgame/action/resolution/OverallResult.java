package com.leeburke.springgame.action.resolution;

/** Aggregate result of resolving an intent. Invalid intents never reach resolution. */
public enum OverallResult {
	COMPLETE_SUCCESS,
	PARTIAL_SUCCESS,
	FAILURE,
	INTERRUPTED,
	MECHANICS_UNAVAILABLE
}
