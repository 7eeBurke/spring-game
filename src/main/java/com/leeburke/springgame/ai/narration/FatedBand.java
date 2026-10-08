package com.leeburke.springgame.ai.narration;

/**
 * Narration-only labels for the visible Fated value (see docs/CHARACTER_GENERATION.md). They have
 * no mechanical effect and reveal no probabilities.
 */
public enum FatedBand {
	ORDINARY("Fate has paid you little attention."),
	TOUCHED("Fate has brushed against you."),
	UNUSUAL("Something unusual follows in your wake."),
	OMINOUS("An ominous weight of fate hangs over you."),
	DEEPLY_FATED("You are deeply bound to fate.");

	private final String phrase;

	FatedBand(String phrase) {
		this.phrase = phrase;
	}

	public static FatedBand of(int fated) {
		return switch (fated) {
			case 0 -> ORDINARY;
			case 1, 2 -> TOUCHED;
			case 3 -> UNUSUAL;
			case 4 -> OMINOUS;
			case 5 -> DEEPLY_FATED;
			default -> throw new IllegalArgumentException("Fated must be 0 to 5, but was " + fated);
		};
	}

	/** Fallback narration phrase. */
	public String phrase() {
		return phrase;
	}
}
