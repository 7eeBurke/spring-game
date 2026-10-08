package com.leeburke.springgame.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.enemy.EnemyInstance;

/**
 * Reads persisted enemy state and applies confirmed HP changes. Enemies are created only by
 * {@link WorldStore#initializeWorld}, with the rest of the world, and loaded exactly as stored.
 */
@Service
public class EnemyStore {

	private final EntityManager entityManager;
	private final EnemyInstanceMapper mapper;

	public EnemyStore(EntityManager entityManager, EnemyCatalog enemies, GameContentCatalog content) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
		this.mapper = new EnemyInstanceMapper(enemies, content);
	}

	/** The scene's enemies ordered by entity ID, independent of row order. */
	@Transactional(readOnly = true)
	public List<EnemyInstance> findEnemies(UUID sceneId) {
		Objects.requireNonNull(sceneId, "sceneId");
		return entityManager
				.createQuery("SELECT e FROM EnemyInstanceEntity e WHERE e.id.sceneId = :sceneId", EnemyInstanceEntity.class)
				.setParameter("sceneId", sceneId)
				.getResultList().stream()
				.map(mapper::toDomain)
				.sorted(Comparator.comparing(EnemyInstance::entityId))
				.toList();
	}

	/**
	 * Sets an enemy's current HP; at 0 it is fallen. Joins the caller's transaction (a turn's
	 * mechanics are applied in one).
	 *
	 * @throws IllegalArgumentException if the enemy does not exist or the HP is outside 0 to its max
	 */
	@Transactional
	public void updateHp(UUID sceneId, String entityId, int currentHp) {
		EnemyInstanceEntity entity = entityManager.find(EnemyInstanceEntity.class, new EnemyInstanceId(sceneId, entityId));
		if (entity == null) {
			throw new IllegalArgumentException("No enemy " + entityId + " in scene " + sceneId);
		}
		if (currentHp < 0 || currentHp > entity.getMaxHp()) {
			throw new IllegalArgumentException("Enemy HP must be between 0 and " + entity.getMaxHp());
		}
		entity.setCurrentHp(currentHp);
	}

	@Transactional(readOnly = true)
	public Optional<EnemyInstance> findEnemy(UUID sceneId, String entityId) {
		EnemyInstanceEntity entity = entityManager.find(EnemyInstanceEntity.class, new EnemyInstanceId(sceneId, entityId));
		return Optional.ofNullable(entity).map(mapper::toDomain);
	}
}
