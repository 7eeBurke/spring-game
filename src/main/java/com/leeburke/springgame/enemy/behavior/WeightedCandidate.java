package com.leeburke.springgame.enemy.behavior;

import com.leeburke.springgame.action.Refs;

/** One behaviour candidate (an attack option code or {@code HOLD}) and its final, non-negative weight. */
public record WeightedCandidate(String code, long weight) {

	public WeightedCandidate {
		Refs.require(code, "Candidate code");
		if (weight < 0) {
			throw new IllegalArgumentException("Candidate weight cannot be negative, but was " + weight);
		}
	}
}
