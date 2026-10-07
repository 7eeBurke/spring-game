package com.leeburke.springgame.persistence;

import java.util.UUID;

/**
 * A scene-state update was based on a revision that is no longer current, either because the
 * caller's expected revision was already out of date or because a concurrent update committed
 * first. Nothing was written; the caller must reload the scene and resolve again.
 */
public class StaleSceneStateException extends RuntimeException {

	private final UUID sceneId;
	private final long expectedRevision;

	public StaleSceneStateException(UUID sceneId, long expectedRevision, String detail, Throwable cause) {
		super("Scene " + sceneId + ": update expected revision " + expectedRevision + " but " + detail, cause);
		this.sceneId = sceneId;
		this.expectedRevision = expectedRevision;
	}

	public UUID sceneId() {
		return sceneId;
	}

	public long expectedRevision() {
		return expectedRevision;
	}
}
