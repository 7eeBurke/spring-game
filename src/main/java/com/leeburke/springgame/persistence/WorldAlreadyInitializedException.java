package com.leeburke.springgame.persistence;

import java.util.UUID;

/** A run's world has already been initialized; a run never receives a second world. Nothing was written. */
public class WorldAlreadyInitializedException extends IllegalStateException {

	public WorldAlreadyInitializedException(UUID runId) {
		super("Run " + runId + " already has an initialized world");
	}
}
