package com.leeburke.springgame.content;

/** Static identity of a passive. Passive mechanics are deferred. */
public record PassiveDefinition(String code, String displayName) {

	public PassiveDefinition {
		DefinitionFields.requireCode(code);
		DefinitionFields.requireDisplayName(displayName);
	}
}
