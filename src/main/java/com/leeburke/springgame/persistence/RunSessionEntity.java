package com.leeburke.springgame.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.leeburke.springgame.game.RunStatus;

/** JPA mapping of {@code run_session}: lifecycle, access-token hash and turn bookkeeping. */
@Entity
@Table(name = "run_session")
public class RunSessionEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RunStatus status;

	@Column(name = "access_token_hash", nullable = false)
	private String accessTokenHash;

	@Column(name = "creation_key", nullable = false)
	private UUID creationKey;

	@Column(name = "state_version", nullable = false)
	private long stateVersion;

	@Column(name = "turn_number", nullable = false)
	private int turnNumber;

	@Column(name = "enemy_cursor_scene_id")
	private UUID enemyCursorSceneId;

	@Column(name = "enemy_cursor_entity_id")
	private String enemyCursorEntityId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RunSessionEntity() {
		// for JPA
	}

	RunSessionEntity(UUID runId, String accessTokenHash, UUID creationKey, Instant createdAt) {
		this.runId = runId;
		this.status = RunStatus.INITIALIZING;
		this.accessTokenHash = accessTokenHash;
		this.creationKey = creationKey;
		this.createdAt = createdAt;
	}

	UUID getRunId() {
		return runId;
	}

	RunStatus getStatus() {
		return status;
	}

	void setStatus(RunStatus status) {
		this.status = status;
	}

	String getAccessTokenHash() {
		return accessTokenHash;
	}

	UUID getCreationKey() {
		return creationKey;
	}

	long getStateVersion() {
		return stateVersion;
	}

	int getTurnNumber() {
		return turnNumber;
	}

	UUID getEnemyCursorSceneId() {
		return enemyCursorSceneId;
	}

	String getEnemyCursorEntityId() {
		return enemyCursorEntityId;
	}

	void advance(RunStatus status, UUID cursorSceneId, String cursorEntityId) {
		this.status = status;
		this.stateVersion++;
		this.turnNumber++;
		this.enemyCursorSceneId = cursorSceneId;
		this.enemyCursorEntityId = cursorEntityId;
	}
}
