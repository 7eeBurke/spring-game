package com.leeburke.springgame.world;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * A persisted scene: stable metadata plus its dynamic {@link SceneState}.
 *
 * @param definitionCode the scene's definition, for example {@code THE_LAST_LANTERN} or {@code RUINED_NAVE}
 * @param revision       increases by one each time the scene's state is updated; a new scene starts at 0
 */
public record SceneInstance(
		UUID id,
		UUID runId,
		String definitionCode,
		ScenePlacement placement,
		boolean discovered,
		long revision,
		SceneState state) {

	public SceneInstance {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(runId, "runId");
		DefinitionCodes.requireCode(definitionCode, "Scene definition code");
		Objects.requireNonNull(placement, "placement");
		Objects.requireNonNull(state, "state");
		if (revision < 0) {
			throw new IllegalArgumentException("Scene revision must not be negative, but was " + revision);
		}
	}

	public SceneKind kind() {
		return placement.kind();
	}

	public Optional<UUID> regionInstanceId() {
		return placement instanceof ScenePlacement.Region region ? Optional.of(region.regionInstanceId()) : Optional.empty();
	}

	public OptionalLong sceneSeed() {
		return placement instanceof ScenePlacement.Region region ? OptionalLong.of(region.sceneSeed()) : OptionalLong.empty();
	}
}
