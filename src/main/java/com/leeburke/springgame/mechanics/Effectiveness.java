package com.leeburke.springgame.mechanics;

/**
 * How effective a hit is against its target. Levels only: the V1 multipliers live in
 * {@link DamageRules}, and how effectiveness is determined is deferred.
 * See docs/GAME_RULES.md "Damage".
 */
public enum Effectiveness {
	VERY_LOW,
	LOW,
	NORMAL,
	HIGH,
	VERY_HIGH
}
