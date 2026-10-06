package com.leeburke.springgame.content;

import java.util.Objects;

/** Static definition of a recovery or utility item. Item effects and tags are deferred. */
public record ItemDefinition(String code, String displayName, ItemCategory category) {

	public ItemDefinition {
		DefinitionFields.requireCode(code);
		DefinitionFields.requireDisplayName(displayName);
		Objects.requireNonNull(category, "category");
	}
}
