package com.leeburke.springgame.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


/**
 * What the player knows of a scene, and how far things are, from the authoritative state and only
 * player-known passages (hidden zones and connections never count):
 * <ul>
 * <li><b>perceivable</b>: the zone they stand in and the zones joined to it by a visible connection;</li>
 * <li><b>known</b>: everything they have perceived in this scene so far (a scene stored before this
 * was recorded is known whole);</li>
 * <li><b>steps</b>: how many moves away a zone is along known passages, or absent when there is none.</li>
 * </ul>
 * No line of sight is simulated.
 */
public final class SceneKnowledge {

	private SceneKnowledge() {
	}

	/** Here, and the zones joined to it by a connection the player can see. */
	public static Set<String> perceivable(SceneState state, String zoneId) {
		Set<String> zones = new LinkedHashSet<>();
		zones.add(zoneId);
		for (ZoneConnection connection : visibleConnections(state)) {
			if (connection.zoneA().equals(zoneId)) {
				zones.add(connection.zoneB());
			} else if (connection.zoneB().equals(zoneId)) {
				zones.add(connection.zoneA());
			}
		}
		zones.removeIf(zone -> state.isHidden(HiddenContentKind.ZONE, zone));
		return zones;
	}

	/** What the player knows of the scene while standing in this zone. */
	public static Set<String> knownZones(SceneState state, String zoneId) {
		if (state.allSeen()) {
			Set<String> all = new LinkedHashSet<>();
			state.zones().stream().map(SceneZone::id).filter(z -> !state.isHidden(HiddenContentKind.ZONE, z)).forEach(all::add);
			return all;
		}
		Set<String> known = new LinkedHashSet<>(state.seenZones().orElseThrow());
		known.addAll(perceivable(state, zoneId));
		return known;
	}

	/**
	 * How many moves each known zone is from this one along known passages (here is 0). A zone with
	 * no known path is absent: known, perhaps, but not reachable as far as the player can tell.
	 */
	public static Map<String, Integer> steps(SceneState state, String zoneId) {
		return distances(state, zoneId, knownZones(state, zoneId));
	}

	/** The first move on a shortest path the player knows (from where they stand) toward another zone. */
	public static Optional<String> nextStepToward(SceneState state, String from, String to) {
		if (from.equals(to)) {
			return Optional.empty();
		}
		Set<String> known = knownZones(state, from);
		Map<String, Integer> fromTarget = distances(state, to, known);
		if (!known.contains(to) || !fromTarget.containsKey(from)) {
			return Optional.empty();
		}
		return visibleConnections(state).stream()
				.map(c -> c.zoneA().equals(from) ? c.zoneB() : c.zoneB().equals(from) ? c.zoneA() : null)
				.filter(Objects::nonNull)
				.filter(zone -> fromTarget.containsKey(zone) && fromTarget.get(zone) == fromTarget.get(from) - 1)
				.sorted()
				.findFirst();
	}

	/** Moves from a zone to each zone of {@code within} along visible connections, never leaving {@code within}. */
	private static Map<String, Integer> distances(SceneState state, String zoneId, Set<String> within) {
		Map<String, Integer> distance = new HashMap<>();
		Deque<String> queue = new ArrayDeque<>();
		distance.put(zoneId, 0);
		queue.add(zoneId);
		while (!queue.isEmpty()) {
			String at = queue.poll();
			for (ZoneConnection connection : visibleConnections(state)) {
				Optional<String> next = connection.zoneA().equals(at) ? Optional.of(connection.zoneB())
						: connection.zoneB().equals(at) ? Optional.of(connection.zoneA()) : Optional.empty();
				next.filter(within::contains).filter(zone -> !distance.containsKey(zone)).ifPresent(zone -> {
					distance.put(zone, distance.get(at) + 1);
					queue.add(zone);
				});
			}
		}
		return distance;
	}

	private static java.util.List<ZoneConnection> visibleConnections(SceneState state) {
		return state.connections().stream().filter(c -> !state.isHidden(HiddenContentKind.CONNECTION, c.id())).toList();
	}
}
