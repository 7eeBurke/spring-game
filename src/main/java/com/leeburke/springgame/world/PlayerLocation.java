package com.leeburke.springgame.world;

import java.util.Objects;
import java.util.UUID;

/** Where the player is: a scene and a zone within it. World state, not character state. No combat positioning. */
public record PlayerLocation(UUID sceneId, String zoneId) {

	public PlayerLocation {
		Objects.requireNonNull(sceneId, "sceneId");
		LocalIds.requireLocalId(zoneId, "Location zone");
	}
}
