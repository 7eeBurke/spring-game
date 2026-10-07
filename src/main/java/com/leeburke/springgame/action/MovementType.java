package com.leeburke.springgame.action;

/** Movement vocabulary (MVP_SCOPE). No range or movement-cost mechanics are implied. */
public enum MovementType {
	ADVANCE,
	RETREAT,
	CLOSE_DISTANCE,
	REPOSITION,
	CIRCLE,
	CLIMB,
	DISENGAGE,
	HOLD_POSITION
}
