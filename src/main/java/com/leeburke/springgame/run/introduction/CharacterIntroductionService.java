package com.leeburke.springgame.run.introduction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.leeburke.springgame.ai.narration.CharacterIntroduction;
import com.leeburke.springgame.ai.narration.CharacterIntroductionContext;
import com.leeburke.springgame.ai.narration.CharacterIntroductionNarrator;
import com.leeburke.springgame.ai.narration.LoreCatalog;
import com.leeburke.springgame.ai.narration.Narration;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.IntroductionStore;
import com.leeburke.springgame.run.GameRun;

/**
 * Gives each run one stable character introduction. Deliberately not transactional: the stored
 * introduction is read, the character loaded, and only then is the narrator called, with no
 * database transaction open while waiting on the model. The result is inserted if absent; if a
 * concurrent request stored one first, that one is returned. Once stored, the provider is never
 * called again for the run.
 */
@Service
public class CharacterIntroductionService {

	private final GameRunStore runs;
	private final IntroductionStore introductions;
	private final CharacterIntroductionNarrator narrator;
	private final LoreCatalog lore;

	public CharacterIntroductionService(GameRunStore runs, IntroductionStore introductions,
			CharacterIntroductionNarrator narrator, LoreCatalog lore) {
		this.runs = Objects.requireNonNull(runs, "runs");
		this.introductions = Objects.requireNonNull(introductions, "introductions");
		this.narrator = Objects.requireNonNull(narrator, "narrator");
		this.lore = Objects.requireNonNull(lore, "lore");
	}

	/** @throws IllegalArgumentException if the run does not exist */
	public CharacterIntroduction introductionFor(UUID runId) {
		Objects.requireNonNull(runId, "runId");
		Optional<CharacterIntroduction> stored = introductions.find(runId);
		if (stored.isPresent()) {
			return stored.get();
		}
		GameRun run = runs.findRun(runId).orElseThrow(() -> new IllegalArgumentException("Unknown run " + runId));
		Narration narration = narrator.narrate(CharacterIntroductionContext.from(run.playerCharacter(), lore));
		return introductions.insertIfAbsent(runId,
				new CharacterIntroduction(narration.text(), narration.source(), narration.promptVersion()));
	}
}
