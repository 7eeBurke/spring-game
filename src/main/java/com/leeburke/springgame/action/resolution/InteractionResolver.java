package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.leeburke.springgame.action.ActionPayload.InteractPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.resolution.StepResult.InteractionFailure;
import com.leeburke.springgame.action.resolution.StepResult.InteractionResult;
import com.leeburke.springgame.world.ContainerState;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;

/**
 * The first slice of interaction: opening a container and taking what it holds. Automatic, with no
 * roll (no difficulty is designed for these). The player must be beside the container, in its zone;
 * nothing walks them there. Out of reach, the attempt cannot begin: it is unavailable
 * ({@link UnavailableReason#OUT_OF_REACH}), never a failure. Closed, empty or no room are failures:
 * the character got as far as trying.
 * <ul>
 * <li>OPEN: a closed container opens (its contents become visible); an open one stays open.</li>
 * <li>PICK_UP from a container: its item goes onto the tool belt, if the container is open, holds
 * something and the belt has room; otherwise a failure says which.</li>
 * </ul>
 * Anything else (another kind of interaction, or an object that is not a container) has no mechanics
 * yet and is reported as such, never as a failure.
 */
final class InteractionResolver {

	/**
	 * @param containers every container's state as the steps play out
	 * @param beltRoom   free tool-belt slots as the steps play out
	 */
	StepOutcome resolve(ActionStep step, InteractPayload interact, SceneState scene, String currentZone,
			Map<String, ContainerState> containers, int beltRoom) {
		boolean supported = interact.kind() == InteractionKind.OPEN || interact.kind() == InteractionKind.PICK_UP;
		if (!supported || !(interact.target() instanceof ActionTarget.ObjectTarget target)) {
			return StepOutcome.unavailable(step.id(), ActionType.INTERACT, UnavailableReason.ACTION_NOT_IMPLEMENTED);
		}
		ContainerState container = containers.get(target.objectId());
		Optional<SceneObject> object = scene.objects().stream().filter(o -> o.id().equals(target.objectId())).findFirst();
		if (container == null || object.isEmpty()) {
			return StepOutcome.unavailable(step.id(), ActionType.INTERACT, UnavailableReason.ACTION_NOT_IMPLEMENTED);
		}
		if (!object.get().zoneId().equals(currentZone)) {
			// Not beside it: nothing can be tried, so this is not a failure (an earlier move in the same
			// intent still counts; on its own, the request is refused without spending a turn).
			return StepOutcome.unavailable(step.id(), ActionType.INTERACT, UnavailableReason.OUT_OF_REACH);
		}
		if (interact.kind() == InteractionKind.OPEN) {
			return container.open()
					? StepOutcome.resolved(step.id(), ActionType.INTERACT, StepSuccess.SUCCESS, Optional.empty(),
							new InteractionResult(InteractionKind.OPEN, target.objectId(), Optional.empty(), Optional.empty(), true,
									container.contents()), List.of())
					: StepOutcome.resolved(step.id(), ActionType.INTERACT, StepSuccess.SUCCESS, Optional.empty(),
							new InteractionResult(InteractionKind.OPEN, target.objectId(), Optional.empty(), Optional.empty(), false,
									container.contents()),
							List.of(new OutcomeEffect.ContainerOpened(target.objectId())));
		}
		if (!container.open()) {
			return failed(step, interact.kind(), target.objectId(), InteractionFailure.CLOSED);
		}
		if (container.contents().isEmpty()) {
			return failed(step, interact.kind(), target.objectId(), InteractionFailure.EMPTY);
		}
		if (beltRoom <= 0) {
			return failed(step, interact.kind(), target.objectId(), InteractionFailure.NO_ROOM);
		}
		String item = container.contents().getFirst();
		return StepOutcome.resolved(step.id(), ActionType.INTERACT, StepSuccess.SUCCESS, Optional.empty(),
				new InteractionResult(InteractionKind.PICK_UP, target.objectId(), Optional.of(item), Optional.empty(), false, List.of()),
				List.of(new OutcomeEffect.ItemTaken(target.objectId(), item)));
	}

	private static StepOutcome failed(ActionStep step, InteractionKind kind, String objectId, InteractionFailure why) {
		return StepOutcome.resolved(step.id(), ActionType.INTERACT, StepSuccess.FAILURE, Optional.empty(),
				new InteractionResult(kind, objectId, Optional.empty(), Optional.of(why), false, List.of()), List.of());
	}
}
