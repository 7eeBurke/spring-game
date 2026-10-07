package com.leeburke.springgame.persistence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.leeburke.springgame.shared.StrictJson;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Storage contract for the persisted {@link GenerationContextSnapshot}. The structure version is
 * stored relationally; decoding is strict and an unsupported version or corrupt document raises
 * {@link PersistedStateException} naming the run.
 */
final class GenerationContextCodec {

	static final int CURRENT_SCHEMA_VERSION = 1;

	private final JsonMapper mapper = StrictJson.createMapper();

	/** Stored document shape, version 1. */
	record GenerationContextDocument(List<String> recentOpeningArchetypeCodes) {
	}

	String encode(GenerationContextSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		return mapper.writeValueAsString(new GenerationContextDocument(snapshot.recentOpeningArchetypeCodes()));
	}

	GenerationContextSnapshot decode(UUID runId, int schemaVersion, String json) {
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			throw new PersistedStateException("Run " + runId + ": unsupported generation-context schema version "
					+ schemaVersion + " (supported: " + CURRENT_SCHEMA_VERSION + ")");
		}
		if (json == null) {
			throw new PersistedStateException("Run " + runId + ": generation-context document is missing");
		}
		GenerationContextDocument document;
		try {
			document = mapper.readValue(json, GenerationContextDocument.class);
		} catch (JacksonException e) {
			throw new PersistedStateException("Run " + runId + ": invalid generation-context document: " + e.getOriginalMessage(), e);
		}
		try {
			return new GenerationContextSnapshot(document.recentOpeningArchetypeCodes());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new PersistedStateException("Run " + runId + ": generation-context document is invalid: " + e.getMessage(), e);
		}
	}
}
