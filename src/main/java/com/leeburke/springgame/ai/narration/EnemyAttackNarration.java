package com.leeburke.springgame.ai.narration;

import java.util.Objects;

/**
 * An incoming attack as presented to the player: descriptive prose (AI or fallback) plus the
 * mandatory, Java-generated cue, which is always shown separately so the player always knows the
 * attack's physical character whatever the prose says. Neither part can change the attack.
 *
 * @param cueText the Java cue sentence, for example "Incoming: an overhead strike coming down from above."
 */
public record EnemyAttackNarration(Narration prose, AttackCue cue, String cueText) {

	public EnemyAttackNarration {
		Objects.requireNonNull(prose, "prose");
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(cueText, "cueText");
		if (!cueText.equals(cueTextOf(cue))) {
			throw new IllegalArgumentException("The cue text is generated from the cue");
		}
	}

	public static String cueTextOf(AttackCue cue) {
		return "Incoming: " + cue.phrase() + ".";
	}
}
