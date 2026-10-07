package com.leeburke.springgame.world;

import java.util.Objects;
import java.util.UUID;

import com.leeburke.springgame.shared.DefinitionCodes;

/** A generated region belonging to a run. Generation metadata belongs to a later stage. */
public record RegionInstance(UUID id, UUID runId, String definitionCode) {

	public RegionInstance {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(runId, "runId");
		DefinitionCodes.requireCode(definitionCode, "Region definition code");
	}
}
