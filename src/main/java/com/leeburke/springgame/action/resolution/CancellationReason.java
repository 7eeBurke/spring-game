package com.leeburke.springgame.action.resolution;

/** Why a step was never executed. */
public enum CancellationReason {
	PREVIOUS_STEP_NOT_SUCCESSFUL,
	PLAYER_DOWN,
	/** An earlier step in the same intent brought this step's target to 0 HP. */
	TARGET_DEFEATED,
	/** An earlier step in the same intent left the scene. */
	LEFT_SCENE
}
