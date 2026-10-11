package com.leeburke.springgame.ai.interpreter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

/**
 * Everything the Action Interpreter may see, and nothing else. Built from the player-safe scene
 * view, the player's own body and tool belt, catalogue display names and the visible cue of each
 * incoming attack. Every reference is a request-scoped alias; there are no backend IDs, UUIDs,
 * stats, HP, enemy mechanics, difficulties or hidden content.
 */
public record ActionInterpretationContext(
		int contextVersion,
		String currentZone,
		List<Zone> zones,
		List<Connection> connections,
		List<Creature> entities,
		List<Thing> objects,
		List<Thing> hazards,
		List<Exit> exits,
		List<String> discoveredFacts,
		List<BodyPartState> playerBody,
		List<Owned> weapons,
		List<Owned> items,
		List<Owned> abilities,
		List<IncomingAttackSummary> incomingAttacks) {

	public static final int CURRENT_SCHEMA_VERSION = 2;

	public ActionInterpretationContext {
		Objects.requireNonNull(currentZone, "currentZone");
		zones = List.copyOf(zones);
		connections = List.copyOf(connections);
		entities = List.copyOf(entities);
		objects = List.copyOf(objects);
		hazards = List.copyOf(hazards);
		exits = List.copyOf(exits);
		discoveredFacts = List.copyOf(discoveredFacts);
		playerBody = List.copyOf(playerBody);
		weapons = List.copyOf(weapons);
		items = List.copyOf(items);
		abilities = List.copyOf(abilities);
		incomingAttacks = List.copyOf(incomingAttacks);
	}

	/**
	 * A known zone: its label, how the world refers to it ("the narrow alcove") and what it physically
	 * is ("the belfry floor, split by a crack wide enough to show the drop below"), so the player's words
	 * about the place, or about a feature of it, can be matched to it. {@code phrase} and
	 * {@code description} are null when no text is authored.
	 */
	public record Zone(String alias, String name, String phrase, String description) {

		public Zone(String alias, String name, String phrase) {
			this(alias, name, phrase, null);
		}
	}

	public record Connection(String zoneA, String zoneB) {
	}

	/** Whether a visible creature can still fight. A FALLEN creature is at 0 HP: still present, never acting. */
	public enum Condition {
		ACTIVE,
		FALLEN
	}

	/** A visible creature: alias, display name, zone alias and condition. */
	public record Creature(String alias, String name, String zone, Condition condition) {
	}

	/**
	 * A known object or hazard: alias, display name and zone alias; for a container, {@code container}
	 * says whether it is open and what it shows ("closed", "open, holding a Bandage", "open and empty";
	 * null for anything that is not a container), and {@code reach} how far it is ("here", "one step
	 * away", "two steps away", "farther", or "no known way").
	 */
	public record Thing(String alias, String name, String zone, String container, String reach) {
	}

	/**
	 * A known exit: the zone it leaves from, where it leads as far as the player knows (the region
	 * for the hub's road, a discovered scene, or "an unexplored way"), and how it looks
	 * ({@code passage}, "a ladder down through the split floor"), so a way described by its look can
	 * be matched to it. {@code passage} is null when no text is authored.
	 */
	public record Exit(String alias, String zone, String leadsTo, String passage) {

		public Exit(String alias, String zone, String leadsTo) {
			this(alias, zone, leadsTo, null);
		}
	}

	public record BodyPartState(BodyPart part, BodySeverity severity) {
	}

	/** A weapon, item or ability the player owns: alias and display name. */
	public record Owned(String alias, String name) {
	}

	/** An incoming attack the player can respond to, as the player perceives it. */
	public record IncomingAttackSummary(String alias, Optional<String> attacker, String cue) {
		public IncomingAttackSummary {
			Objects.requireNonNull(attacker, "attacker");
		}
	}
}
