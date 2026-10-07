package com.leeburke.springgame.persistence;

import java.util.Objects;
import java.util.UUID;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.enemy.EnemyBody;
import com.leeburke.springgame.enemy.EnemyCombatant;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

/**
 * Converts between {@link EnemyInstance} and {@link EnemyInstanceEntity}. Plain Java. Loading
 * returns the stored state as is (never regenerated from a seed) and checks it against static
 * content: a known definition and weapon, and a body matching the definition's anatomy. Any failure
 * raises {@link PersistedStateException}.
 */
final class EnemyInstanceMapper {

	private final EnemyCatalog enemies;
	private final GameContentCatalog content;

	EnemyInstanceMapper(EnemyCatalog enemies, GameContentCatalog content) {
		this.enemies = Objects.requireNonNull(enemies, "enemies");
		this.content = Objects.requireNonNull(content, "content");
	}

	EnemyInstanceEntity toEntity(UUID sceneId, EnemyInstance enemy) {
		EnemyCombatant.of(enemy, enemies, content);
		StatBlock stats = enemy.stats();
		return new EnemyInstanceEntity(new EnemyInstanceId(sceneId, enemy.entityId()), enemy.definitionCode(),
				stats.might().value(), stats.agility().value(), stats.perception().value(), stats.arcana().value(),
				stats.resolve().value(), enemy.maxHp(), enemy.currentHp(), enemy.weaponCode(), enemy.body().severities());
	}

	EnemyInstance toDomain(EnemyInstanceEntity entity) {
		EnemyInstanceId id = entity.getId();
		try {
			EnemyInstance enemy = new EnemyInstance(id.getEntityLocalId(), entity.getDefinitionCode(),
					new StatBlock(new StatValue(entity.getMight()), new StatValue(entity.getAgility()),
							new StatValue(entity.getPerception()), new StatValue(entity.getArcana()),
							new StatValue(entity.getResolve())),
					entity.getMaxHp(), entity.getCurrentHp(), new EnemyBody(entity.getBodyParts()), entity.getWeaponCode());
			EnemyCombatant.of(enemy, enemies, content);
			return enemy;
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new PersistedStateException("Enemy " + id.getEntityLocalId() + " in scene " + id.getSceneId()
					+ " has invalid stored state: " + e.getMessage(), e);
		}
	}
}
