package com.leeburke.springgame.content.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

/** Structural checks shared by authored scene templates (archetypes and fixed scenes). */
final class SceneTemplates {

	private SceneTemplates() {
	}

	/**
	 * Zones and connections must form a valid scene layout (unique zones, connections between
	 * distinct existing zones), and every zone must be reachable from every other. Connectivity is
	 * checked explicitly; a valid SceneState does not imply it.
	 */
	static void requireValidConnectedLayout(String owner, List<SceneZone> zones, List<ZoneConnection> connections) {
		try {
			new SceneState(zones, connections, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(owner + " has an invalid zone layout: " + e.getMessage(), e);
		}
		Map<String, Set<String>> adjacency = new HashMap<>();
		zones.forEach(zone -> adjacency.put(zone.id(), new HashSet<>()));
		for (ZoneConnection connection : connections) {
			adjacency.get(connection.zoneA()).add(connection.zoneB());
			adjacency.get(connection.zoneB()).add(connection.zoneA());
		}
		Set<String> reached = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>(List.of(zones.getFirst().id()));
		while (!queue.isEmpty()) {
			String zone = queue.poll();
			if (reached.add(zone)) {
				queue.addAll(adjacency.get(zone));
			}
		}
		if (reached.size() != zones.size()) {
			throw new IllegalArgumentException(owner + " has zones not connected to the rest: "
					+ zones.stream().map(SceneZone::id).filter(id -> !reached.contains(id)).toList());
		}
	}

	static Set<String> zoneIds(List<SceneZone> zones) {
		Set<String> ids = new HashSet<>();
		zones.forEach(zone -> ids.add(zone.id()));
		return ids;
	}
}
