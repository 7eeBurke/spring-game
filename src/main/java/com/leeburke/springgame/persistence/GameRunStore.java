package com.leeburke.springgame.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.character.GeneratedCharacter;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.run.GameRun;

/**
 * Persistence facade for runs. Callers work with domain types ({@link GameRun}); JPA entities never
 * leave this package.
 * <p>
 * Uses the {@link EntityManager} directly rather than Spring Data repositories: IDs are assigned
 * here, and {@code persist}/{@code find} are the only operations needed. A repository's
 * {@code save()} would {@code merge} an assigned-ID entity instead of inserting it.
 */
@Service
public class GameRunStore {

	private final EntityManager entityManager;
	private final PlayerCharacterMapper mapper;

	public GameRunStore(EntityManager entityManager, GameContentCatalog catalog) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
		this.mapper = new PlayerCharacterMapper(catalog);
	}

	/**
	 * Persists a new run and its player character atomically: both rows commit or neither does.
	 *
	 * @throws IllegalArgumentException if the character references content that is not the current
	 *                                  catalogue definition for its code; nothing is written
	 */
	@Transactional
	public GameRun createRun(long runSeed, GeneratedCharacter character) {
		Objects.requireNonNull(character, "character");
		UUID runId = UUID.randomUUID(); // identity only, not game randomness
		PlayerCharacterState state = PlayerCharacterState.from(character);
		PlayerCharacterEntity characterEntity = mapper.toEntity(runId, state);

		// No JPA association links these entities, so flush the run first to satisfy the
		// player_character foreign key regardless of Hibernate's insert ordering. Still one transaction.
		entityManager.persist(new GameRunEntity(runId, runSeed));
		entityManager.flush();
		entityManager.persist(characterEntity);
		return new GameRun(runId, runSeed, state);
	}

	/**
	 * Loads a run. Empty only when no run with this ID exists.
	 *
	 * @throws PersistedStateException if the run exists but its stored state is missing or invalid
	 */
	@Transactional(readOnly = true)
	public Optional<GameRun> findRun(UUID runId) {
		Objects.requireNonNull(runId, "runId");
		GameRunEntity run = entityManager.find(GameRunEntity.class, runId);
		if (run == null) {
			return Optional.empty();
		}
		PlayerCharacterEntity character = entityManager.find(PlayerCharacterEntity.class, runId);
		if (character == null) {
			throw new PersistedStateException("Run " + runId + " has no player character");
		}
		return Optional.of(new GameRun(run.getId(), run.getRunSeed(), mapper.toDomain(character)));
	}
}
