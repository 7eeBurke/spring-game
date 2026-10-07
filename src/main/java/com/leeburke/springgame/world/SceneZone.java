package com.leeburke.springgame.world;

/** A meaningful area of a scene (not a coordinate or tile). */
public record SceneZone(String id, String displayName) {

	public SceneZone {
		LocalIds.requireLocalId(id, "Zone id");
		if (displayName == null || displayName.isBlank()) {
			throw new IllegalArgumentException("Zone " + id + " display name must not be blank");
		}
	}
}
