package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.DamageResult;
import com.leeburke.springgame.mechanics.TraumaResult;

/** Confirmed, action-specific mechanical facts of a resolved step. No prose. */
public sealed interface StepResult {

	/** A player attack: contact derived from the check, damage and trauma from Stage 4 calculators. */
	record AttackResult(
			String weaponRef,
			String weaponCode,
			String targetEntityId,
			Optional<BodyPart> targetedBodyPart,
			ContactQuality contact,
			DamageResult damage,
			TraumaResult trauma) implements StepResult {
		public AttackResult {
			Refs.require(weaponRef, "weaponRef");
			Refs.require(weaponCode, "weaponCode");
			Refs.require(targetEntityId, "targetEntityId");
			Objects.requireNonNull(targetedBodyPart, "targetedBodyPart");
			Objects.requireNonNull(contact, "contact");
			Objects.requireNonNull(damage, "damage");
			Objects.requireNonNull(trauma, "trauma");
		}
	}

	/** The player's defense against an incoming attack, and what got through. */
	record DefenseResult(
			String attackRef,
			DefenseMethod method,
			ContactQuality incomingContact,
			DamageResult damage,
			TraumaResult trauma) implements StepResult {
		public DefenseResult {
			Refs.require(attackRef, "attackRef");
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(incomingContact, "incomingContact");
			Objects.requireNonNull(damage, "damage");
			Objects.requireNonNull(trauma, "trauma");
		}
	}

	/** Zone-to-zone movement within the current scene. */
	record MovementResult(String fromZone, String toZone, boolean moved) implements StepResult {
		public MovementResult {
			Refs.require(fromZone, "fromZone");
			Refs.require(toZone, "toZone");
			if (moved && fromZone.equals(toZone)) {
				throw new IllegalArgumentException("Moving to the same zone is not a move");
			}
		}
	}

	/** The player went through a known exit in their current zone, leaving the scene. */
	record ExitResult(String exitId) implements StepResult {
		public ExitResult {
			Refs.require(exitId, "exitId");
		}
	}

	/**
	 * The player took in their surroundings, or looked at something visible. Automatic: it reads only
	 * what the player can already see and never reveals hidden content (searching for hidden things
	 * is not a mechanic yet).
	 */
	/**
	 * An interaction with a container: what was tried on which object, the item taken (when one was),
	 * and, for a failure, why. {@code alreadyOpen} marks opening something that was open already;
	 * {@code contents} is what an opened container holds, now in sight.
	 */
	record InteractionResult(InteractionKind kind, String objectId, Optional<String> itemCode,
			Optional<InteractionFailure> failure, boolean alreadyOpen, List<String> contents) implements StepResult {
		public InteractionResult {
			Objects.requireNonNull(kind, "kind");
			Refs.require(objectId, "objectId");
			Objects.requireNonNull(itemCode, "itemCode");
			Objects.requireNonNull(failure, "failure");
			contents = List.copyOf(contents);
		}
	}

	/** Why an interaction with a container did not happen. */
	enum InteractionFailure {
		/**
		 * The player is not beside it. Resolution now reports this as unavailable
		 * ({@link UnavailableReason#OUT_OF_REACH}), since nothing could be tried; kept for turns stored
		 * earlier and as the narration reason for both.
		 */
		OUT_OF_REACH,
		/** Taking from a container that is shut. */
		CLOSED,
		/** Taking from a container with nothing in it. */
		EMPTY,
		/** The tool belt is full. */
		NO_ROOM
	}

	record ObservationResult(ObservationKind kind) implements StepResult {
		public ObservationResult {
			Objects.requireNonNull(kind, "kind");
		}
	}

	/** Something was said. Social consequences are not resolved yet. */
	record CommunicationResult(CommunicationKind kind, Optional<String> addresseeEntityId) implements StepResult {
		public CommunicationResult {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(addresseeEntityId, "addresseeEntityId");
			addresseeEntityId.ifPresent(id -> Refs.require(id, "addresseeEntityId"));
		}
	}
}
