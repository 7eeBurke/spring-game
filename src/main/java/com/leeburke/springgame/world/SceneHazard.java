package com.leeburke.springgame.world;

import com.leeburke.springgame.shared.DefinitionCodes;

/** Placement of a hazard. Identity and placement only; hazard mechanics come later. */
public record SceneHazard(String id, String definitionCode, String zoneId) {

	public SceneHazard {
		LocalIds.requireLocalId(id, "Hazard id");
		DefinitionCodes.requireCode(definitionCode, "Hazard definition code");
		LocalIds.requireLocalId(zoneId, "Hazard zone");
	}
}
