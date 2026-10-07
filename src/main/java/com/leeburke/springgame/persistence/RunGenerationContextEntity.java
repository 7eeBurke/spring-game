package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping of {@code run_generation_context}. The document is produced only by {@link GenerationContextCodec}. */
@Entity
@Table(name = "run_generation_context")
public class RunGenerationContextEntity {

	@Id
	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "schema_version", nullable = false)
	private int schemaVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "snapshot", nullable = false)
	private String snapshot;

	protected RunGenerationContextEntity() {
		// for JPA
	}

	RunGenerationContextEntity(UUID runId, int schemaVersion, String snapshot) {
		this.runId = runId;
		this.schemaVersion = schemaVersion;
		this.snapshot = snapshot;
	}

	UUID getRunId() {
		return runId;
	}

	int getSchemaVersion() {
		return schemaVersion;
	}

	String getSnapshot() {
		return snapshot;
	}
}
