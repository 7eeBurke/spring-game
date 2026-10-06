package com.leeburke.springgame.content;

/** Static identity of an active ability. Ability mechanics are deferred. */
public record AbilityDefinition(String code, String displayName) {

	public AbilityDefinition {
		DefinitionFields.requireCode(code);
		DefinitionFields.requireDisplayName(displayName);
	}
}
