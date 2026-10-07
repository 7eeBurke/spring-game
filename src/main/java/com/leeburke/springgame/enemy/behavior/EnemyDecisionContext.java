package com.leeburke.springgame.enemy.behavior;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.content.enemy.EnemyAttackOption;
import com.leeburke.springgame.enemy.EnemyCombatant;

/**
 * Everything an enemy may react to when deciding, and nothing else. Like {@code PlayerSceneView}
 * for the player, this is a boundary: it holds the enemy's own state and definitions and its own
 * recent choices, and deliberately no player information at all (no stats, passive, ability,
 * inventory or HP), because none of it is observable yet. Perception-based exploitation is deferred
 * until an observable player model exists.
 *
 * @param self          the deciding enemy with its checked definitions
 * @param recentChoices the enemy's own previous choices, oldest first: attack option codes of this
 *                      enemy or {@code HOLD}. Copied; supplied by the caller (action history is not
 *                      persisted yet)
 * @param attackRef     the opaque reference to give an incoming attack if one is chosen
 */
public record EnemyDecisionContext(EnemyCombatant self, List<String> recentChoices, String attackRef) {

	public EnemyDecisionContext {
		Objects.requireNonNull(self, "self");
		recentChoices = List.copyOf(Objects.requireNonNull(recentChoices, "recentChoices"));
		for (String choice : recentChoices) {
			Refs.require(choice, "Recent choice");
			if (!choice.equals(EnemyAttackOption.HOLD_CODE) && self.definition().findAttack(choice).isEmpty()) {
				throw new IllegalArgumentException("Recent choice " + choice + " is not HOLD or an attack of "
						+ self.definition().code());
			}
		}
		Refs.require(attackRef, "Incoming attack reference");
	}
}
