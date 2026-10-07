package com.leeburke.springgame.action.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.WeaponDefinition;

/**
 * The player's currently usable weapons, abilities and items, keyed by opaque references valid
 * only for the current action context.
 * <p>
 * Keys are not definition codes and not persistent instance IDs; two keys may map to the same
 * definition (for example two bandages). How keys are minted and shown to the interpreter is
 * decided with AI integration. Owning a reference here, not merely the definition existing in
 * the content catalogue, is what makes it usable.
 */
public record PlayerActionReferences(
		Map<String, WeaponDefinition> weapons,
		Map<String, AbilityDefinition> abilities,
		Map<String, ItemDefinition> items) {

	public PlayerActionReferences {
		weapons = copy(weapons, "weapon");
		abilities = copy(abilities, "ability");
		items = copy(items, "item");
	}

	public static PlayerActionReferences none() {
		return new PlayerActionReferences(Map.of(), Map.of(), Map.of());
	}

	private static <T> Map<String, T> copy(Map<String, T> source, String kind) {
		Objects.requireNonNull(source, kind + " references");
		Map<String, T> copy = new LinkedHashMap<>();
		source.forEach((ref, definition) -> copy.put(Refs.require(ref, "Player " + kind + " reference"),
				Objects.requireNonNull(definition, "definition for " + kind + " reference " + ref)));
		return Collections.unmodifiableMap(copy);
	}
}
