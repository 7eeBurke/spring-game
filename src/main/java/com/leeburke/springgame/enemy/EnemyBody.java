package com.leeburke.springgame.enemy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.content.enemy.AnatomyDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

/**
 * Immutable severity of each body part an enemy has. Unlike the player's body, the set of parts
 * comes from the enemy's anatomy; whether it matches is checked where body and anatomy meet
 * ({@link EnemyCombatant}, generation and loading). Conditions and escalation are deferred.
 */
public record EnemyBody(Map<BodyPart, BodySeverity> severities) {

	public EnemyBody {
		Objects.requireNonNull(severities, "severities");
		EnumMap<BodyPart, BodySeverity> copy = new EnumMap<>(BodyPart.class);
		severities.forEach((part, severity) -> copy.put(Objects.requireNonNull(part, "body part"),
				Objects.requireNonNull(severity, "severity of " + part)));
		if (copy.isEmpty()) {
			throw new IllegalArgumentException("An enemy body has at least one part");
		}
		severities = Collections.unmodifiableMap(copy);
	}

	/** Every part of the anatomy, HEALTHY. */
	public static EnemyBody healthy(AnatomyDefinition anatomy) {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(BodyPart.class);
		anatomy.bodyParts().forEach(part -> severities.put(part, BodySeverity.HEALTHY));
		return new EnemyBody(severities);
	}

	public boolean has(BodyPart part) {
		return severities.containsKey(Objects.requireNonNull(part, "part"));
	}

	/** Empty if the enemy has no such part. */
	public Optional<BodySeverity> severity(BodyPart part) {
		return Optional.ofNullable(severities.get(Objects.requireNonNull(part, "part")));
	}

	/** True when this body has exactly the anatomy's parts. */
	public boolean matches(AnatomyDefinition anatomy) {
		return severities.keySet().equals(anatomy.parts());
	}
}
