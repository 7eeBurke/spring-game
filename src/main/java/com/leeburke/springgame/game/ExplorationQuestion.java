package com.leeburke.springgame.game;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Whether the player's words ask where they can go: "I look for a way I haven't gone", "where can I
 * go from here?", "I search for another passage", "is there a way deeper into the chapel?". Such a
 * look is answered from what the player knows of the scene's ways and places (unexplored ways,
 * places not yet visited, where they have been), not only from what is in sight. Deterministic Java
 * over the player's own words, like {@link DestinationCheck}; it never moves anyone and never
 * decides what exists.
 */
public final class ExplorationQuestion {

	/** Phrases (lower case, accents and apostrophes removed) that ask about ways and places to go. */
	private static final List<String> PHRASES = List.of(
			"where can i go", "where could i go", "where to go", "where i can go", "where else", "where next", "go next",
			"havent gone", "havent been", "havent tried", "havent explored", "not been", "not gone", "not yet",
			"new way", "another way", "other way", "a way i", "way deeper", "deeper", "further in", "farther in",
			"unexplored", "explore", "way on", "way forward", "way onward", "onward", "way out", "ways out",
			"exit", "passage", "route", "path", "somewhere new", "somewhere else", "lead");

	private ExplorationQuestion() {
	}

	/** True for an exploration question; a {@code /search} command always is one. */
	public static boolean asks(String input) {
		if (input == null || input.isBlank()) {
			return false;
		}
		String folded = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.replace("'", "").replace("’", "").replaceAll("\\s+", " ").trim();
		if (folded.startsWith("/search")) {
			return true;
		}
		if (folded.startsWith("/")) {
			return false;
		}
		String padded = " " + folded.replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ") + " ";
		return PHRASES.stream().anyMatch(phrase -> padded.contains(" " + phrase));
	}
}
