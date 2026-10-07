package com.leeburke.springgame.character;

import java.util.Objects;

import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.WeaponDefinition;

/**
 * What occupies one tool-belt slot: a weapon or an item, referenced by its static definition.
 * No runtime instance IDs; their format is deferred.
 */
public sealed interface ToolBeltEntry {

	record Weapon(WeaponDefinition definition) implements ToolBeltEntry {
		public Weapon {
			Objects.requireNonNull(definition, "definition");
		}
	}

	record Item(ItemDefinition definition) implements ToolBeltEntry {
		public Item {
			Objects.requireNonNull(definition, "definition");
		}
	}
}
