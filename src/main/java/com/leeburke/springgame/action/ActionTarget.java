package com.leeburke.springgame.action;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.mechanics.BodyPart;

/**
 * What a step is aimed at. Scene targets use the scene-local IDs exposed by
 * {@code PlayerSceneView}; an exit target is only the known exit's local ID, never its destination.
 * A body part can be named only on a creature ({@link EntityTarget}) or on the player
 * ({@link SelfTarget}); anatomy is not checked here.
 * <p>
 * Referencing targets are always {@link TargetSpecificity#EXPLICIT} or
 * {@link TargetSpecificity#INFERRED}; only {@link Unspecified} is {@code UNSPECIFIED}, and it holds
 * no reference.
 */
public sealed interface ActionTarget {

	TargetSpecificity specificity();

	record EntityTarget(String entityId, Optional<BodyPart> bodyPart, TargetSpecificity specificity) implements ActionTarget {
		public EntityTarget {
			Refs.require(entityId, "Entity target id");
			Objects.requireNonNull(bodyPart, "bodyPart");
			requireReferencing(specificity);
		}
	}

	record ObjectTarget(String objectId, TargetSpecificity specificity) implements ActionTarget {
		public ObjectTarget {
			Refs.require(objectId, "Object target id");
			requireReferencing(specificity);
		}
	}

	record HazardTarget(String hazardId, TargetSpecificity specificity) implements ActionTarget {
		public HazardTarget {
			Refs.require(hazardId, "Hazard target id");
			requireReferencing(specificity);
		}
	}

	record ZoneTarget(String zoneId, TargetSpecificity specificity) implements ActionTarget {
		public ZoneTarget {
			Refs.require(zoneId, "Zone target id");
			requireReferencing(specificity);
		}
	}

	/** A known exit, by its local ID only. */
	record ExitTarget(String exitId, TargetSpecificity specificity) implements ActionTarget {
		public ExitTarget {
			Refs.require(exitId, "Exit target id");
			requireReferencing(specificity);
		}
	}

	/** The player character themself, for example "use the bandage on my left arm". Not a scene entity. */
	record SelfTarget(Optional<BodyPart> bodyPart, TargetSpecificity specificity) implements ActionTarget {
		public SelfTarget {
			Objects.requireNonNull(bodyPart, "bodyPart");
			requireReferencing(specificity);
		}
	}

	/** No target given. Holds no reference. */
	record Unspecified() implements ActionTarget {
		@Override
		public TargetSpecificity specificity() {
			return TargetSpecificity.UNSPECIFIED;
		}
	}

	static ActionTarget unspecified() {
		return new Unspecified();
	}

	private static void requireReferencing(TargetSpecificity specificity) {
		Objects.requireNonNull(specificity, "specificity");
		if (specificity == TargetSpecificity.UNSPECIFIED) {
			throw new IllegalArgumentException("A target that names something must be EXPLICIT or INFERRED");
		}
	}
}
