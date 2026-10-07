package com.leeburke.springgame.action;

/** The kind of action a step attempts. Derived from the step's payload, never stored separately. */
public enum ActionType {
	ATTACK,
	DEFEND,
	MOVE,
	INTERACT,
	OBSERVE,
	USE_ABILITY,
	USE_ITEM,
	COMMUNICATE
}
