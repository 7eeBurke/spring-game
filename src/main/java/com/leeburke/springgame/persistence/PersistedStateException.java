package com.leeburke.springgame.persistence;

/**
 * Stored data could not be turned into valid domain state, for example a content code missing from
 * the current catalogue or a run without its player character. Never results in partial state.
 */
public class PersistedStateException extends RuntimeException {

	public PersistedStateException(String message) {
		super(message);
	}

	public PersistedStateException(String message, Throwable cause) {
		super(message, cause);
	}
}
