package com.leeburke.springgame.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.ScenePlacement;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;
import com.leeburke.springgame.world.generation.GenerationException;
import com.leeburke.springgame.world.generation.WorldRandom;

/**
 * Generates the enemy state of every enemy placement in a newly generated world, before anything
 * is persisted; nothing is generated lazily on scene entry. Hidden placements are included. An
 * entity whose code has no enemy definition gets no instance.
 * <p>
 * Each enemy uses its own stream, {@link WorldRandom#entitySeed(long, String)} of its scene's seed
 * and its local ID, so the result does not depend on generation order or on other enemies.
 */
public final class EnemyRosterGenerator {

	private final EnemyCatalog catalog;
	private final EnemyGenerator generator;

	public EnemyRosterGenerator(EnemyCatalog catalog) {
		this.catalog = Objects.requireNonNull(catalog, "catalog");
		this.generator = new EnemyGenerator(catalog);
	}

	public GeneratedEnemyRoster generate(GeneratedRunWorld world) {
		Objects.requireNonNull(world, "world");
		List<SceneEnemy> enemies = new ArrayList<>();
		for (SceneEntity entity : world.hub().state().entities()) {
			if (catalog.findEnemy(entity.definitionCode()).isPresent()) {
				throw new GenerationException("The hub has no scene seed, so it cannot hold enemy " + entity.id());
			}
		}
		for (SceneInstance scene : world.region().scenes()) {
			long sceneSeed = ((ScenePlacement.Region) scene.placement()).sceneSeed();
			for (SceneEntity entity : scene.state().entities()) {
				Optional<EnemyDefinition> definition = catalog.findEnemy(entity.definitionCode());
				if (definition.isPresent()) {
					EnemyInstance enemy = generator.generate(definition.get(), entity.id(),
							WorldRandom.create(WorldRandom.entitySeed(sceneSeed, entity.id())));
					enemies.add(new SceneEnemy(scene.id(), enemy));
				}
			}
		}
		return new GeneratedEnemyRoster(enemies);
	}
}
