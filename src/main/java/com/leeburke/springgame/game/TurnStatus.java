package com.leeburke.springgame.game;

/**
 * The life of one turn request. INTERPRETING holds a lease while the interpreter runs; REJECTED and
 * STALE store a final error; MECHANICS_COMMITTED means the turn's state changes are committed and
 * only narration remains; COMPLETED stores the final response. A transient AI failure deletes the
 * INTERPRETING record instead, so the key can be retried.
 */
public enum TurnStatus {
	INTERPRETING,
	REJECTED,
	STALE,
	MECHANICS_COMMITTED,
	COMPLETED;

	/** Whether the record still blocks a new turn of the same run. */
	public boolean unfinished() {
		return this == INTERPRETING || this == MECHANICS_COMMITTED;
	}
}
