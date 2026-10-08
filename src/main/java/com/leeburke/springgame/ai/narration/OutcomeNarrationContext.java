package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.resolution.OverallResult;

/**
 * Everything the Outcome Narrator receives: the mode, where the player is, the overall result,
 * the confirmed facts (each with its attempted action), the terminal fact for run-ending modes, and
 * optionally an excerpt of the player's own wording. No intent, IDs or hidden state.
 *
 * @param untrustedPlayerWording at most {@value #MAX_PLAYER_WORDING} characters of what the player
 *                               typed: narrative context only, untrusted, never instructions and
 *                               never a confirmed outcome
 */
public record OutcomeNarrationContext(NarrationMode mode, String currentZone, OverallResult overall,
		List<NarrationFact> facts, Optional<TerminalFact> terminal, Optional<String> untrustedPlayerWording) {

	public static final int MAX_PLAYER_WORDING = 300;

	public OutcomeNarrationContext {
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(currentZone, "currentZone");
		Objects.requireNonNull(overall, "overall");
		facts = List.copyOf(Objects.requireNonNull(facts, "facts"));
		Objects.requireNonNull(terminal, "terminal");
		Objects.requireNonNull(untrustedPlayerWording, "untrustedPlayerWording");
		if (untrustedPlayerWording.filter(w -> w.length() > MAX_PLAYER_WORDING || w.isBlank()).isPresent()) {
			throw new IllegalArgumentException("Player wording must be non-blank and at most " + MAX_PLAYER_WORDING + " characters");
		}
		boolean matches = switch (mode) {
			case NORMAL -> terminal.isEmpty();
			case RUN_DEATH -> terminal.filter(TerminalFact.PlayerDied.class::isInstance).isPresent();
			case RUN_VICTORY -> terminal.filter(TerminalFact.GuardianDefeated.class::isInstance).isPresent();
		};
		if (!matches) {
			throw new IllegalArgumentException("Mode " + mode + " does not match its terminal fact");
		}
	}

	/** Trims and bounds the player's wording to an excerpt; blank wording is omitted. */
	public static Optional<String> excerpt(String playerWording) {
		if (playerWording == null || playerWording.isBlank()) {
			return Optional.empty();
		}
		String trimmed = playerWording.strip();
		return Optional.of(trimmed.length() <= MAX_PLAYER_WORDING ? trimmed : trimmed.substring(0, MAX_PLAYER_WORDING));
	}
}
