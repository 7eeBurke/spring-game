package com.leeburke.springgame.game;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepOutcome;
import com.leeburke.springgame.action.resolution.StepResult;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.action.resolution.StepSuccess;

/**
 * Steps that resolved but did nothing meaningful: a move to the zone the player was already in, or
 * toward an object they were already beside.
 * Resolution still treats such a move as a success (so "go to the road, and if that works, take the
 * exit" behaves the same whether or not the player was already on the road), but a turn made only
 * of idle steps is not a turn, and idle steps never provoke an enemy.
 * <p>
 * Holding position is a deliberate wait and is never idle; nor is a blocked move (a failure).
 */
public final class IdleSteps {

	private IdleSteps() {
	}

	/** The IDs of the idle steps of a resolved intent. */
	public static Set<String> of(ResolvedOutcome outcome, ActionIntent intent) {
		Map<String, ActionStep> steps = intent.steps().stream().collect(Collectors.toMap(ActionStep::id, Function.identity()));
		return outcome.steps().stream()
				.filter(step -> idle(step, steps.get(step.stepId())))
				.map(StepOutcome::stepId)
				.collect(Collectors.toUnmodifiableSet());
	}

	private static boolean idle(StepOutcome outcome, ActionStep step) {
		return step != null
				&& outcome.status() == StepStatus.RESOLVED
				&& outcome.success().filter(StepSuccess.SUCCESS::equals).isPresent()
				&& outcome.result().filter(r -> r instanceof StepResult.MovementResult m && !m.moved() && m.fromZone().equals(m.toZone()))
						.isPresent()
				&& step.payload() instanceof ActionPayload.MovePayload move
				&& (move.movementType() == MovementType.REPOSITION || move.movementType() == MovementType.ADVANCE
						|| move.movementType() == MovementType.CLOSE_DISTANCE)
				&& (move.target() instanceof ActionTarget.ZoneTarget || move.target() instanceof ActionTarget.ObjectTarget);
	}
}
