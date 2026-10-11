package com.leeburke.springgame.content.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.shared.DefinitionCodes;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

/**
 * Authored structure from which a scene's contents are generated: fixed zones and connections,
 * the zones region exits may use, the one of them that is the scene's way in ({@code entranceZone}),
 * and optional content slots. No coordinates or grids.
 */
public record SceneArchetypeDefinition(
		String code,
		String displayName,
		List<SceneZone> zones,
		List<ZoneConnection> connections,
		List<String> exitZones,
		String entranceZone,
		List<ContentSlot> slots) {

	/** An archetype whose way in is its first exit zone. */
	public SceneArchetypeDefinition(String code, String displayName, List<SceneZone> zones, List<ZoneConnection> connections,
			List<String> exitZones, List<ContentSlot> slots) {
		this(code, displayName, zones, connections, exitZones, exitZones == null || exitZones.isEmpty() ? null : exitZones.getFirst(),
				slots);
	}

	public SceneArchetypeDefinition {
		DefinitionCodes.requireCode(code, "Archetype code");
		WorldText.requireDisplayName(displayName, code);
		zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
		connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
		SceneTemplates.requireValidConnectedLayout("Archetype " + code, zones, connections);
		Set<String> zoneIds = SceneTemplates.zoneIds(zones);

		exitZones = WorldText.uniqueList(exitZones, "exit zones of archetype " + code, true);
		for (String zone : exitZones) {
			requireZone(code, zoneIds, zone, "exit zone");
		}
		if (entranceZone == null || !exitZones.contains(entranceZone)) {
			throw new IllegalArgumentException("Archetype " + code + " entrance zone must be one of its exit zones");
		}
		slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
		Set<String> slotIds = new HashSet<>();
		for (ContentSlot slot : slots) {
			if (!slotIds.add(slot.id())) {
				throw new IllegalArgumentException("Archetype " + code + " has duplicate slot id " + slot.id());
			}
			slot.zones().forEach(zone -> requireZone(code, zoneIds, zone, "slot " + slot.id() + " zone"));
		}
	}

	private static void requireZone(String code, Set<String> zoneIds, String zone, String what) {
		if (!zoneIds.contains(zone)) {
			throw new IllegalArgumentException("Archetype " + code + " " + what + " refers to unknown zone " + zone);
		}
	}
}
