package com.leeburke.springgame.world;

import com.leeburke.springgame.shared.DefinitionCodes;

/** Placement of an interactable or environment object. Identity and placement only; mechanics come later. */
public record SceneObject(String id, String definitionCode, String zoneId) {

	public SceneObject {
		LocalIds.requireLocalId(id, "Object id");
		DefinitionCodes.requireCode(definitionCode, "Object definition code");
		LocalIds.requireLocalId(zoneId, "Object zone");
	}
}
