package com.leeburke.springgame.enemy;

import java.util.Objects;
import java.util.UUID;

/** An enemy instance with the scene it was placed in: together, its runtime identity. */
public record SceneEnemy(UUID sceneId, EnemyInstance enemy) {

	public SceneEnemy {
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(enemy, "enemy");
	}
}
