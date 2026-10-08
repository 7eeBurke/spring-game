package com.leeburke.springgame.ai.narration;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.content.WeaponDefinition;

/**
 * Everything the Enemy Attack Narrator receives about an attack Java has already chosen: who
 * attacks (visible name), with what weapon (name), and the attack's physical cue. No difficulty,
 * damage, target part, alternative options, weights or stats.
 */
public record EnemyAttackNarrationContext(String attackerName, String weaponName, AttackCue cue, String cuePhrase,
		List<String> requiredKeywords) {

	public EnemyAttackNarrationContext {
		Objects.requireNonNull(attackerName, "attackerName");
		Objects.requireNonNull(weaponName, "weaponName");
		Objects.requireNonNull(cue, "cue");
		Objects.requireNonNull(cuePhrase, "cuePhrase");
		requiredKeywords = List.copyOf(requiredKeywords);
	}

	/** @param names visible names; an attacker the player cannot see is "something unseen" */
	public static EnemyAttackNarrationContext of(IncomingAttack attack, NarrationNames names, WeaponDefinition weapon) {
		AttackCue cue = AttackCue.of(attack.template());
		return new EnemyAttackNarrationContext(names.entity(attack.attackerEntityId()).orElse(OutcomeNarrationContextBuilder.UNSEEN),
				weapon.displayName(), cue, cue.phrase(), cue.keywords());
	}
}
