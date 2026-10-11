package com.leeburke.springgame.game.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.ai.narration.Perception;
import com.leeburke.springgame.ai.narration.PlaceDescriber;
import com.leeburke.springgame.game.DestinationCheck;

/**
 * Player-facing guidance for refused actions, in the world's words rather than map labels: where the
 * player stands, what lies beside it, what is within reach, and the ways on. Built by Java from the
 * player-known view only, so a hint never mentions anything the player could not know.
 */
final class HintWriter {

	private final PlaceDescriber places;
	private final String zoneId;
	private final java.util.Optional<com.leeburke.springgame.world.view.ExplorationLeads> leads;

	HintWriter(PlaceDescriber places, String zoneId) {
		this(places, zoneId, java.util.Optional.empty());
	}

	/** @param leads what the player knows is left to explore here, for a short "not yet explored" line */
	HintWriter(PlaceDescriber places, String zoneId, java.util.Optional<com.leeburke.springgame.world.view.ExplorationLeads> leads) {
		this.places = Objects.requireNonNull(places, "places");
		this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
		this.leads = Objects.requireNonNull(leads, "leads");
	}

	/** "You're at the threshold of the sacristy. From here you can reach the vestment racks. Within reach: a crate (closed). ..." */
	String where() {
		Perception seen = places.perceive(zoneId);
		List<String> sentences = new ArrayList<>();
		seen.here().ifPresent(here -> sentences.add("You're at " + here.phrase() + "."));
		if (!seen.beside().isEmpty()) {
			sentences.add("From here you can reach " + join(seen.beside().stream().map(b -> b.place().phrase()).toList()) + ".");
		}
		List<String> inReach = seen.things().stream().filter(Perception.SeenThing::here)
				.map(t -> lower(t.name()) + t.state().map(state -> " (" + state + ")").orElse("")).toList();
		if (!inReach.isEmpty()) {
			sentences.add("Within reach: " + join(inReach.stream().map(PlaceDescriberArticles::article).toList()) + ".");
		}
		List<String> nearby = seen.things().stream().filter(t -> !t.here())
				.map(t -> PlaceDescriberArticles.article(lower(t.name())) + " in " + t.where()).toList();
		if (!nearby.isEmpty()) {
			sentences.add("Nearby: " + join(nearby) + ".");
		}
		List<String> ways = seen.ways().stream()
				.map(way -> way.passage() + (way.here() ? ", right here" : ", reached from " + way.where())
						+ (way.leadsTo().isBlank() ? "" : " (" + way.leadsTo() + ")"))
				.toList();
		if (!ways.isEmpty()) {
			sentences.add("Ways on: " + join(ways) + ".");
		}
		// Beyond what is in sight: the unexplored ways the player already knows of, and how to reach them.
		leads.ifPresent(known -> {
			List<String> farther = places.ways(known.unexplored()).stream().filter(lead -> lead.steps() >= 2)
					.map(lead -> lead.what() + ", from " + lead.where() + lead.via().map(v -> " by way of " + v).orElse("")).toList();
			if (!farther.isEmpty()) {
				sentences.add("Not yet explored, farther off: " + join(farther) + ".");
			}
		});
		return String.join(" ", sentences);
	}

	/** The way on at the player's feet, as a question: "Before you: the Hollow Chapel's sagging west doors, ... Do you want to go through?" */
	String threshold(List<DestinationCheck.Place> ways) {
		String passage = ways.stream().findFirst()
				.map(way -> places.exitZone(way.id()).isPresent() ? places.wayPassage(way.id()) : way.name())
				.orElse("the way on");
		return "Before you: " + passage + ". Do you want to go through?";
	}

	/**
	 * Why something cannot be reached from here, and what to do: "The crate is in the vestment racks,
	 * out of reach from here. Go there first." Only what the player knows: an object not in the known
	 * view gets no place and no name.
	 */
	String outOfReach(String objectId) {
		return places.objectPlace(objectId)
				.map(where -> "The " + lower(places.objectName(objectId)) + " is in " + where + ", out of reach from here. Go there first.")
				.orElse("That is out of reach from here.");
	}

	/** Places and ways as the player knows them: zones by phrase, ways out by where they lead. */
	String choices(List<DestinationCheck.Place> options) {
		return join(options.stream().map(p -> p.exit() ? p.name() : places.place(p.id()).phrase()).toList());
	}

	static String join(List<String> parts) {
		if (parts.size() <= 1) {
			return String.join("", parts);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.getLast();
	}

	private static String capitalised(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String lower(String name) {
		return name.toLowerCase(java.util.Locale.ROOT);
	}

	/** Indefinite articles, as narration uses them. */
	private static final class PlaceDescriberArticles {
		static String article(String name) {
			String lower = name.toLowerCase(java.util.Locale.ROOT);
			return (lower.startsWith("a") || lower.startsWith("e") || lower.startsWith("i") || lower.startsWith("o")
					|| lower.startsWith("u") ? "an " : "a ") + name;
		}
	}
}
