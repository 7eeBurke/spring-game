package com.leeburke.springgame.ai.narration;

import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.resolution.CancellationReason;
import com.leeburke.springgame.action.resolution.UnavailableReason;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.ImpactSeverity;

/**
 * One confirmed fact for the Outcome Narrator, derived deterministically from a step outcome and
 * named with player-visible names only. Every fact carries the step's {@link AttemptedAction}: what
 * was tried, which is never itself a confirmed result. Non-events are explicit (no contact,
 * contact without damage, staying put, a cancelled or ineffective step) so narration cannot imply
 * they happened.
 *
 * @see OutcomeNarrationContextBuilder
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "fact")
@JsonSubTypes({
		@JsonSubTypes.Type(NarrationFact.PlayerAttacked.class),
		@JsonSubTypes.Type(NarrationFact.PlayerDefended.class),
		@JsonSubTypes.Type(NarrationFact.PlayerMoved.class),
		@JsonSubTypes.Type(NarrationFact.PlayerStayed.class),
		@JsonSubTypes.Type(NarrationFact.PlayerLeftScene.class),
		@JsonSubTypes.Type(NarrationFact.PlayerArrived.class),
		@JsonSubTypes.Type(NarrationFact.ExitNotReached.class),
		@JsonSubTypes.Type(NarrationFact.PlayerSpoke.class),
		@JsonSubTypes.Type(NarrationFact.StepCancelled.class),
		@JsonSubTypes.Type(NarrationFact.StepHadNoEffect.class),
		@JsonSubTypes.Type(NarrationFact.PlayerObserved.class),
		@JsonSubTypes.Type(NarrationFact.WalkedTo.class),
		@JsonSubTypes.Type(NarrationFact.StayedPut.class),
		@JsonSubTypes.Type(NarrationFact.CrossedInto.class),
		@JsonSubTypes.Type(NarrationFact.Perceived.class),
		@JsonSubTypes.Type(NarrationFact.Inspected.class),
		@JsonSubTypes.Type(NarrationFact.ContainerOpened.class),
		@JsonSubTypes.Type(NarrationFact.ItemTaken.class),
		@JsonSubTypes.Type(NarrationFact.InteractionFailed.class),
		@JsonSubTypes.Type(NarrationFact.SoughtWays.class),
		@JsonSubTypes.Type(NarrationFact.TookItem.class),
		@JsonSubTypes.Type(NarrationFact.OpenedContainer.class),
		@JsonSubTypes.Type(NarrationFact.InspectedPlace.class) })
public sealed interface NarrationFact {

	/** 1-based step the fact belongs to. */
	int step();

	/** What the step tried to do. */
	AttemptedAction attempt();

	// --- The world in its own words (places carry a phrase for prose and a label for headings only) ---

	/** The player walked from one place to the one beside it, by this passage. */
	record WalkedTo(int step, AttemptedAction attempt, Perception.PlaceRef from, Perception.PlaceRef to, String passage)
			implements NarrationFact {
		public WalkedTo {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(from, "from");
			Objects.requireNonNull(to, "to");
			Objects.requireNonNull(passage, "passage");
		}
	}

	/**
	 * The player did not move: they held their ground or were already there, or (with
	 * {@code couldNotReach}) the place they tried for has no way to it that they know.
	 */
	record StayedPut(int step, AttemptedAction attempt, Perception.PlaceRef here, Optional<Perception.PlaceRef> couldNotReach)
			implements NarrationFact {
		public StayedPut {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(here, "here");
			Objects.requireNonNull(couldNotReach, "couldNotReach");
		}
	}

	/**
	 * The player went through a way out into another place.
	 *
	 * @param through      the passage they went through, as it looked from where they were
	 * @param scene        the new place's label (for headings only)
	 * @param sceneDescription what the new place is, in the world's words
	 * @param arrival      the spot they arrive at
	 * @param behind       the passage at their back, the way they came in
	 * @param behindLeadsTo where it leads back to, as far as they know
	 */
	record CrossedInto(int step, AttemptedAction attempt, String through, String scene, String sceneDescription,
			Perception.PlaceRef arrival, Optional<String> behind, Optional<String> behindLeadsTo) implements NarrationFact {
		public CrossedInto {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(through, "through");
			Objects.requireNonNull(scene, "scene");
			Objects.requireNonNull(sceneDescription, "sceneDescription");
			Objects.requireNonNull(arrival, "arrival");
			Objects.requireNonNull(behind, "behind");
			Objects.requireNonNull(behindLeadsTo, "behindLeadsTo");
		}
	}

	/**
	 * What the player perceives. After a crossing in the same turn, the place itself is left out (the
	 * crossing told it). {@code unchanged}: the same as the player's last look, so it can be told briefly.
	 */
	record Perceived(int step, AttemptedAction attempt, Perception perception, boolean unchanged) implements NarrationFact {
		public Perceived {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(perception, "perception");
		}
	}

	/**
	 * A look for somewhere to go ("I look for a way I haven't gone"): what is in sight, and what the
	 * player knows of the scene's ways and places beyond it. Only known things: an empty
	 * {@code unexplored} and {@code unvisited} ({@code nothingKnownLeft}) means no lead is known, never
	 * that no other way exists. Nothing here moves the player.
	 *
	 * @param visited     the places already stood in (phrases), other than here
	 * @param visitsKnown false for scenes stored before visits were recorded (then {@code unvisited} is
	 *                    empty and nothing is claimed about where the player has been)
	 */
	record SoughtWays(int step, AttemptedAction attempt, Perception perception, java.util.List<Lead> unexplored,
			java.util.List<Lead> unvisited, java.util.List<String> visited, java.util.List<Lead> known, boolean visitsKnown,
			boolean nothingKnownLeft) implements NarrationFact {
		public SoughtWays {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(perception, "perception");
			unexplored = java.util.List.copyOf(unexplored);
			unvisited = java.util.List.copyOf(unvisited);
			visited = java.util.List.copyOf(visited);
			known = java.util.List.copyOf(known);
		}
	}

	/**
	 * A way or place the player knows of.
	 *
	 * @param what    the way's passage, or the place's phrase
	 * @param where   "here", or the phrase of the place it leaves from (for a place: its own phrase)
	 * @param steps   passages from where the player stands
	 * @param via     the first place to go through, when it is farther than the next place
	 * @param leadsTo for a way out: where it leads, as far as the player knows
	 */
	record Lead(String what, String where, int steps, Optional<String> via, Optional<String> leadsTo) {
		public Lead {
			Objects.requireNonNull(what, "what");
			Objects.requireNonNull(where, "where");
			Objects.requireNonNull(via, "via");
			Objects.requireNonNull(leadsTo, "leadsTo");
		}
	}

	/**
	 * A close look at a place: what it physically is, and the known ways that leave from it. When it
	 * is out of sight ({@code inSight} false), nothing about it is told. Nothing here moves the player.
	 *
	 * @param where "here", or the phrase of the place (it is in sight, beside the player)
	 */
	record InspectedPlace(int step, AttemptedAction attempt, Perception.PlaceRef place, String where, java.util.List<Lead> ways,
			boolean inSight) implements NarrationFact {
		public InspectedPlace {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(place, "place");
			Objects.requireNonNull(where, "where");
			ways = java.util.List.copyOf(ways);
		}
	}

	/**
	 * A close look at one thing, place or way out (places are now told as {@link InspectedPlace}).
	 *
	 * @param inSight false when it is too far to make out from here (then nothing else is told)
	 * @param state   a container's state ("closed", "open, holding a Bandage", "open and empty")
	 * @param where   "here", or the phrase of the place it is in
	 */
	record Inspected(int step, AttemptedAction attempt, String name, String description, Optional<String> state, String where,
			boolean inSight) implements NarrationFact {
		public Inspected {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(description, "description");
			Objects.requireNonNull(state, "state");
			Objects.requireNonNull(where, "where");
		}
	}

	/**
	 * A container was opened (or found already open); its contents are now in sight, each with how
	 * it looks ("" when no description is authored).
	 */
	record OpenedContainer(int step, AttemptedAction attempt, String name, java.util.List<Found> contents, boolean alreadyOpen)
			implements NarrationFact {
		public OpenedContainer {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(name, "name");
			contents = java.util.List.copyOf(contents);
		}
	}

	/** An item found in an opened container, and how it looks. */
	record Found(String item, String description) {
		public Found {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(description, "description");
		}
	}

	/** Earlier form of {@link OpenedContainer} (contents by name only), kept so summaries stored before it still read. */
	record ContainerOpened(int step, AttemptedAction attempt, String name, java.util.List<String> contents, boolean alreadyOpen)
			implements NarrationFact {
		public ContainerOpened {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(name, "name");
			contents = java.util.List.copyOf(contents);
		}
	}

	/**
	 * The player took an item from a container; it is now on their tool belt, and the container no
	 * longer holds it. {@code description}: how the item looks in the hand ("" when none is authored).
	 */
	record TookItem(int step, AttemptedAction attempt, String item, String description, String from) implements NarrationFact {
		public TookItem {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(description, "description");
			Objects.requireNonNull(from, "from");
		}
	}

	/** Earlier form of {@link TookItem}, kept so summaries stored before it still read. */
	record ItemTaken(int step, AttemptedAction attempt, String item, String from) implements NarrationFact {
		public ItemTaken {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(from, "from");
		}
	}

	/**
	 * An interaction that did not happen, and why: out of reach (with where it is), shut, empty, or no
	 * room on the tool belt.
	 */
	record InteractionFailed(int step, AttemptedAction attempt, String name,
			com.leeburke.springgame.action.resolution.StepResult.InteractionFailure reason, Optional<String> where)
			implements NarrationFact {
		public InteractionFailed {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(where, "where");
		}
	}

	record PlayerAttacked(int step, AttemptedAction attempt, String weaponName, String targetName, Optional<BodyPart> bodyPart,
			ContactQuality contact, int hpDamage, Optional<ImpactSeverity> impact, boolean noContact,
			boolean contactWithoutDamage) implements NarrationFact {
		public PlayerAttacked {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(weaponName, "weaponName");
			Objects.requireNonNull(targetName, "targetName");
			Objects.requireNonNull(bodyPart, "bodyPart");
			Objects.requireNonNull(contact, "contact");
			Objects.requireNonNull(impact, "impact");
			if (noContact != (contact == ContactQuality.NONE) || contactWithoutDamage != (!noContact && hpDamage == 0)) {
				throw new IllegalArgumentException("Non-event flags must agree with contact and damage");
			}
		}

		public static PlayerAttacked of(int step, AttemptedAction attempt, String weaponName, String targetName,
				Optional<BodyPart> bodyPart, ContactQuality contact, int hpDamage, Optional<ImpactSeverity> impact) {
			boolean noContact = contact == ContactQuality.NONE;
			return new PlayerAttacked(step, attempt, weaponName, targetName, bodyPart, contact, hpDamage, impact, noContact,
					!noContact && hpDamage == 0);
		}
	}

	record PlayerDefended(int step, AttemptedAction attempt, String attackerName, DefenseMethod method, ContactQuality contact,
			int hpDamage, Optional<BodyPart> bodyPart, Optional<ImpactSeverity> impact, boolean avoided,
			boolean contactWithoutDamage) implements NarrationFact {
		public PlayerDefended {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(attackerName, "attackerName");
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(contact, "contact");
			Objects.requireNonNull(bodyPart, "bodyPart");
			Objects.requireNonNull(impact, "impact");
			if (avoided != (contact == ContactQuality.NONE) || contactWithoutDamage != (!avoided && hpDamage == 0)) {
				throw new IllegalArgumentException("Non-event flags must agree with contact and damage");
			}
		}

		public static PlayerDefended of(int step, AttemptedAction attempt, String attackerName, DefenseMethod method,
				ContactQuality contact, int hpDamage, Optional<BodyPart> bodyPart, Optional<ImpactSeverity> impact) {
			boolean avoided = contact == ContactQuality.NONE;
			return new PlayerDefended(step, attempt, attackerName, method, contact, hpDamage, bodyPart, impact, avoided,
					!avoided && hpDamage == 0);
		}
	}

	record PlayerMoved(int step, AttemptedAction attempt, String fromZone, String toZone) implements NarrationFact {
		public PlayerMoved {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(fromZone, "fromZone");
			Objects.requireNonNull(toZone, "toZone");
		}
	}

	/** The player did not move: holding position, or a zone that could not be reached. */
	record PlayerStayed(int step, AttemptedAction attempt, String zone, Optional<String> attemptedZone)
			implements NarrationFact {
		public PlayerStayed {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(zone, "zone");
			Objects.requireNonNull(attemptedZone, "attemptedZone");
		}
	}

	/** The player went through an exit and arrived in another scene, both named as the player now sees them. */
	record PlayerLeftScene(int step, AttemptedAction attempt, String destinationScene, String arrivalZone)
			implements NarrationFact {
		public PlayerLeftScene {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(destinationScene, "destinationScene");
			Objects.requireNonNull(arrivalZone, "arrivalZone");
		}
	}

	/**
	 * What the player finds on arriving in another scene, from a fresh player-visible view of where they
	 * now stand. Follows the {@link PlayerLeftScene} of the same step. (A separate fact, so narration
	 * contexts stored before it existed still read back.)
	 */
	record PlayerArrived(int step, AttemptedAction attempt, Surroundings surroundings) implements NarrationFact {
		public PlayerArrived {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(surroundings, "surroundings");
		}
	}

	/**
	 * The player took in their surroundings or looked at something visible. The surroundings are
	 * exactly what the player can see from where they stood: nothing hidden was found.
	 */
	record PlayerObserved(int step, AttemptedAction attempt, Surroundings surroundings) implements NarrationFact {
		public PlayerObserved {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(surroundings, "surroundings");
		}
	}

	/** The player tried to leave through an exit that is not in their zone; they did not move. */
	record ExitNotReached(int step, AttemptedAction attempt, String zone) implements NarrationFact {
		public ExitNotReached {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(zone, "zone");
		}
	}

	/**
	 * The player spoke. The words are in the attempt's {@code spokenWords}, present because this
	 * step resolved. No reaction is implied.
	 */
	record PlayerSpoke(int step, AttemptedAction attempt, CommunicationKind kind, Optional<String> addressee)
			implements NarrationFact {
		public PlayerSpoke {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(addressee, "addressee");
		}
	}

	/** The step did not happen. Its attempt never carries spoken words. */
	record StepCancelled(int step, AttemptedAction attempt, CancellationReason reason) implements NarrationFact {
		public StepCancelled {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(reason, "reason");
			if (attempt.spokenWords().isPresent()) {
				throw new IllegalArgumentException("A cancelled step said nothing");
			}
		}

		public ActionType action() {
			return attempt.action();
		}
	}

	/** A valid action with no mechanics yet: nothing happened mechanically. Its attempt never carries spoken words. */
	record StepHadNoEffect(int step, AttemptedAction attempt, UnavailableReason reason) implements NarrationFact {
		public StepHadNoEffect {
			Objects.requireNonNull(attempt, "attempt");
			Objects.requireNonNull(reason, "reason");
			if (attempt.spokenWords().isPresent()) {
				throw new IllegalArgumentException("A step without effect said nothing");
			}
		}

		public ActionType action() {
			return attempt.action();
		}
	}
}
