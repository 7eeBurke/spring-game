package com.leeburke.springgame.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.enemy.EnemyDefinition;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;

/**
 * Checks that an enemy roster matches its world: every enemy refers to a real entity of a world
 * scene with the same definition code, every entity with an enemy definition has exactly one
 * instance, no other entity has one, and each instance agrees with its definition, anatomy and a
 * known weapon. Used by generation tests and again before persisting.
 */
public final class EnemyRosterValidator {

	private final EnemyCatalog enemies;
	private final GameContentCatalog content;

	public EnemyRosterValidator(EnemyCatalog enemies, GameContentCatalog content) {
		this.enemies = Objects.requireNonNull(enemies, "enemies");
		this.content = Objects.requireNonNull(content, "content");
	}

	/** Empty when the roster is valid for the world. */
	public List<String> problems(GeneratedRunWorld world, GeneratedEnemyRoster roster) {
		List<String> problems = new ArrayList<>();
		Map<UUID, SceneInstance> scenes = Stream.concat(Stream.of(world.hub()), world.region().scenes().stream())
				.collect(Collectors.toMap(SceneInstance::id, Function.identity()));

		for (SceneEnemy entry : roster.enemies()) {
			EnemyInstance enemy = entry.enemy();
			SceneInstance scene = scenes.get(entry.sceneId());
			if (scene == null) {
				problems.add("Enemy " + enemy.entityId() + " belongs to unknown scene " + entry.sceneId());
				continue;
			}
			Optional<SceneEntity> entity = scene.state().entities().stream()
					.filter(e -> e.id().equals(enemy.entityId())).findFirst();
			if (entity.isEmpty()) {
				problems.add("Enemy " + enemy.entityId() + " has no entity in scene " + scene.id());
			} else if (!entity.get().definitionCode().equals(enemy.definitionCode())) {
				problems.add("Enemy " + enemy.entityId() + " is a " + enemy.definitionCode() + " but its entity is a "
						+ entity.get().definitionCode());
			}
			try {
				EnemyCombatant.of(enemy, enemies, content);
			} catch (IllegalArgumentException e) {
				problems.add(e.getMessage());
			}
		}

		for (SceneInstance scene : scenes.values()) {
			for (SceneEntity entity : scene.state().entities()) {
				Optional<EnemyDefinition> definition = enemies.findEnemy(entity.definitionCode());
				boolean hasInstance = roster.find(scene.id(), entity.id()).isPresent();
				if (definition.isPresent() && !hasInstance) {
					problems.add("Enemy entity " + entity.id() + " in scene " + scene.id() + " has no enemy state");
				}
				if (definition.isEmpty() && hasInstance) {
					problems.add("Entity " + entity.id() + " in scene " + scene.id() + " is not an enemy but has enemy state");
				}
			}
		}
		return problems;
	}
}
