package com.leeburke.springgame.ai;

/** The four AI roles. Run-ending narration is a mode of the Outcome Narrator, not a fifth role. */
public enum AiRole {
	ACTION_INTERPRETER("action-interpreter"),
	OUTCOME_NARRATOR("outcome-narrator"),
	ENEMY_ATTACK_NARRATOR("enemy-attack-narrator"),
	CHARACTER_INTRODUCTION("character-introduction");

	private final String resourceName;

	AiRole(String resourceName) {
		this.resourceName = resourceName;
	}

	/** Base name of the role's prompt resource, for example {@code action-interpreter}. */
	public String resourceName() {
		return resourceName;
	}
}
