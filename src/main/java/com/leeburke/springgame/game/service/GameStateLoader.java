package com.leeburke.springgame.game.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.leeburke.springgame.game.GameSnapshot;
import com.leeburke.springgame.game.RunSession;
import com.leeburke.springgame.persistence.EnemyStore;
import com.leeburke.springgame.persistence.GameRunStore;
import com.leeburke.springgame.persistence.PendingAttackStore;
import com.leeburke.springgame.persistence.PersistedStateException;
import com.leeburke.springgame.persistence.WorldStore;
import com.leeburke.springgame.run.GameRun;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneInstance;

/**
 * Loads a {@link GameSnapshot} from the stores. Not transactional itself: callers run it inside
 * their transaction (a read-only one for views, the locked mechanics transaction for a turn) so the
 * snapshot is consistent.
 */
@Component
class GameStateLoader {

	private final GameRunStore runs;
	private final WorldStore world;
	private final EnemyStore enemies;
	private final PendingAttackStore pending;

	GameStateLoader(GameRunStore runs, WorldStore world, EnemyStore enemies, PendingAttackStore pending) {
		this.runs = Objects.requireNonNull(runs, "runs");
		this.world = Objects.requireNonNull(world, "world");
		this.enemies = Objects.requireNonNull(enemies, "enemies");
		this.pending = Objects.requireNonNull(pending, "pending");
	}

	GameSnapshot load(RunSession session) {
		UUID runId = session.runId();
		GameRun run = runs.findRun(runId).orElseThrow(() -> new PersistedStateException("Run " + runId + " is missing"));
		PlayerLocation location = world.findPlayerLocation(runId)
				.orElseThrow(() -> new PersistedStateException("Run " + runId + " has no player location"));
		SceneInstance scene = world.findScene(location.sceneId())
				.orElseThrow(() -> new PersistedStateException("Run " + runId + ": the player's scene is missing"));
		return new GameSnapshot(session, run.runSeed(), run.playerCharacter(), location, scene,
				enemies.findEnemies(scene.id()), pending.find(runId));
	}
}
