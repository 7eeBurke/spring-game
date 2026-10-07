package com.leeburke.springgame.action.resolution;

/** Why a step was never executed. */
public enum CancellationReason {
	PREVIOUS_STEP_NOT_SUCCESSFUL,
	PLAYER_DOWN
}
