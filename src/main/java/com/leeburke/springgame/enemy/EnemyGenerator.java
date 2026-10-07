package com.leeburke.springgame.enemy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.character.StatShape;
import com.leeburke.springgame.content.enemy.AnatomyDefinition;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.content.enemy.EnemyStatRule;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.StatValue;

/**
 * Generates one enemy's starting mechanical state from its definition and its own random stream.
 * <p>
 * Draw order: a normal enemy draws one shape index; a fixed-stat enemy (the boss) draws nothing.
 * HP, body and weapon involve no randomness. Fated is not used.
 */
public final class EnemyGenerator {

	private final EnemyCatalog catalog;

	public EnemyGenerator(EnemyCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
	}

	public EnemyInstance generate(EnemyDefinition definition, String entityId, RandomGenerator rng) {
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(rng, "rng");
		if (!definition.equals(catalog.findEnemy(definition.code()).orElse(null))) {
			throw new IllegalArgumentException("Enemy definition " + definition.code() + " is not the catalogued one");
		}
		AnatomyDefinition anatomy = catalog.anatomyOf(definition);

		StatBlock stats = switch (definition.statRule()) {
			case EnemyStatRule.ShapePriority rule -> assign(drawShape(rng), rule.priority());
			case EnemyStatRule.Fixed rule -> rule.stats();
		};
		int maxHp = definition.maxHpAt(stats.resolve());
		return new EnemyInstance(entityId, definition.code(), stats, maxHp, maxHp, EnemyBody.healthy(anatomy),
				definition.weapon());
	}

	private static StatShape drawShape(RandomGenerator rng) {
		List<StatShape> shapes = EnemyRules.enemyShapes();
		return shapes.get(rng.nextInt(shapes.size()));
	}

	/** Highest value to the first priority stat, and so on down. */
	static StatBlock assign(StatShape shape, List<StatType> priority) {
		List<Integer> descending = shape.values().stream().sorted((a, b) -> Integer.compare(b, a)).toList();
		Map<StatType, Integer> values = new EnumMap<>(StatType.class);
		for (int i = 0; i < priority.size(); i++) {
			values.put(priority.get(i), descending.get(i));
		}
		return new StatBlock(new StatValue(values.get(StatType.MIGHT)), new StatValue(values.get(StatType.AGILITY)),
				new StatValue(values.get(StatType.PERCEPTION)), new StatValue(values.get(StatType.ARCANA)),
				new StatValue(values.get(StatType.RESOLVE)));
	}
}
