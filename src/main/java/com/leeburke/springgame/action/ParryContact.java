package com.leeburke.springgame.action;

/** Where a parry meets the incoming attack. Meaningful only for PARRY. */
public enum ParryContact {
	UNSPECIFIED,
	WEAPON,
	WEAPON_HEAD,
	SHAFT,
	BLADE,
	ATTACKING_ARM
}
