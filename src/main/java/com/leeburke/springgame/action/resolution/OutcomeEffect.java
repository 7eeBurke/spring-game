package com.leeburke.springgame.action.resolution;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.ImpactSeverity;

/**
 * A confirmed state change produced by resolution. Effects describe what must change; applying them
 * to authoritative state and persisting it happens outside the engine. Only effects with
 * implemented mechanics exist.
 */
public sealed interface OutcomeEffect {

	/** A scene entity was struck. Applying HP and injury to the entity belongs to the enemy model. */
	record TargetDamaged(String entityId, int hpDamage, Optional<BodyPart> bodyPart, Optional<ImpactSeverity> impactSeverity)
			implements OutcomeEffect {
		public TargetDamaged {
			Refs.require(entityId, "entityId");
			requireDamage(hpDamage);
			Objects.requireNonNull(bodyPart, "bodyPart");
			Objects.requireNonNull(impactSeverity, "impactSeverity");
		}
	}

	/** The player was struck. Body-part escalation and conditions are not yet applied. */
	record PlayerDamaged(int hpDamage, Optional<BodyPart> bodyPart, Optional<ImpactSeverity> impactSeverity)
			implements OutcomeEffect {
		public PlayerDamaged {
			requireDamage(hpDamage);
			Objects.requireNonNull(bodyPart, "bodyPart");
			Objects.requireNonNull(impactSeverity, "impactSeverity");
		}
	}

	/** The player moved between zones of the current scene. */
	record PlayerMoved(String fromZone, String toZone) implements OutcomeEffect {
		public PlayerMoved {
			Refs.require(fromZone, "fromZone");
			Refs.require(toZone, "toZone");
			if (fromZone.equals(toZone)) {
				throw new IllegalArgumentException("A move needs two different zones");
			}
		}
	}

	private static void requireDamage(int hpDamage) {
		if (hpDamage < 0) {
			throw new IllegalArgumentException("HP damage cannot be negative, but was " + hpDamage);
		}
	}
}
