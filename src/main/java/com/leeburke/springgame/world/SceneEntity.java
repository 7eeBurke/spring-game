package com.leeburke.springgame.world;

import com.leeburke.springgame.shared.DefinitionCodes;

/** Placement of an entity in a scene. Identity and placement only; enemy state belongs to a later stage. */
public record SceneEntity(String id, String definitionCode, String zoneId) {

	public SceneEntity {
		LocalIds.requireLocalId(id, "Entity id");
		DefinitionCodes.requireCode(definitionCode, "Entity definition code");
		LocalIds.requireLocalId(zoneId, "Entity zone");
	}
}
