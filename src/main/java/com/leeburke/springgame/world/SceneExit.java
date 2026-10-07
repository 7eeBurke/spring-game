package com.leeburke.springgame.world;

import java.util.Objects;
import java.util.UUID;

/** An exit from a zone toward another scene. The destination is authoritative backend state and is never shown to the player. */
public record SceneExit(String id, String zoneId, UUID destinationSceneId) {

	public SceneExit {
		LocalIds.requireLocalId(id, "Exit id");
		LocalIds.requireLocalId(zoneId, "Exit zone");
		Objects.requireNonNull(destinationSceneId, "destinationSceneId");
	}
}
