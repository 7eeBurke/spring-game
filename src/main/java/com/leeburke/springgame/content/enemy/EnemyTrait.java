package com.leeburke.springgame.content.enemy;

/**
 * Authored enemy temperament. Each trait's behaviour effect is defined in
 * {@code enemy.behavior.EnemyBehaviorRules}; {@code OPPORTUNISTIC} is identity-only until an
 * observable player-weakness model exists.
 */
public enum EnemyTrait {
	AGGRESSIVE,
	CAUTIOUS,
	OPPORTUNISTIC,
	RECKLESS,
	ADAPTIVE,
	RELENTLESS
}
