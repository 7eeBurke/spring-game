package com.leeburke.springgame.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.ai.narration.NarrationSource;

/**
 * An enemy attack awaiting the player's defense, persisted with everything Stage 11 needs to resolve
 * it and the Java cue the player was shown, so a reload never rerolls or re-telegraphs it.
 *
 * @param narration the attack's narration once finalised; the cue is always available before that
 */
public record PendingAttack(IncomingAttack attack, UUID sceneId, String optionCode, String weaponCode, int createdTurn,
		String cueText, Optional<StoredNarration> narration) {

	public PendingAttack {
		Objects.requireNonNull(attack, "attack");
		Objects.requireNonNull(sceneId, "sceneId");
		Refs.require(optionCode, "optionCode");
		Refs.require(weaponCode, "weaponCode");
		if (createdTurn < 1) {
			throw new IllegalArgumentException("An attack is created on turn 1 or later");
		}
		Objects.requireNonNull(cueText, "cueText");
		if (cueText.isBlank()) {
			throw new IllegalArgumentException("The attack cue is mandatory");
		}
		Objects.requireNonNull(narration, "narration");
	}

	/** Presentation text with its source. */
	public record StoredNarration(String text, NarrationSource source) {
		public StoredNarration {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(source, "source");
		}
	}
}
