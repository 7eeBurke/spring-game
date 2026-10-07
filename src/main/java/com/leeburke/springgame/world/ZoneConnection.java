package com.leeburke.springgame.world;

/** An undirected movement possibility between two zones of one scene. */
public record ZoneConnection(String id, String zoneA, String zoneB) {

	public ZoneConnection {
		LocalIds.requireLocalId(id, "Connection id");
		LocalIds.requireLocalId(zoneA, "Connection zone");
		LocalIds.requireLocalId(zoneB, "Connection zone");
	}
}
