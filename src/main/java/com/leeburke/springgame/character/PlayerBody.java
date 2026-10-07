package com.leeburke.springgame.character;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;

/**
 * The player's body: exactly one severity for every {@link BodyPart}. Immutable; the caller's map
 * is copied. Any severity can be represented, though V1 generation always starts healthy.
 * Conditions are not modelled yet.
 */
public record PlayerBody(Map<BodyPart, BodySeverity> severities) {

	public PlayerBody {
		Objects.requireNonNull(severities, "severities");
		EnumMap<BodyPart, BodySeverity> copy = new EnumMap<>(BodyPart.class);
		for (Map.Entry<BodyPart, BodySeverity> entry : severities.entrySet()) {
			copy.put(Objects.requireNonNull(entry.getKey(), "body part"),
					Objects.requireNonNull(entry.getValue(), "severity of " + entry.getKey()));
		}
		for (BodyPart part : BodyPart.values()) {
			if (!copy.containsKey(part)) {
				throw new IllegalArgumentException("Missing severity for body part " + part);
			}
		}
		severities = Collections.unmodifiableMap(copy);
	}

	public static PlayerBody healthy() {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(BodyPart.class);
		for (BodyPart part : BodyPart.values()) {
			severities.put(part, BodySeverity.HEALTHY);
		}
		return new PlayerBody(severities);
	}

	public BodySeverity severity(BodyPart part) {
		return severities.get(Objects.requireNonNull(part, "part"));
	}
}
