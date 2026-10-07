package com.leeburke.springgame.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.ItemCategory;
import com.leeburke.springgame.content.ItemDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;

/**
 * Generates a complete initial player character from a content catalogue and a caller-supplied
 * {@link RandomGenerator}. Owns the exact V1 generation policy; see docs/CHARACTER_GENERATION.md.
 * <p>
 * Every choice is uniform and independent within its pool (no affinity or coherence weighting yet).
 * Pools come from the catalogue, so content sizes are never assumed.
 * <p>
 * Random draws happen in this fixed order; changing it changes seeded results:
 * stats, Fated, weapon, passive, ability, recovery item, utility item, name.
 */
public final class PlayerCharacterGenerator {

	private final PlayerStatGenerator statGenerator;
	private final List<WeaponDefinition> weapons;
	private final List<PassiveDefinition> passives;
	private final List<AbilityDefinition> abilities;
	private final List<ItemDefinition> recoveryItems;
	private final List<ItemDefinition> utilityItems;
	private final List<String> names;

	/**
	 * @throws IllegalArgumentException if any pool required for generation is empty; the message
	 *                                  names every empty pool
	 */
	public PlayerCharacterGenerator(GameContentCatalog catalog, PlayerStatGenerator statGenerator) {
		Objects.requireNonNull(catalog, "catalog");
		this.statGenerator = Objects.requireNonNull(statGenerator, "statGenerator");
		this.weapons = catalog.weapons();
		this.passives = catalog.passives();
		this.abilities = catalog.abilities();
		this.recoveryItems = itemsIn(catalog, ItemCategory.RECOVERY);
		this.utilityItems = itemsIn(catalog, ItemCategory.UTILITY);
		this.names = catalog.characterNames();

		List<String> emptyPools = new ArrayList<>();
		addIfEmpty(emptyPools, "weapons", weapons);
		addIfEmpty(emptyPools, "passives", passives);
		addIfEmpty(emptyPools, "abilities", abilities);
		addIfEmpty(emptyPools, "RECOVERY items", recoveryItems);
		addIfEmpty(emptyPools, "UTILITY items", utilityItems);
		addIfEmpty(emptyPools, "character names", names);
		if (!emptyPools.isEmpty()) {
			throw new IllegalArgumentException(
					"Content catalogue cannot generate a player character; empty pools: " + String.join(", ", emptyPools));
		}
	}

	public GeneratedCharacter generate(RandomGenerator rng) {
		Objects.requireNonNull(rng, "rng");
		StatBlock stats = statGenerator.generate(rng);
		Fated fated = StartingCharacterRules.rollFated(rng);
		int maxHp = StartingCharacterRules.startingMaxHp(stats.get(StatType.RESOLVE));
		WeaponDefinition weapon = pick(weapons, rng);
		PassiveDefinition passive = pick(passives, rng);
		AbilityDefinition ability = pick(abilities, rng);
		ItemDefinition recoveryItem = pick(recoveryItems, rng);
		ItemDefinition utilityItem = pick(utilityItems, rng);
		PlayerBody body = PlayerBody.healthy();
		String name = pick(names, rng);

		ToolBelt toolBelt = new ToolBelt(List.of(
				new ToolBeltEntry.Weapon(weapon),
				new ToolBeltEntry.Item(recoveryItem),
				new ToolBeltEntry.Item(utilityItem)));
		return new GeneratedCharacter(name, stats, fated, maxHp, maxHp, body, passive, ability, toolBelt);
	}

	private static <T> T pick(List<T> pool, RandomGenerator rng) {
		return pool.get(rng.nextInt(pool.size()));
	}

	private static List<ItemDefinition> itemsIn(GameContentCatalog catalog, ItemCategory category) {
		return catalog.items().stream().filter(item -> item.category() == category).toList();
	}

	private static void addIfEmpty(List<String> emptyPools, String poolName, List<?> pool) {
		if (pool.isEmpty()) {
			emptyPools.add(poolName);
		}
	}
}
