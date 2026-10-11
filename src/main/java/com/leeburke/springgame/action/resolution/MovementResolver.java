package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Optional;

import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneState;

/**
 * Resolves the movement the current world model supports: holding position, and moving to the
 * same or a directly connected zone through a connection the player knows (not hidden). These are
 * automatic; no roll. Exit traversal, range-based movement (closing or creating distance,
 * circling, disengaging), climbing, cover and entity-relative movement are reported unavailable;
 * no range state is invented and nothing is revealed.
 */
final class MovementResolver {

	StepOutcome resolve(ActionStep step, MovePayload move, SceneState scene, String currentZone) {
		if (move.movementType() == MovementType.HOLD_POSITION) {
			return resolved(step, StepSuccess.SUCCESS, new StepResult.MovementResult(currentZone, currentZone, false), List.of());
		}
		if (move.movementType() == MovementType.ADVANCE && move.goal() == RelativeGoal.NONE
				&& move.target() instanceof ActionTarget.ExitTarget exit) {
			return throughExit(step, exit.exitId(), scene, currentZone);
		}
		if (move.goal() == RelativeGoal.NONE && move.target() instanceof ActionTarget.ObjectTarget object
				&& (move.movementType() == MovementType.REPOSITION || move.movementType() == MovementType.ADVANCE
						|| move.movementType() == MovementType.CLOSE_DISTANCE)) {
			return towardObject(step, object.objectId(), scene, currentZone);
		}
		boolean zoneMove = (move.movementType() == MovementType.REPOSITION || move.movementType() == MovementType.ADVANCE)
				&& move.goal() == RelativeGoal.NONE
				&& move.target() instanceof ActionTarget.ZoneTarget;
		if (!zoneMove) {
			return StepOutcome.unavailable(step.id(), ActionType.MOVE, UnavailableReason.ACTION_NOT_IMPLEMENTED);
		}
		String destination = ((ActionTarget.ZoneTarget) move.target()).zoneId();
		if (destination.equals(currentZone)) {
			return resolved(step, StepSuccess.SUCCESS, new StepResult.MovementResult(currentZone, destination, false), List.of());
		}
		if (!knownConnection(scene, currentZone, destination)) {
			return resolved(step, StepSuccess.FAILURE, new StepResult.MovementResult(currentZone, destination, false), List.of());
		}
		return resolved(step, StepSuccess.SUCCESS, new StepResult.MovementResult(currentZone, destination, true),
				List.of(new OutcomeEffect.PlayerMoved(currentZone, destination)));
	}

	/**
	 * Going toward an object: one move along the shortest passage the player knows toward its zone,
	 * never more (a longer approach is one move per step). Already beside it: no move. No known way:
	 * an automatic failure, with no movement and nothing revealed.
	 */
	private static StepOutcome towardObject(ActionStep step, String objectId, SceneState scene, String currentZone) {
		String objectZone = scene.objects().stream().filter(o -> o.id().equals(objectId)).findFirst()
				.map(com.leeburke.springgame.world.SceneObject::zoneId).orElse(currentZone);
		if (objectZone.equals(currentZone)) {
			return resolved(step, StepSuccess.SUCCESS, new StepResult.MovementResult(currentZone, currentZone, false), List.of());
		}
		return com.leeburke.springgame.world.SceneKnowledge.nextStepToward(scene, currentZone, objectZone)
				.map(next -> resolved(step, StepSuccess.SUCCESS, new StepResult.MovementResult(currentZone, next, true),
						List.of(new OutcomeEffect.PlayerMoved(currentZone, next))))
				.orElseGet(() -> resolved(step, StepSuccess.FAILURE, new StepResult.MovementResult(currentZone, objectZone, false),
						List.of()));
	}

	/**
	 * Leaving through a known exit: automatic success from the exit's own zone, automatic failure from
	 * anywhere else (the player must first move to it). The destination is not resolution's concern.
	 */
	private static StepOutcome throughExit(ActionStep step, String exitId, SceneState scene, String currentZone) {
		boolean here = scene.exits().stream().anyMatch(x -> x.id().equals(exitId) && x.zoneId().equals(currentZone))
				&& !scene.isHidden(HiddenContentKind.EXIT, exitId);
		if (!here) {
			return resolved(step, StepSuccess.FAILURE, new StepResult.ExitResult(exitId), List.of());
		}
		return resolved(step, StepSuccess.SUCCESS, new StepResult.ExitResult(exitId), List.of(new OutcomeEffect.LeftScene(exitId)));
	}

	private static boolean knownConnection(SceneState scene, String a, String b) {
		return scene.connections().stream()
				.filter(c -> !scene.isHidden(HiddenContentKind.CONNECTION, c.id()))
				.anyMatch(c -> (c.zoneA().equals(a) && c.zoneB().equals(b)) || (c.zoneA().equals(b) && c.zoneB().equals(a)));
	}

	private static StepOutcome resolved(ActionStep step, StepSuccess success, StepResult result, List<OutcomeEffect> effects) {
		return StepOutcome.resolved(step.id(), ActionType.MOVE, success, Optional.empty(), result, effects);
	}
}
