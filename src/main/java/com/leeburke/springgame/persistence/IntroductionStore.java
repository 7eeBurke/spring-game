package com.leeburke.springgame.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leeburke.springgame.ai.narration.CharacterIntroduction;

/**
 * Stores each run's character introduction exactly once. Each method is its own short
 * transaction; callers generate the text outside any transaction and then call
 * {@link #insertIfAbsent}, which keeps whichever introduction was committed first.
 */
@Service
public class IntroductionStore {

	private final EntityManager entityManager;

	public IntroductionStore(EntityManager entityManager) {
		this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
	}

	@Transactional(readOnly = true)
	public Optional<CharacterIntroduction> find(UUID runId) {
		return load(runId);
	}

	/**
	 * Inserts the introduction unless the run already has one, then returns the stored one: this
	 * introduction, or the one a concurrent caller committed first.
	 */
	@Transactional
	public CharacterIntroduction insertIfAbsent(UUID runId, CharacterIntroduction introduction) {
		Objects.requireNonNull(runId, "runId");
		Objects.requireNonNull(introduction, "introduction");
		entityManager.createNativeQuery("""
				INSERT INTO character_introduction (run_id, intro_text, source, prompt_version)
				VALUES (?1, ?2, ?3, ?4)
				ON CONFLICT (run_id) DO NOTHING
				""")
				.setParameter(1, runId)
				.setParameter(2, introduction.text())
				.setParameter(3, introduction.source().name())
				.setParameter(4, introduction.promptVersion())
				.executeUpdate();
		return load(runId).orElseThrow(() -> new PersistedStateException("Run " + runId + ": introduction was not stored"));
	}

	private Optional<CharacterIntroduction> load(UUID runId) {
		List<CharacterIntroductionEntity> rows = entityManager
				.createQuery("SELECT i FROM CharacterIntroductionEntity i WHERE i.runId = :runId", CharacterIntroductionEntity.class)
				.setParameter("runId", runId)
				.getResultList();
		if (rows.isEmpty()) {
			return Optional.empty();
		}
		CharacterIntroductionEntity row = rows.getFirst();
		try {
			return Optional.of(new CharacterIntroduction(row.getIntroText(), row.getSource(), row.getPromptVersion()));
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new PersistedStateException("Run " + runId + ": invalid stored introduction: " + e.getMessage(), e);
		}
	}
}
