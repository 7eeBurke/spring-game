package com.leeburke.springgame.enemy.behavior;

/** Stage 12 behaviour weights (see docs/GAME_RULES.md "Enemy behaviour"). */
public final class EnemyBehaviorRules {

	/** AGGRESSIVE: added to the HOLD weight. */
	public static final long AGGRESSIVE_HOLD = -10;
	/** CAUTIOUS: added to the HOLD weight. */
	public static final long CAUTIOUS_HOLD = 10;

	/** HOLD bonus per point of RESOLVE below this threshold, while desperate (unless RECKLESS). */
	public static final int PRESSURE_RESOLVE_THRESHOLD = 7;
	public static final long PRESSURE_HOLD_PER_POINT = 5;

	/** How many of the most recent choices count towards repetition. */
	public static final int REPETITION_WINDOW = 2;
	/** Penalty per occurrence of a candidate in the window; doubled when ADAPTIVE, none when RELENTLESS. */
	public static final long REPETITION_PENALTY = 15;
	public static final long ADAPTIVE_REPETITION_MULTIPLIER = 2;

	private EnemyBehaviorRules() {
	}
}
