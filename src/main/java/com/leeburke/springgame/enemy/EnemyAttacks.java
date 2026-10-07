package com.leeburke.springgame.enemy;

import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.content.enemy.EnemyAttackOption;

/**
 * Builds Stage 11's {@link IncomingAttack} for a chosen enemy attack option. Damage and trauma come
 * from the enemy's weapon definition, difficulty from the enemy's stats; effectiveness, attack form
 * and anatomy interaction use the Stage 12 baselines, and no hit location is invented. Nothing is
 * rolled and no damage is computed: Stage 11 resolves the player's defense.
 */
public final class EnemyAttacks {

	private EnemyAttacks() {
	}

	/**
	 * @param attackRef the opaque reference the caller gives this incoming attack
	 * @throws IllegalArgumentException if the option is not one of this enemy's
	 */
	public static IncomingAttack build(String attackRef, EnemyCombatant enemy, EnemyAttackOption option) {
		Refs.require(attackRef, "Incoming attack reference");
		Objects.requireNonNull(enemy, "enemy");
		Objects.requireNonNull(option, "option");
		if (!enemy.definition().attacks().contains(option)) {
			throw new IllegalArgumentException("Attack option " + option.code() + " does not belong to " + enemy.definition().code());
		}
		return new IncomingAttack(attackRef, enemy.entityId(), option.template(),
				EnemyRules.attackDifficulty(enemy.instance().stats(), option.method()),
				enemy.weapon().baseDamage(), enemy.weapon().trauma(),
				EnemyRules.EFFECTIVENESS_BASELINE, EnemyRules.ATTACK_FORM_BASELINE, EnemyRules.ANATOMY_INTERACTION_BASELINE,
				Optional.empty());
	}
}
