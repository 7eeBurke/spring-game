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
		List<String> discoveredFacts,
		List<VisibleContainer> containers) {

	/** A view with no containers. */
	public PlayerSceneView(String currentZoneId, List<VisibleZone> zones, List<VisibleConnection> connections,
			List<VisibleEntity> entities, List<VisibleObject> objects, List<VisibleHazard> hazards, List<KnownExit> exits,
			List<String> discoveredFacts) {
		this(currentZoneId, zones, connections, entities, objects, hazards, exits, discoveredFacts, List.of());
	}

	public PlayerSceneView {
		Objects.requireNonNull(currentZoneId, "currentZoneId");
		zones = List.copyOf(zones);
		connections = List.copyOf(connections);
		entities = List.copyOf(entities);
		objects = List.copyOf(objects);
		hazards = List.copyOf(hazards);
		exits = List.copyOf(exits);
		discoveredFacts = List.copyOf(discoveredFacts);
		containers = List.copyOf(containers);
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

	/**
	 * A visible object that is a container: whether it is open, and what it holds. The contents are
	 * listed only when it is open: a closed container's contents are hidden.
	 */
	/** This view with one container as it now is (replacing what was known of it). */
	public PlayerSceneView withContainer(VisibleContainer changed) {
		List<VisibleContainer> updated = new java.util.ArrayList<>(containers.stream()
				.filter(c -> !c.objectId().equals(changed.objectId())).toList());
		updated.add(changed);
		return new PlayerSceneView(currentZoneId, zones, connections, entities, objects, hazards, exits, discoveredFacts, updated);
	}

	public record VisibleContainer(String objectId, boolean open, List<String> contents) {
		public VisibleContainer {
			contents = List.copyOf(contents);
			if (!open && !contents.isEmpty()) {
				throw new IllegalArgumentException("A closed container's contents are not visible");
			}
		}
	}

	public java.util.Optional<VisibleContainer> container(String objectId) {
		return containers.stream().filter(c -> c.objectId().equals(objectId)).findFirst();
	}

	/**
	 * How many moves each zone in this view is from the given one, along the passages the view shows
	 * (the zone itself is 0). Zones with no shown path are absent.
	 */
	public java.util.Map<String, Integer> stepsFrom(String zoneId) {
		java.util.Map<String, Integer> distance = new java.util.HashMap<>();
		java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
		distance.put(zoneId, 0);
		queue.add(zoneId);
		while (!queue.isEmpty()) {
			String at = queue.poll();
			for (VisibleConnection c : connections) {
				String next = c.zoneA().equals(at) ? c.zoneB() : c.zoneB().equals(at) ? c.zoneA() : null;
				if (next != null && !distance.containsKey(next)) {
					distance.put(next, distance.get(at) + 1);
					queue.add(next);
				}
			}
		}
		return distance;
	}
}
