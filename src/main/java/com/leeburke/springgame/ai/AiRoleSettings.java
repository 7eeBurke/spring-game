package com.leeburke.springgame.ai;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** The generation settings of each role, built from configuration. */
public final class AiRoleSettings {

	private final Map<AiRole, AiGenerationSettings> settings;

	public AiRoleSettings(Map<AiRole, AiGenerationSettings> settings) {
		Objects.requireNonNull(settings, "settings");
		this.settings = new EnumMap<>(AiRole.class);
		for (AiRole role : AiRole.values()) {
			this.settings.put(role, Objects.requireNonNull(settings.get(role), "settings for " + role));
		}
	}

	public AiGenerationSettings forRole(AiRole role) {
		return settings.get(role);
	}
}
