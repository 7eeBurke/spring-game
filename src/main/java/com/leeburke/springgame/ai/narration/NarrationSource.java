package com.leeburke.springgame.ai.narration;

/**
 * Who wrote a narration: the model; the deterministic fallback (the model failed or was unavailable);
 * or the game directly, by design, when there is nothing for a storyteller to tell (a look that finds
 * nothing changed). Only outcome narration is ever {@code DIRECT}.
 */
public enum NarrationSource {
	AI,
	FALLBACK,
	DIRECT
}
