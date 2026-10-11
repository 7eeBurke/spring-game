package com.leeburke.springgame.content;

import java.util.Objects;

/**
 * Static definition of a recovery or utility item: what it is called, its category, and a short
 * physical description (what it looks like in the hand, never what it does). Item effects and tags
 * are deferred.
 */
public record ItemDefinition(String code, String displayName, ItemCategory category, String description) {

	/** An item with no description (fixtures). */
	public ItemDefinition(String code, String displayName, ItemCategory category) {
		this(code, displayName, category, "");
	}

	public ItemDefinition {
		DefinitionFields.requireCode(code);
		DefinitionFields.requireDisplayName(displayName);
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(description, "description");
	}
}
