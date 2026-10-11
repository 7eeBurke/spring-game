package com.leeburke.springgame.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 * {@code containers} holds the state of placed container objects (open, and what they still hold).
 * {@code seenZones} is what the player has come to know of the scene: the zones they have stood in or
 * beside. Empty means everything is known, as for every scene stored before this was recorded.
 * {@code visitedZones} is where the player has actually stood. Empty means unknown (scenes stored
 * before it was recorded): nothing is then claimed about where the player has or has not been.
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
		List<String> discoveredFacts,
		List<ContainerState> containers,
		Optional<List<String>> seenZones,
		Optional<List<String>> visitedZones) {

	/** A scene whose visited zones are unknown (as stored before they were recorded). */
	public SceneState(List<SceneZone> zones, List<ZoneConnection> connections, List<SceneEntity> entities, List<SceneObject> objects,
			List<SceneHazard> hazards, List<SceneExit> exits, List<SceneEvent> activeEvents, List<String> environmentFlags,
			List<HiddenContentRef> hiddenContent, List<String> discoveredFacts, List<ContainerState> containers,
			Optional<List<String>> seenZones) {
		this(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent, discoveredFacts,
				containers, seenZones, Optional.empty());
	}

	/** A scene with no containers in which everything is known (as stored before either existed). */
	public SceneState(List<SceneZone> zones, List<ZoneConnection> connections, List<SceneEntity> entities, List<SceneObject> objects,
			List<SceneHazard> hazards, List<SceneExit> exits, List<SceneEvent> activeEvents, List<String> environmentFlags,
			List<HiddenContentRef> hiddenContent, List<String> discoveredFacts) {
		this(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent, discoveredFacts,
				List.of(), Optional.empty());
	}

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
		containers = copy(containers, "containers");
		seenZones = Objects.requireNonNull(seenZones, "seenZones").map(List::copyOf);
		visitedZones = Objects.requireNonNull(visitedZones, "visitedZones").map(List::copyOf);

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

		Set<String> containerIds = new HashSet<>();
		for (ContainerState container : containers) {
			if (!objectIds.contains(container.objectId())) {
				throw new IllegalArgumentException("Container state for unknown object " + container.objectId());
			}
			if (!containerIds.add(container.objectId())) {
				throw new IllegalArgumentException("Duplicate container state for object " + container.objectId());
			}
		}
		seenZones.ifPresent(seen -> {
			Set<String> unique = new HashSet<>();
			for (String zone : seen) {
				requireZone(zoneIds, zone, "Seen zone");
				if (!unique.add(zone)) {
					throw new IllegalArgumentException("Duplicate seen zone " + zone);
				}
			}
		});
		visitedZones.ifPresent(visited -> {
			Set<String> unique = new HashSet<>();
			for (String zone : visited) {
				requireZone(zoneIds, zone, "Visited zone");
				if (!unique.add(zone)) {
					throw new IllegalArgumentException("Duplicate visited zone " + zone);
				}
			}
		});

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

	public Optional<ContainerState> container(String objectId) {
		return containers.stream().filter(c -> c.objectId().equals(objectId)).findFirst();
	}

	/** Every scene recorded before seen zones existed knows its whole layout. */
	public boolean allSeen() {
		return seenZones.isEmpty();
	}

	/** This state with these zones added to what the player has seen (no change if everything is already known). */
	public SceneState seeing(java.util.Collection<String> zones) {
		if (seenZones.isEmpty()) {
			return this;
		}
		Set<String> seen = new LinkedHashSet<>(seenZones.get());
		if (!seen.addAll(zones)) {
			return this;
		}
		return new SceneState(this.zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags,
				hiddenContent, discoveredFacts, containers, Optional.of(List.copyOf(seen)), visitedZones);
	}

	/** This state with the player having stood in this zone (no change when visits are not recorded for this scene). */
	public SceneState visiting(String zone) {
		if (visitedZones.isEmpty() || visitedZones.get().contains(zone)) {
			return this;
		}
		List<String> visited = new ArrayList<>(visitedZones.get());
		visited.add(zone);
		return new SceneState(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent,
				discoveredFacts, containers, seenZones, Optional.of(List.copyOf(visited)));
	}

	/** This state with the whole layout known (as every scene stored before seen zones were recorded). */
	public SceneState allKnown() {
		return seenZones.isEmpty() ? this
				: new SceneState(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent,
						discoveredFacts, containers, Optional.empty(), visitedZones);
	}

	/** This state with one container's state replaced, or added (a scene stored before containers had state). */
	public SceneState withContainer(ContainerState changed) {
		List<ContainerState> updated = new ArrayList<>();
		boolean found = false;
		for (ContainerState container : containers) {
			if (container.objectId().equals(changed.objectId())) {
				updated.add(changed);
				found = true;
			} else {
				updated.add(container);
			}
		}
		if (!found) {
			updated.add(changed);
		}
		return new SceneState(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent,
				discoveredFacts, updated, seenZones, visitedZones);
	}

	/** This state with container states and empty records of seen and visited zones, as a newly generated scene starts. */
	public SceneState generated(List<ContainerState> withContainers) {
		return new SceneState(zones, connections, entities, objects, hazards, exits, activeEvents, environmentFlags, hiddenContent,
				discoveredFacts, withContainers, Optional.of(List.of()), Optional.of(List.of()));
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
