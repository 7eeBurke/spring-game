package com.leeburke.springgame.game;

import java.util.random.RandomGenerator;

import com.leeburke.springgame.world.generation.WorldRandom;

/**
 * Gameplay random streams, derived from the run seed with the same SplitMix64 derivation as world
 * generation but separate domains. Each turn number gets fixed streams for the player's checks and
 * the enemy's decision, so recomputing a turn can never reroll it. The run seed itself is never
 * exposed and is not used as one long mutable stream.
 */
public final class TurnRandom {

	/** Character generation at run creation. */
	public static final long CHARACTER_DOMAIN = 10;
	/** The player's checks on a turn. */
	public static final long PLAYER_TURN_DOMAIN = 20;
	/** The acting enemy's decision on a turn. */
	public static final long ENEMY_TURN_DOMAIN = 21;

	private TurnRandom() {
	}

	public static RandomGenerator character(long runSeed) {
		return WorldRandom.create(WorldRandom.derive(runSeed, CHARACTER_DOMAIN, 0));
	}

	public static RandomGenerator player(long runSeed, int turnNumber) {
		return WorldRandom.create(WorldRandom.derive(runSeed, PLAYER_TURN_DOMAIN, turnNumber));
	}

	public static RandomGenerator enemy(long runSeed, int turnNumber) {
		return WorldRandom.create(WorldRandom.derive(runSeed, ENEMY_TURN_DOMAIN, turnNumber));
	}
}
