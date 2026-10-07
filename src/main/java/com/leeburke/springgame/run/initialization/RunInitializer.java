package com.leeburke.springgame.run.initialization;

import java.util.Objects;
import java.util.UUID;

import com.leeburke.springgame.enemy.EnemyRosterGenerator;
import com.leeburke.springgame.world.generation.GeneratedRunWorld;
import com.leeburke.springgame.world.generation.GenerationContextSnapshot;
import com.leeburke.springgame.world.generation.RunWorldGenerator;

/**
 * Generates a new run's complete starting state in memory: the world, then the enemies placed in
 * it. Pure Java; persisting the result is {@code WorldStore}'s job.
 */
public final class RunInitializer {

	private final RunWorldGenerator worlds;
	private final EnemyRosterGenerator enemies;

	public RunInitializer(RunWorldGenerator worlds, EnemyRosterGenerator enemies) {
		this.worlds = Objects.requireNonNull(worlds, "worlds");
		this.enemies = Objects.requireNonNull(enemies, "enemies");
	}

	public RunInitialization initialize(UUID runId, long runSeed, GenerationContextSnapshot context) {
		GeneratedRunWorld world = worlds.generate(runId, runSeed, context);
		return new RunInitialization(world, enemies.generate(world));
	}
}
