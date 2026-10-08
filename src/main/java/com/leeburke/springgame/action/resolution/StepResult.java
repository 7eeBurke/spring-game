package com.leeburke.springgame.action.resolution;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.DefenseMethod;
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

	/** Something was said. Social consequences are not resolved yet. */
	/** The player went through a known exit in their current zone, leaving the scene. */
	record ExitResult(String exitId) implements StepResult {
		public ExitResult {
			Refs.require(exitId, "exitId");
		}
	}

	record CommunicationResult(CommunicationKind kind, Optional<String> addresseeEntityId) implements StepResult {
		public CommunicationResult {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(addresseeEntityId, "addresseeEntityId");
			addresseeEntityId.ifPresent(id -> Refs.require(id, "addresseeEntityId"));
		}
	}
}
