package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping of {@code run_world_state}: the run's current player location. Persistence-only. */
@Entity
@Table(name = "run_world_state")
public class RunWorldStateEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "current_scene_id", nullable = false)
	private UUID currentSceneId;

	@Column(name = "current_zone_id", nullable = false)
	private String currentZoneId;

	protected RunWorldStateEntity() {
		// for JPA
	}

	RunWorldStateEntity(UUID runId, UUID currentSceneId, String currentZoneId) {
		this.runId = runId;
		this.currentSceneId = currentSceneId;
		this.currentZoneId = currentZoneId;
	}

	void moveTo(UUID sceneId, String zoneId) {
		this.currentSceneId = sceneId;
		this.currentZoneId = zoneId;
	}

	UUID getCurrentSceneId() {
		return currentSceneId;
	}

	String getCurrentZoneId() {
		return currentZoneId;
	}
}
