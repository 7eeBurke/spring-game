package com.leeburke.springgame.content.world;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.leeburke.springgame.shared.DefinitionCodes;
import com.leeburke.springgame.world.LocalIds;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;

/**
 * An authored, non-procedural scene such as {@code THE_LAST_LANTERN}. Its single exit leads into
 * the entry scene of the region named by {@code exit.destinationRegion}.
 */
public record FixedSceneDefinition(
		String code,
		String displayName,
		List<SceneZone> zones,
		List<ZoneConnection> connections,
		String startZone,
		FixedSceneExit exit) {

	public record FixedSceneExit(String id, String zoneId, String destinationRegion) {
		public FixedSceneExit {
			LocalIds.requireLocalId(id, "Fixed scene exit id");
			LocalIds.requireLocalId(zoneId, "Fixed scene exit zone");
			DefinitionCodes.requireCode(destinationRegion, "Fixed scene exit destination region");
		}
	}

	public FixedSceneDefinition {
		DefinitionCodes.requireCode(code, "Fixed scene code");
		WorldText.requireDisplayName(displayName, code);
		zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
		connections = List.copyOf(Objects.requireNonNull(connections, "connections"));
		SceneTemplates.requireValidConnectedLayout("Fixed scene " + code, zones, connections);
		Objects.requireNonNull(exit, "exit");
		Set<String> zoneIds = SceneTemplates.zoneIds(zones);
		LocalIds.requireLocalId(startZone, "Fixed scene start zone");
		if (!zoneIds.contains(startZone)) {
			throw new IllegalArgumentException("Fixed scene " + code + " start zone " + startZone + " does not exist");
		}
		if (!zoneIds.contains(exit.zoneId())) {
			throw new IllegalArgumentException("Fixed scene " + code + " exit zone " + exit.zoneId() + " does not exist");
		}
	}
}
