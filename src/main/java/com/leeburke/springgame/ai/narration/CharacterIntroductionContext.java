package com.leeburke.springgame.ai.narration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBeltEntry;
import com.leeburke.springgame.mechanics.StatType;

/**
 * Everything the Character Introduction Narrator receives: the generated character's confirmed
 * facts and the fixed lore. The Fated value is visible to the player anyway; its band is a
 * narration label. No probabilities, HP or persistence identities.
 */
public record CharacterIntroductionContext(String name, Map<StatType, Integer> stats, int fatedValue, FatedBand fatedBand,
		List<String> weapons, String passive, String ability, List<String> items, List<String> lore) {

	public CharacterIntroductionContext {
		Objects.requireNonNull(name, "name");
		stats = Map.copyOf(new EnumMap<>(Objects.requireNonNull(stats, "stats")));
		Objects.requireNonNull(fatedBand, "fatedBand");
		weapons = List.copyOf(weapons);
		Objects.requireNonNull(passive, "passive");
		Objects.requireNonNull(ability, "ability");
		items = List.copyOf(items);
		lore = List.copyOf(lore);
	}

	public static CharacterIntroductionContext from(PlayerCharacterState character, LoreCatalog lore) {
		Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
		for (StatType stat : StatType.values()) {
			stats.put(stat, character.stats().get(stat).value());
		}
		List<String> weapons = new ArrayList<>();
		List<String> items = new ArrayList<>();
		for (ToolBeltEntry entry : character.toolBelt().entries()) {
			switch (entry) {
				case ToolBeltEntry.Weapon weapon -> weapons.add(weapon.definition().displayName());
				case ToolBeltEntry.Item item -> items.add(item.definition().displayName());
			}
		}
		int fated = character.fated().value();
		return new CharacterIntroductionContext(character.name(), stats, fated, FatedBand.of(fated), weapons,
				character.passive().displayName(), character.ability().displayName(), items,
				java.util.stream.Stream.concat(lore.premise().stream(), java.util.stream.Stream.of(lore.objective())).toList());
	}
}
