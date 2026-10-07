package com.leeburke.springgame.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * The authoritative dynamic contents of one scene. Immutable.
 * <p>
 * Scene identity, run, region, kind, definition code, seed, discovered flag and revision are
 * {@link SceneInstance} metadata and are deliberately not part of this state.
 * <p>
 * Hidden content is kept once in the normal collections and marked by {@link HiddenContentRef}s;
 * this state must never be given to the player or AI directly (see {@code world.view}).
 * <p>
 * Structural integrity is enforced here. Procedural quality (reachability, valid exit destinations,
 * region graph shape) is validated when a complete region is generated, not here. Scene history is
 * deferred until its entry schema is designed.
 */
public record SceneState(
		List<SceneZone> zones,
		List<ZoneConnection> connections,
		List<SceneEntity> entities,
		List<SceneObject> objects,
		List<SceneHazard> hazards,
		List<SceneExit> exits,
		List<SceneEvent> activeEvents,
		List<String> environmentFlags,
		List<HiddenContentRef> hiddenContent,
		List<String> discoveredFacts) {

	public SceneState {
		zones = copy(zones, "zones");
		connections = copy(connections, "connections");
		entities = copy(entities, "entities");
		objects = copy(objects, "objects");
		hazards = copy(hazards, "hazards");
		exits = copy(exits, "exits");
		activeEvents = copy(activeEvents, "activeEvents");
		environmentFlags = copy(environmentFlags, "environmentFlags");
		hiddenContent = copy(hiddenContent, "hiddenContent");
		discoveredFacts = copy(discoveredFacts, "discoveredFacts");

		if (zones.isEmpty()) {
			throw new IllegalArgumentException("A scene must have at least one zone");
		}
		Set<String> zoneIds = uniqueIds("zone", zones, SceneZone::id);
		Set<String> connectionIds = uniqueIds("connection", connections, ZoneConnection::id);
		Set<String> entityIds = uniqueIds("entity", entities, SceneEntity::id);
		Set<String> objectIds = uniqueIds("object", objects, SceneObject::id);
		Set<String> hazardIds = uniqueIds("hazard", hazards, SceneHazard::id);
		Set<String> exitIds = uniqueIds("exit", exits, SceneExit::id);
		Set<String> eventIds = uniqueIds("event", activeEvents, SceneEvent::id);

		Set<Set<String>> zonePairs = new HashSet<>();
		for (ZoneConnection connection : connections) {
			requireZone(zoneIds, connection.zoneA(), "Connection " + connection.id());
			requireZone(zoneIds, connection.zoneB(), "Connection " + connection.id());
			if (connection.zoneA().equals(connection.zoneB())) {
				throw new IllegalArgumentException("Connection " + connection.id() + " connects zone " + connection.zoneA() + " to itself");
			}
			if (!zonePairs.add(Set.of(connection.zoneA(), connection.zoneB()))) {
				throw new IllegalArgumentException("Duplicate connection between zones " + connection.zoneA() + " and " + connection.zoneB());
			}
		}
		entities.forEach(e -> requireZone(zoneIds, e.zoneId(), "Entity " + e.id()));
		objects.forEach(o -> requireZone(zoneIds, o.zoneId(), "Object " + o.id()));
		hazards.forEach(h -> requireZone(zoneIds, h.zoneId(), "Hazard " + h.id()));
		exits.forEach(x -> requireZone(zoneIds, x.zoneId(), "Exit " + x.id()));
		activeEvents.forEach(v -> requireZone(zoneIds, v.zoneId(), "Event " + v.id()));

		requireUniqueCodes("environment flag", environmentFlags);
		requireUniqueCodes("discovered fact", discoveredFacts);

		Set<HiddenContentRef> seenRefs = new HashSet<>();
		for (HiddenContentRef ref : hiddenContent) {
			Set<String> idsOfKind = switch (ref.kind()) {
				case ZONE -> zoneIds;
				case CONNECTION -> connectionIds;
				case ENTITY -> entityIds;
				case OBJECT -> objectIds;
				case HAZARD -> hazardIds;
				case EXIT -> exitIds;
				case EVENT -> eventIds;
			};
			if (!idsOfKind.contains(ref.localId())) {
				throw new IllegalArgumentException("Hidden reference " + ref + " does not identify any " + ref.kind() + " in the scene");
			}
			if (!seenRefs.add(ref)) {
				throw new IllegalArgumentException("Duplicate hidden reference " + ref);
			}
		}
	}

	public boolean hasZone(String zoneId) {
		return zones.stream().anyMatch(zone -> zone.id().equals(zoneId));
	}

	public boolean isHidden(HiddenContentKind kind, String localId) {
		return hiddenContent.contains(new HiddenContentRef(kind, localId));
	}

	private static <T> List<T> copy(List<T> list, String name) {
		return List.copyOf(Objects.requireNonNull(list, name));
	}

	private static <T> Set<String> uniqueIds(String type, List<T> items, Function<T, String> id) {
		Set<String> ids = new HashSet<>();
		for (T item : items) {
			if (!ids.add(id.apply(item))) {
				throw new IllegalArgumentException("Duplicate " + type + " id: " + id.apply(item));
			}
		}
		return ids;
	}

	private static void requireZone(Set<String> zoneIds, String zoneId, String owner) {
		if (!zoneIds.contains(zoneId)) {
			throw new IllegalArgumentException(owner + " refers to unknown zone " + zoneId);
		}
	}

	private static void requireUniqueCodes(String type, List<String> codes) {
		Set<String> seen = new HashSet<>();
		for (String code : codes) {
			DefinitionCodes.requireCode(code, "Scene " + type);
			if (!seen.add(code)) {
				throw new IllegalArgumentException("Duplicate " + type + ": " + code);
			}
		}
	}
}
