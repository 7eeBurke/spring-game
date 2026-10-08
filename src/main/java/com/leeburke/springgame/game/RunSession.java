package com.leeburke.springgame.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A run's lifecycle and turn bookkeeping. {@code stateVersion} changes with every committed turn and
 * is what a client's view must match; {@code cursor} is the last enemy to act.
 */
public record RunSession(UUID runId, RunStatus status, long stateVersion, int turnNumber, Optional<EnemyCursor> cursor) {

	public RunSession {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(cursor, "cursor");
		if (stateVersion < 0 || turnNumber < 0) {
			throw new IllegalArgumentException("Versions and turn numbers cannot be negative");
		}
	}

	/** The last enemy to act, by scene and scene-local entity ID. */
	public record EnemyCursor(UUID sceneId, String entityId) {
		public EnemyCursor {
			Objects.requireNonNull(sceneId, "sceneId");
			Objects.requireNonNull(entityId, "entityId");
		}
	}
}
