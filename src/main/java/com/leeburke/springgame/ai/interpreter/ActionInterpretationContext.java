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
		int schemaVersion,
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

	public static final int CURRENT_SCHEMA_VERSION = 1;

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

	public record Zone(String alias, String name) {
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

	/** A visible object or hazard: alias, display name and zone alias. */
	public record Thing(String alias, String name, String zone) {
	}

	public record Exit(String alias, String zone) {
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
