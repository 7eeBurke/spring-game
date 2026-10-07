package com.leeburke.springgame.world;

import com.leeburke.springgame.shared.DefinitionCodes;

/** An active event in a scene. Identity and placement only; event choices and rewards come later. */
public record SceneEvent(String id, String definitionCode, String zoneId) {

	public SceneEvent {
		LocalIds.requireLocalId(id, "Event id");
		DefinitionCodes.requireCode(definitionCode, "Event definition code");
		LocalIds.requireLocalId(zoneId, "Event zone");
	}
}
