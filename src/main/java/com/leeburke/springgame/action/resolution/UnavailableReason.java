package com.leeburke.springgame.action.resolution;

/** Why a valid step cannot be mechanically resolved yet. Not a failure: nothing was attempted. */
public enum UnavailableReason {
	ACTION_NOT_IMPLEMENTED,
	SIMULTANEOUS_ACTION,
	MISSING_TARGET_PROFILE,
	NO_INCOMING_ATTACK,
	INCOMING_ATTACK_ALREADY_RESOLVED,
	UNDEFINED_INJURY_MODIFIER
}
