package com.leeburke.springgame.character;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.StatBlock;

/**
 * A newly generated player character's confirmed initial mechanical state. In memory only.
 * <p>
 * This type enforces structural validity only:
 * <ul>
 * <li>no null components and a non-blank name;</li>
 * <li>{@code maxHp >= 1} and a new character at full health ({@code currentHp == maxHp});</li>
 * <li>a starting tool belt of exactly one weapon, one RECOVERY item and one UTILITY item.</li>
 * </ul>
 * The exact V1 generation policy (the Resolve-based HP formula, an all-healthy body, Fated
 * distribution, uniform selection) belongs to {@link PlayerCharacterGenerator}. It is deliberately
 * not enforced here, so future Fated starting modifications need no model change.
 * <p>
 * The passive and ability do not occupy tool-belt slots.
 */
public record GeneratedCharacter(
		String name,
		StatBlock stats,
		Fated fated,
		int maxHp,
		int currentHp,
		PlayerBody body,
		PassiveDefinition passive,
		AbilityDefinition ability,
		ToolBelt toolBelt) {

	public GeneratedCharacter {
		Objects.requireNonNull(name, "name");
		if (name.isBlank()) {
			throw new IllegalArgumentException("Character name must not be blank");
		}
		Objects.requireNonNull(stats, "stats");
		Objects.requireNonNull(fated, "fated");
		Objects.requireNonNull(body, "body");
		Objects.requireNonNull(passive, "passive");
		Objects.requireNonNull(ability, "ability");
		Objects.requireNonNull(toolBelt, "toolBelt");
		if (maxHp < 1) {
			throw new IllegalArgumentException("Max HP must be at least 1, but was " + maxHp);
		}
		if (currentHp != maxHp) {
			throw new IllegalArgumentException(
					"A generated character starts at full health, but current HP " + currentHp + " != max HP " + maxHp);
		}
		requireStartingToolBelt(toolBelt);
	}

	public WeaponDefinition weapon() {
		return weapons(toolBelt).getFirst();
	}

	public ItemDefinition recoveryItem() {
		return items(toolBelt, ItemCategory.RECOVERY).getFirst();
	}

	public ItemDefinition utilityItem() {
		return items(toolBelt, ItemCategory.UTILITY).getFirst();
	}

	private static void requireStartingToolBelt(ToolBelt toolBelt) {
		int weapons = weapons(toolBelt).size();
		int recovery = items(toolBelt, ItemCategory.RECOVERY).size();
		int utility = items(toolBelt, ItemCategory.UTILITY).size();
		if (toolBelt.occupiedSlots() != 3 || weapons != 1 || recovery != 1 || utility != 1) {
			throw new IllegalArgumentException("A starting tool belt holds exactly one weapon, one RECOVERY item and one UTILITY item,"
					+ " but had " + weapons + " weapon(s), " + recovery + " recovery, " + utility + " utility");
		}
	}

	private static List<WeaponDefinition> weapons(ToolBelt toolBelt) {
		return toolBelt.entries().stream()
				.filter(ToolBeltEntry.Weapon.class::isInstance)
				.map(entry -> ((ToolBeltEntry.Weapon) entry).definition())
				.toList();
	}

	private static List<ItemDefinition> items(ToolBelt toolBelt, ItemCategory category) {
		return toolBelt.entries().stream()
				.filter(ToolBeltEntry.Item.class::isInstance)
				.map(entry -> ((ToolBeltEntry.Item) entry).definition())
				.filter(item -> item.category() == category)
				.toList();
	}
}
