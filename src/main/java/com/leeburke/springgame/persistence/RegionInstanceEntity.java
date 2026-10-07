package com.leeburke.springgame.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** JPA mapping of {@code region_instance}. Persistence-only. */
@Entity
@Table(name = "region_instance")
public class RegionInstanceEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "run_id", nullable = false)
	private UUID runId;

	@Column(name = "definition_code", nullable = false)
	private String definitionCode;

	protected RegionInstanceEntity() {
		// for JPA
	}

	RegionInstanceEntity(UUID id, UUID runId, String definitionCode) {
		this.id = id;
		this.runId = runId;
		this.definitionCode = definitionCode;
	}

	UUID getId() {
		return id;
	}

	UUID getRunId() {
		return runId;
	}

	String getDefinitionCode() {
		return definitionCode;
	}
}
