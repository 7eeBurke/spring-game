package com.leeburke.springgame.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.RegionInstance;
import com.leeburke.springgame.world.SceneInstance;
import com.leeburke.springgame.world.ScenePlacement;
import com.leeburke.springgame.world.SceneState;

/**
 * Persistence facade for world state: regions, scenes and the run's player location. Callers work
 * with {@code world} domain types; entities never leave this package.
 * <p>
 * Scene state updates are optimistic: callers pass the revision they resolved against, and a stale
 * revision raises {@link StaleSceneStateException} instead of overwriting newer state.
 */
@Service
public class WorldStore {

	private final EntityManager entityManager;
	private final SceneStateCodec codec = new SceneStateCodec();

	public WorldStore(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
	}

	@Transactional
	public void persistRegion(RegionInstance region) {
		Objects.requireNonNull(region, "region");
		entityManager.persist(new RegionInstanceEntity(region.id(), region.runId(), region.definitionCode()));
	}

	@Transactional(readOnly = true)
	public Optional<RegionInstance> findRegion(UUID regionId) {
		Objects.requireNonNull(regionId, "regionId");
		RegionInstanceEntity entity = entityManager.find(RegionInstanceEntity.class, regionId);
		return Optional.ofNullable(entity)
				.map(e -> new RegionInstance(e.getId(), e.getRunId(), e.getDefinitionCode()));
	}

	/**
	 * Persists a new scene. Its revision must be 0.
	 *
	 * @throws IllegalArgumentException if the revision is not 0, or a region scene's region does not
	 *                                  exist or belongs to a different run
	 */
	@Transactional
	public void persistScene(SceneInstance scene) {
		Objects.requireNonNull(scene, "scene");
		if (scene.revision() != 0) {
			throw new IllegalArgumentException("A new scene starts at revision 0, but scene " + scene.id() + " had " + scene.revision());
		}
		UUID regionId = null;
		Long seed = null;
		if (scene.placement() instanceof ScenePlacement.Region(UUID region, long sceneSeed)) {
			RegionInstanceEntity regionEntity = entityManager.find(RegionInstanceEntity.class, region);
			if (regionEntity == null || !regionEntity.getRunId().equals(scene.runId())) {
				throw new IllegalArgumentException("Scene " + scene.id() + " refers to region " + region
						+ ", which does not exist in run " + scene.runId());
			}
			regionId = region;
			seed = sceneSeed;
		}
		entityManager.persist(new SceneInstanceEntity(scene.id(), scene.runId(), scene.kind(), regionId,
				scene.definitionCode(), seed, scene.discovered(), SceneStateCodec.CURRENT_SCHEMA_VERSION,
				codec.encode(scene.state())));
	}

	/**
	 * @throws PersistedStateException if the stored scene state is corrupt or has an unsupported version
	 */
	@Transactional(readOnly = true)
	public Optional<SceneInstance> findScene(UUID sceneId) {
		Objects.requireNonNull(sceneId, "sceneId");
		return Optional.ofNullable(entityManager.find(SceneInstanceEntity.class, sceneId)).map(this::toDomain);
	}

	/**
	 * Replaces a scene's state if it is still at {@code expectedRevision}, incrementing the revision.
	 *
	 * @return the updated scene, with its new revision
	 * @throws IllegalArgumentException  if the scene does not exist
	 * @throws StaleSceneStateException if the scene is no longer at {@code expectedRevision}, or a
	 *                                   concurrent update committed first; nothing is written
	 */
	@Transactional
	public SceneInstance updateSceneState(UUID sceneId, long expectedRevision, SceneState newState) {
		Objects.requireNonNull(sceneId, "sceneId");
		Objects.requireNonNull(newState, "newState");
		SceneInstanceEntity entity = entityManager.find(SceneInstanceEntity.class, sceneId);
		if (entity == null) {
			throw new IllegalArgumentException("Unknown scene " + sceneId);
		}
		if (entity.getRevision() != expectedRevision) {
			throw new StaleSceneStateException(sceneId, expectedRevision, "it is at revision " + entity.getRevision(), null);
		}
		entity.replaceState(SceneStateCodec.CURRENT_SCHEMA_VERSION, codec.encode(newState));
		try {
			entityManager.flush(); // UPDATE ... WHERE id = ? AND revision = ?
		} catch (OptimisticLockException e) {
			throw new StaleSceneStateException(sceneId, expectedRevision, "a concurrent update committed first", e);
		}
		return toDomain(entity);
	}

	@Transactional(readOnly = true)
	public Optional<PlayerLocation> findPlayerLocation(UUID runId) {
		Objects.requireNonNull(runId, "runId");
		return Optional.ofNullable(entityManager.find(RunWorldStateEntity.class, runId))
				.map(e -> new PlayerLocation(e.getCurrentSceneId(), e.getCurrentZoneId()));
	}

	/**
	 * Sets the run's current player location, creating it if the run has none yet.
	 *
	 * @throws IllegalArgumentException if the scene does not exist or belongs to another run, or the
	 *                                  zone does not exist in that scene or is hidden
	 */
	@Transactional
	public void setPlayerLocation(UUID runId, PlayerLocation location) {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(location, "location");
		SceneInstanceEntity scene = entityManager.find(SceneInstanceEntity.class, location.sceneId());
		if (scene == null || !scene.getRunId().equals(runId)) {
			throw new IllegalArgumentException("Scene " + location.sceneId() + " does not exist in run " + runId);
		}
		SceneState state = codec.decode(scene.getId(), scene.getStateSchemaVersion(), scene.getState());
		if (!state.hasZone(location.zoneId())) {
			throw new IllegalArgumentException("Zone " + location.zoneId() + " does not exist in scene " + location.sceneId());
		}
		if (state.isHidden(HiddenContentKind.ZONE, location.zoneId())) {
			throw new IllegalArgumentException("Zone " + location.zoneId() + " in scene " + location.sceneId()
					+ " is hidden; the player cannot be located there");
		}
		RunWorldStateEntity existing = entityManager.find(RunWorldStateEntity.class, runId);
		if (existing == null) {
			entityManager.persist(new RunWorldStateEntity(runId, location.sceneId(), location.zoneId()));
		} else {
			existing.moveTo(location.sceneId(), location.zoneId());
		}
	}

	private SceneInstance toDomain(SceneInstanceEntity e) {
		SceneState state = codec.decode(e.getId(), e.getStateSchemaVersion(), e.getState());
		try {
			ScenePlacement placement = switch (e.getKind()) {
				case HUB -> new ScenePlacement.Hub();
				case REGION -> new ScenePlacement.Region(e.getRegionId(), e.getSceneSeed());
			};
			return new SceneInstance(e.getId(), e.getRunId(), e.getDefinitionCode(), placement, e.isDiscovered(),
					e.getRevision(), state);
		} catch (IllegalArgumentException | NullPointerException ex) {
			throw new PersistedStateException("Scene " + e.getId() + ": stored scene metadata is invalid: " + ex.getMessage(), ex);
		}
	}
}
