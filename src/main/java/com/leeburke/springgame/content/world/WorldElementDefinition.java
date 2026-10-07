package com.leeburke.springgame.content.world;

import java.util.Objects;

import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * Identity of something that can be placed in a generated scene (an enemy, object, hazard or
 * event). Identity only: no stats, behaviour or mechanics.
 */
public record WorldElementDefinition(String code, String displayName, WorldElementKind kind) {

	public WorldElementDefinition {
		DefinitionCodes.requireCode(code, "World element code");
		WorldText.requireDisplayName(displayName, code);
		Objects.requireNonNull(kind, "kind");
	}
}
