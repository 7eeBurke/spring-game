package com.leeburke.springgame.action.resolution;

/** Whether a step was resolved, never executed (cancelled), or is valid but has no mechanics yet. */
public enum StepStatus {
	RESOLVED,
	CANCELLED,
	MECHANICS_UNAVAILABLE
}
