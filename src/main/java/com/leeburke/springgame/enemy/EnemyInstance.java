package com.leeburke.springgame.enemy;

import java.util.Objects;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * The mechanical state of one placed enemy in this run. Immutable.
 * <p>
 * Identity is the scene plus {@code entityId}, the scene-local ID of its {@code SceneEntity}; there
 * is no separate UUID. Placement (zone, visibility) stays in {@code SceneState}. Traits, behaviour
 * and anatomy are static definition data and are not copied here. The weapon code is instance
 * state so a future mechanic (for example a disarm) could change it.
 * <p>
 * Reduced HP and injured parts are representable; applying damage is deferred.
 */
public record EnemyInstance(
		String entityId,
		String definitionCode,
		StatBlock stats,
		int maxHp,
		int currentHp,
		EnemyBody body,
		String weaponCode) {

	public EnemyInstance {
		Refs.require(entityId, "Enemy entity id");
		DefinitionCodes.requireCode(definitionCode, "Enemy definition code");
		Objects.requireNonNull(stats, "stats");
		if (maxHp < 1) {
			throw new IllegalArgumentException("Enemy max HP must be at least 1, but was " + maxHp);
		}
		if (currentHp < 0 || currentHp > maxHp) {
			throw new IllegalArgumentException("Enemy current HP must be between 0 and " + maxHp + ", but was " + currentHp);
		}
		Objects.requireNonNull(body, "body");
		DefinitionCodes.requireCode(weaponCode, "Enemy weapon code");
	}
}
