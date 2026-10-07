package com.leeburke.springgame.enemy.behavior;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.action.resolution.IncomingAttack;

/**
 * What an enemy decided. No prose, no damage and no defense roll: an attack is handed to Stage 11
 * as an {@link IncomingAttack}, and the player's defense decides what happens.
 */
public sealed interface EnemyDecision {

	/** The final weights the choice was made from, in candidate order. */
	List<WeightedCandidate> weights();

	record Attack(String optionCode, IncomingAttack attack, List<WeightedCandidate> weights) implements EnemyDecision {
		public Attack {
			Refs.require(optionCode, "Attack option code");
			Objects.requireNonNull(attack, "attack");
			weights = List.copyOf(Objects.requireNonNull(weights, "weights"));
		}
	}

	/** The enemy holds back this time: no incoming attack is produced. */
	record Hold(List<WeightedCandidate> weights) implements EnemyDecision {
		public Hold {
			weights = List.copyOf(Objects.requireNonNull(weights, "weights"));
		}
	}
}
