package com.leeburke.springgame.game;

/** A run's lifecycle. Only ACTIVE runs accept turns; DEAD and VICTORIOUS are terminal and decided by Java. */
public enum RunStatus {
	/** Being created; never playable. */
	INITIALIZING,
	ACTIVE,
	/** The player's HP reached 0. */
	DEAD,
	/** The Chapel Guardian reached 0 HP while the player lived. */
	VICTORIOUS;

	public boolean terminal() {
		return this == DEAD || this == VICTORIOUS;
	}
}
