package com.leeburke.springgame.enemy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every enemy generated for a new run's world, in memory before persistence. At most one instance
 * per {@code (sceneId, entityId)}. Agreement with the world itself is checked by
 * {@link EnemyRosterValidator}.
 */
public record GeneratedEnemyRoster(List<SceneEnemy> enemies) {

	public GeneratedEnemyRoster {
		enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
		Set<List<Object>> keys = new HashSet<>();
		for (SceneEnemy enemy : enemies) {
			if (!keys.add(List.of(enemy.sceneId(), enemy.enemy().entityId()))) {
				throw new IllegalArgumentException("Duplicate enemy " + enemy.enemy().entityId() + " in scene " + enemy.sceneId());
			}
		}
	}

	public Optional<EnemyInstance> find(UUID sceneId, String entityId) {
		return enemies.stream()
				.filter(e -> e.sceneId().equals(sceneId) && e.enemy().entityId().equals(entityId))
				.map(SceneEnemy::enemy)
				.findFirst();
	}

	public List<EnemyInstance> inScene(UUID sceneId) {
		return enemies.stream().filter(e -> e.sceneId().equals(sceneId)).map(SceneEnemy::enemy).toList();
	}
}
