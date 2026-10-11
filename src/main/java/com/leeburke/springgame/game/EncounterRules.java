package com.leeburke.springgame.game;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.enemy.EnemyInstance;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneState;

/**
 * When an enemy acts, and which one. One enemy may act per committed turn, chosen round-robin by a
 * persisted cursor over the living, visible enemies in the player's zone, in entity-ID order. A HOLD
 * still advances the cursor; a scene change resets it.
 */
public final class EncounterRules {

	private EncounterRules() {
	}

	/**
	 * The enemy phase runs only after a turn that did something other than defend or look: an ACTIVE
	 * run, the player still in the scene, no attack pending, and at least one RESOLVED step that is
	 * neither DEFEND nor OBSERVE. So a defense-only turn never immediately provokes another attack,
	 * taking in the surroundings is safe, and defending then counterattacking still provokes one.
	 */
	public static boolean enemyPhaseRuns(ResolvedOutcome outcome, RunStatus status, boolean leftScene, boolean attackPending) {
		return enemyPhaseRuns(outcome, Set.of(), status, leftScene, attackPending);
	}

	/** @param idleSteps steps that did nothing ({@link IdleSteps}); they never provoke an enemy */
	public static boolean enemyPhaseRuns(ResolvedOutcome outcome, Set<String> idleSteps, RunStatus status, boolean leftScene,
			boolean attackPending) {
		return status == RunStatus.ACTIVE && !leftScene && !attackPending
				&& outcome.steps().stream().anyMatch(s -> s.status() == StepStatus.RESOLVED && !idleSteps.contains(s.stepId())
						&& s.actionType() != ActionType.DEFEND && s.actionType() != ActionType.OBSERVE);
	}

	/**
	 * The enemies that may act: those standing in the player's zone after the turn's moves. There is
	 * no enemy movement or attack range yet, so a creature in another zone (beside, farther, or not
	 * yet seen) cannot reach the player and never acts. Walking into its zone lets it act that turn.
	 */
	public static List<EnemyInstance> withinReach(List<EnemyInstance> enemies, SceneState scene, String playerZone) {
		Set<String> here = scene.entities().stream().filter(e -> e.zoneId().equals(playerZone)).map(SceneEntity::id)
				.collect(java.util.stream.Collectors.toSet());
		return enemies.stream().filter(e -> here.contains(e.entityId())).toList();
	}

	/**
	 * The next enemy to act.
	 *
	 * @param eligible living, visible enemies in the player's zone ({@link #withinReach})
	 * @param cursor   the last enemy to act, if any
	 */
	public static Optional<EnemyInstance> nextActor(List<EnemyInstance> eligible, UUID sceneId, Optional<RunSession.EnemyCursor> cursor) {
		List<EnemyInstance> ordered = eligible.stream().filter(e -> e.currentHp() > 0)
				.sorted(Comparator.comparing(EnemyInstance::entityId)).toList();
		if (ordered.isEmpty()) {
			return Optional.empty();
		}
		Optional<String> last = cursor.filter(c -> c.sceneId().equals(sceneId)).map(RunSession.EnemyCursor::entityId);
		if (last.isEmpty()) {
			return Optional.of(ordered.getFirst());
		}
		return ordered.stream().filter(e -> e.entityId().compareTo(last.get()) > 0).findFirst()
				.or(() -> Optional.of(ordered.getFirst()));
	}
}
