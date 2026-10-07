package com.leeburke.springgame.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Composite key of {@code enemy_instance}: the scene and the enemy's scene-local entity ID. */
@Embeddable
public class EnemyInstanceId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "scene_id", nullable = false)
	private UUID sceneId;

	@Column(name = "entity_local_id", nullable = false)
	private String entityLocalId;

	protected EnemyInstanceId() {
		// for JPA
	}

	EnemyInstanceId(UUID sceneId, String entityLocalId) {
		this.sceneId = Objects.requireNonNull(sceneId, "sceneId");
		this.entityLocalId = Objects.requireNonNull(entityLocalId, "entityLocalId");
	}

	UUID getSceneId() {
		return sceneId;
	}

	String getEntityLocalId() {
		return entityLocalId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EnemyInstanceId id && Objects.equals(sceneId, id.sceneId)
				&& Objects.equals(entityLocalId, id.entityLocalId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sceneId, entityLocalId);
	}
}
