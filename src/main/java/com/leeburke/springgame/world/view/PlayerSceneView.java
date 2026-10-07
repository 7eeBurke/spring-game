package com.leeburke.springgame.world.view;

import java.util.List;
import java.util.Objects;

/**
 * What the player currently knows about a scene. This, never the authoritative scene state, is
 * what action interpretation and AI roles receive.
 * <p>
 * Built only by {@link PlayerSceneViewProjector}. It uses its own small records rather than the
 * authoritative ones, so fields added to authoritative state later cannot leak automatically. It
 * never contains hidden content, scene or exit-destination UUIDs, seeds, revisions, environment
 * flags or active events.
 */
public record PlayerSceneView(
		String currentZoneId,
		List<VisibleZone> zones,
		List<VisibleConnection> connections,
		List<VisibleEntity> entities,
		List<VisibleObject> objects,
		List<VisibleHazard> hazards,
		List<KnownExit> exits,
		List<String> discoveredFacts) {

	public PlayerSceneView {
		Objects.requireNonNull(currentZoneId, "currentZoneId");
		zones = List.copyOf(zones);
		connections = List.copyOf(connections);
		entities = List.copyOf(entities);
		objects = List.copyOf(objects);
		hazards = List.copyOf(hazards);
		exits = List.copyOf(exits);
		discoveredFacts = List.copyOf(discoveredFacts);
	}

	public record VisibleZone(String id, String displayName) {
	}

	/** A visible connection between two visible zones. Connection IDs are not exposed. */
	public record VisibleConnection(String zoneA, String zoneB) {
	}

	public record VisibleEntity(String id, String definitionCode, String zoneId) {
	}

	public record VisibleObject(String id, String definitionCode, String zoneId) {
	}

	public record VisibleHazard(String id, String definitionCode, String zoneId) {
	}

	/** A known exit. Its destination scene is deliberately not exposed. */
	public record KnownExit(String id, String zoneId) {
	}
}
