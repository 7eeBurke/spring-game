package com.leeburke.springgame.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.game.RunStatus;

/**
 * Persists run sessions. The row lock taken by {@link #lock} serialises every mechanical change of
 * one run: all of a turn's writes happen while holding it, and {@code state_version} advances with
 * each committed turn.
 */
@Service
public class RunSessionStore {

	private final EntityManager entityManager;

	public RunSessionStore(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
	}

	/** Inserts a new INITIALIZING session; joins the caller's transaction (run creation). */
	@Transactional
	public void create(UUID runId, String accessTokenHash, UUID creationKey, Instant now) {
		entityManager.persist(new RunSessionEntity(runId, accessTokenHash, creationKey, now));
		entityManager.flush();
	}

	/** The session created with this creation key: run ID and token hash. */
	@Transactional(readOnly = true)
	public Optional<Credentials> findByCreationKey(UUID creationKey) {
		return first(entityManager.createQuery("SELECT s FROM RunSessionEntity s WHERE s.creationKey = :key", RunSessionEntity.class)
				.setParameter("key", creationKey).getResultList())
				.map(s -> new Credentials(s.getRunId(), s.getAccessTokenHash(), s.getStatus()));
	}

	/** The run a token hash belongs to. */
	@Transactional(readOnly = true)
	public Optional<UUID> findRunByTokenHash(String accessTokenHash) {
		return first(entityManager.createQuery("SELECT s.runId FROM RunSessionEntity s WHERE s.accessTokenHash = :hash", UUID.class)
				.setParameter("hash", accessTokenHash).getResultList());
	}

	@Transactional(readOnly = true)
	public Optional<RunSession> find(UUID runId) {
		return Optional.ofNullable(entityManager.find(RunSessionEntity.class, runId)).map(RunSessionStore::toDomain);
	}

	/** Locks the session row (SELECT ... FOR UPDATE) for the rest of the caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public RunSession lock(UUID runId) {
		RunSessionEntity entity = entityManager.find(RunSessionEntity.class, runId, LockModeType.PESSIMISTIC_WRITE);
		if (entity == null) {
			throw new PersistedStateException("Run " + runId + " has no session");
		}
		return toDomain(entity);
	}

	/** INITIALIZING to ACTIVE; false when the session is not INITIALIZING. */
	@Transactional
	public boolean activate(UUID runId) {
		return entityManager.createQuery("UPDATE RunSessionEntity s SET s.status = :active WHERE s.runId = :id AND s.status = :init")
				.setParameter("active", RunStatus.ACTIVE).setParameter("init", RunStatus.INITIALIZING)
				.setParameter("id", runId).executeUpdate() == 1;
	}

	/** Records a committed turn on the locked session: new status, version and turn number, and the cursor. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void advance(UUID runId, RunStatus status, Optional<RunSession.EnemyCursor> cursor) {
		RunSessionEntity entity = entityManager.find(RunSessionEntity.class, runId);
		entity.advance(status, cursor.map(RunSession.EnemyCursor::sceneId).orElse(null),
				cursor.map(RunSession.EnemyCursor::entityId).orElse(null));
		entityManager.flush();
	}

	private static RunSession toDomain(RunSessionEntity e) {
		Optional<RunSession.EnemyCursor> cursor = e.getEnemyCursorSceneId() == null
				? Optional.empty()
				: Optional.of(new RunSession.EnemyCursor(e.getEnemyCursorSceneId(), e.getEnemyCursorEntityId()));
		return new RunSession(e.getRunId(), e.getStatus(), e.getStateVersion(), e.getTurnNumber(), cursor);
	}

	private static <T> Optional<T> first(List<T> rows) {
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
	}

	/** What run creation needs to resume or reject a retry. */
	public record Credentials(UUID runId, String accessTokenHash, RunStatus status) {
	}
}
