package com.leeburke.springgame.ai.narration;

import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiRole;

/**
 * The Enemy Attack Narrator role: describes an attack Java already chose, so the player can pick a
 * defense. The result always carries the Java-generated cue separately, so the prose may use its
 * own wording; it is accepted if it is non-blank, at most {@value #MAX_LENGTH} characters and states
 * no numbers. Otherwise, or on any failure, the deterministic fallback prose is used. The narrator
 * only receives names and the cue, so it cannot change the attack or the cue.
 */
public final class EnemyAttackNarrator {

	static final int MAX_LENGTH = 600;

	private final TextRole role;

	public EnemyAttackNarrator(AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings) {
		this.role = new TextRole(AiRole.ENEMY_ATTACK_NARRATOR, provider, instructions, promptVersion, settings);
	}

	public EnemyAttackNarration narrate(EnemyAttackNarrationContext context) {
		Narration prose = role.narrate(new Input(context),
				text -> text.length() <= MAX_LENGTH && text.chars().noneMatch(Character::isDigit),
				() -> fallback(context));
		return new EnemyAttackNarration(prose, context.cue(), EnemyAttackNarration.cueTextOf(context.cue()));
	}

	/** Deterministic prose; it also contains the cue phrase. */
	public static String fallback(EnemyAttackNarrationContext context) {
		return "The " + context.attackerName() + " comes at you with its " + context.weaponName() + ": "
				+ context.cuePhrase() + ".";
	}

	record Input(EnemyAttackNarrationContext attack) {
	}
}
