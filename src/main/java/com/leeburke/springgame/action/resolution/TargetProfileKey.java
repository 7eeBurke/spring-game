package com.leeburke.springgame.action.resolution;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * Looks up a {@link TargetCombatProfile}: an entity, attacked either without a named body part
 * (empty) or at one specific part. Lookups are exact; there is no fallback between parts.
 */
public record TargetProfileKey(String entityId, Optional<BodyPart> bodyPart) {

	public TargetProfileKey {
		Refs.require(entityId, "Target entity id");
		Objects.requireNonNull(bodyPart, "bodyPart");
	}

	public static TargetProfileKey wholeTarget(String entityId) {
		return new TargetProfileKey(entityId, Optional.empty());
	}

	public static TargetProfileKey at(String entityId, BodyPart bodyPart) {
		return new TargetProfileKey(entityId, Optional.of(bodyPart));
	}
}
