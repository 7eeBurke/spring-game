package com.leeburke.springgame.ai.narration;

import java.util.Objects;
import java.util.Optional;

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
public sealed interface NarrationFact {

	/** 1-based step the fact belongs to. */
	int step();

	/** What the step tried to do. */
	AttemptedAction attempt();

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
