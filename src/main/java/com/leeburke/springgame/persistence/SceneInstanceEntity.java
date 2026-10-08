package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.leeburke.springgame.world.SceneKind;

/**
 * JPA mapping of {@code scene_instance}: relational metadata plus the scene state as a JSONB
 * document. The document text is produced and read only by {@link SceneStateCodec}; Hibernate
 * binds it as JSON without interpreting it.
 * <p>
 * {@code revision} is the optimistic-lock version: Hibernate starts it at 0 and increments it on
 * every update, and rejects an update whose version no longer matches.
 */
@Entity
@Table(name = "scene_instance")
public class SceneInstanceEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Enumerated(EnumType.STRING)
	@Column(name = "scene_kind", nullable = false)
	private SceneKind kind;

	@Column(name = "region_id")
	private UUID regionId;

	@Column(name = "definition_code", nullable = false)
	private String definitionCode;

	@Column(name = "scene_seed")
	private Long sceneSeed;

	@Column(name = "discovered", nullable = false)
	private boolean discovered;

	@Version
	@Column(name = "revision", nullable = false)
	private long revision;

	@Column(name = "state_schema_version", nullable = false)
	private int stateSchemaVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "state", nullable = false)
	private String state;

	protected SceneInstanceEntity() {
		// for JPA
	}

	SceneInstanceEntity(UUID id, UUID runId, SceneKind kind, UUID regionId, String definitionCode, Long sceneSeed,
			boolean discovered, int stateSchemaVersion, String state) {
		this.id = id;
		this.runId = runId;
		this.kind = kind;
		this.regionId = regionId;
		this.definitionCode = definitionCode;
		this.sceneSeed = sceneSeed;
		this.discovered = discovered;
		this.stateSchemaVersion = stateSchemaVersion;
		this.state = state;
	}

	void replaceState(int stateSchemaVersion, String state) {
		this.stateSchemaVersion = stateSchemaVersion;
		this.state = state;
	}

	UUID getId() {
		return id;
	}

	UUID getRunId() {
		return runId;
	}

	SceneKind getKind() {
		return kind;
	}

	UUID getRegionId() {
		return regionId;
	}

	String getDefinitionCode() {
		return definitionCode;
	}

	Long getSceneSeed() {
		return sceneSeed;
	}

	boolean isDiscovered() {
		return discovered;
	}

	void markDiscovered() {
		this.discovered = true;
	}

	long getRevision() {
		return revision;
	}

	int getStateSchemaVersion() {
		return stateSchemaVersion;
	}

	String getState() {
		return state;
	}
}
