package com.leeburke.springgame.run.initialization;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.leeburke.springgame.enemy.GeneratedEnemyRoster;
import com.leeburke.springgame.enemy.SceneEnemy;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;

/**
 * Everything generated for a new run before play begins, persisted in one transaction: the world
 * and the starting state of every enemy placed in it. Enemy state is never generated later, on
 * scene entry. Full agreement between roster and world is checked by {@code EnemyRosterValidator}.
 */
public record RunInitialization(GeneratedRunWorld world, GeneratedEnemyRoster enemies) {

	public RunInitialization {
		Objects.requireNonNull(world, "world");
		Objects.requireNonNull(enemies, "enemies");
		Set<UUID> sceneIds = new HashSet<>(world.region().sceneIds());
		sceneIds.add(world.hub().id());
		for (SceneEnemy enemy : enemies.enemies()) {
			if (!sceneIds.contains(enemy.sceneId())) {
				throw new IllegalArgumentException("Enemy " + enemy.enemy().entityId() + " belongs to scene " + enemy.sceneId()
						+ ", which is not part of run " + world.runId());
			}
		}
	}

	public UUID runId() {
		return world.runId();
	}
}
