package com.leeburke.springgame.action;

/** How a step relates to the immediately preceding step. Execution semantics belong to action resolution. */
public enum StepRelation {
	START,
	THEN,
	IF_PREVIOUS_SUCCEEDS,
	WHILE
}
