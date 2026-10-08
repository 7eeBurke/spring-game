package com.leeburke.springgame.game;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.enemy.EnemyInstance;

/**
 * When an enemy acts, and which one. One enemy may act per committed turn, chosen round-robin by a
 * persisted cursor over the living, visible enemies of the current scene in entity-ID order. A HOLD
 * still advances the cursor; a scene change resets it.
 */
public final class EncounterRules {

	private EncounterRules() {
	}

	/**
	 * The enemy phase runs only after a turn that did something other than defend: an ACTIVE run, the
	 * player still in the scene, no attack pending, and at least one RESOLVED step that is not DEFEND.
	 * So a defense-only turn never immediately provokes another attack, and defending then
	 * counterattacking still does.
	 */
	public static boolean enemyPhaseRuns(ResolvedOutcome outcome, RunStatus status, boolean leftScene, boolean attackPending) {
		return status == RunStatus.ACTIVE && !leftScene && !attackPending
				&& outcome.steps().stream().anyMatch(s -> s.status() == StepStatus.RESOLVED && s.actionType() != ActionType.DEFEND);
	}

	/**
	 * The next enemy to act.
	 *
	 * @param eligible living, visible enemies of the current scene
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
