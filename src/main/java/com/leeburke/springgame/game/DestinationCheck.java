package com.leeburke.springgame.game;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * How the player's own words name visible places, for {@link RouteGrounding}: zones by name, and
 * known exits by what they lead to as far as the player knows. General-purpose: places are matched
 * by the significant words of their visible names, with no special cases.
 */
public final class DestinationCheck {

	/** Words that never identify a place on their own. */
	private static final Set<String> COMMON = Set.of(
			"the", "and", "into", "onto", "from", "with", "toward", "towards", "back", "through", "way", "ways", "then",
			"that", "this", "there", "here", "where", "enter", "follow", "go", "walk", "head", "move", "run", "take",
			"leave", "unexplored", "until", "something", "find", "continue", "onward", "onwards", "forward", "again");

	private DestinationCheck() {
	}

	/** A place the player can name: a zone of this scene or a known exit, with its visible name. */
	public record Place(String id, boolean exit, String name) {
	}

	/** Every visible zone and every labelled known exit. */
	static List<Place> places(PlayerSceneView view, Map<String, String> exitLabels) {
		Map<String, Place> places = new LinkedHashMap<>();
		for (PlayerSceneView.VisibleZone zone : view.zones()) {
			places.put(zone.id(), new Place(zone.id(), false, zone.displayName()));
		}
		for (PlayerSceneView.KnownExit exit : view.exits()) {
			String label = exitLabels.get(exit.id());
			if (label != null) {
				places.put(exit.id(), new Place(exit.id(), true, label));
			}
		}
		return List.copyOf(places.values());
	}

	/** Every word of a text, in order: lower case, accents folded, apostrophes and punctuation dropped. */
	static List<String> tokens(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
		return java.util.Arrays.stream(folded.split("[^\\p{L}]+")).filter(word -> !word.isEmpty()).toList();
	}

	/** The significant words of a text: lower case, accents folded, four letters or more, not common. */
	static Set<String> words(String text) {
		String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
		return java.util.Arrays.stream(folded.split("[^\\p{L}]+"))
				.filter(word -> word.length() >= 4 && !COMMON.contains(word))
				.collect(Collectors.toSet());
	}
}
