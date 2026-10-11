package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the player can perceive from where they stand, in the world's own words: the place itself
 * (when not already told), the places beside it and the passages to them, and what is in sight there.
 * Built by Java from the player-known view and authored texts, so it holds nothing hidden and nothing
 * farther than the next place. A closed container's contents are never listed.
 *
 * @param here     the place the player stands in; absent when an arrival in the same turn already told it
 * @param beside   places joined to here by a passage the player can see
 * @param things   objects in sight: here, or in a place beside
 * @param hazards  hazards in sight
 * @param creatures creatures in sight (a fallen one is fallen)
 * @param ways     ways out of the scene in sight, by where they lead as far as the player knows
 */
public record Perception(Optional<PlaceRef> here, List<Beside> beside, List<SeenThing> things, List<SeenThing> hazards,
		List<SeenCreature> creatures, List<WayOut> ways) {

	public Perception {
		Objects.requireNonNull(here, "here");
		beside = List.copyOf(beside);
		things = List.copyOf(things);
		hazards = List.copyOf(hazards);
		creatures = List.copyOf(creatures);
		ways = List.copyOf(ways);
	}

	/** The same perception without the place itself (it has just been told). */
	public Perception withoutHere() {
		return new Perception(Optional.empty(), beside, things, hazards, creatures, ways);
	}

	/**
	 * A place: its label is for headings and the map, never for prose; its phrase is how the world
	 * refers to it ("the narrow alcove"); its description what it physically is.
	 */
	public record PlaceRef(String label, String phrase, String description) {
		public PlaceRef {
			Objects.requireNonNull(label, "label");
			Objects.requireNonNull(phrase, "phrase");
			Objects.requireNonNull(description, "description");
		}
	}

	/** A place beside this one, and the passage to it. */
	public record Beside(PlaceRef place, String passage) {
		public Beside {
			Objects.requireNonNull(place, "place");
			Objects.requireNonNull(passage, "passage");
		}
	}

	/**
	 * Something in sight.
	 *
	 * @param where "here", or the phrase of the place beside where it is
	 * @param state for a container: "closed", "open, holding a Bandage" or "open and empty"
	 */
	public record SeenThing(String name, String description, String where, boolean here, Optional<String> state) {
		public SeenThing {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(description, "description");
			Objects.requireNonNull(where, "where");
			Objects.requireNonNull(state, "state");
		}
	}

	public record SeenCreature(String name, String where, boolean here, boolean fallen) {
		public SeenCreature {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(where, "where");
		}
	}

	/**
	 * A way out of the scene.
	 *
	 * @param leadsTo where it goes as far as the player knows ("an unexplored way", "the way to The Last Lantern")
	 * @param passage what it physically is ("a narrow doorway in the sacristy wall")
	 * @param where   "here", or the phrase of the place beside it leaves from
	 */
	public record WayOut(String leadsTo, String passage, String where, boolean here) {
		public WayOut {
			Objects.requireNonNull(leadsTo, "leadsTo");
			Objects.requireNonNull(passage, "passage");
			Objects.requireNonNull(where, "where");
		}
	}
}
