package com.leeburke.springgame.action.validation;

/**
 * Stable validation error codes.
 * <p>
 * {@link #UNKNOWN_SCENE_REFERENCE} deliberately covers both "does not exist" and "exists but the
 * player cannot see it": validation only consults the player-safe view, so the two are
 * indistinguishable and errors can never reveal hidden content.
 */
public enum ActionValidationCode {
	/** The intent's schema version is not supported. */
	UNSUPPORTED_SCHEMA_VERSION,
	/** The action is internally inconsistent (for example a missing or wrong kind of target). */
	SCHEMA_INVALID,
	/** The interpreter could not resolve a phrase to anything it was shown. */
	UNRESOLVED_REFERENCE,
	/** A targeted entity, object, hazard, zone or exit is not in the player's view. */
	UNKNOWN_SCENE_REFERENCE,
	/** A weapon, ability, item or carried reference is not among the player's current references. */
	UNKNOWN_PLAYER_REFERENCE,
	/** The incoming attack being responded to is not a currently known incoming attack. */
	UNKNOWN_INCOMING_ATTACK,
	/** The action is physically impossible; no check is rolled. Distinct from TERRIBLE suitability. */
	ACTION_PHYSICALLY_IMPOSSIBLE
}
