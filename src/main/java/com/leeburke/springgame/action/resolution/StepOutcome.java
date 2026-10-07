package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.Refs;
import com.leeburke.springgame.mechanics.CheckResult;

/**
 * What happened to one step.
 * <ul>
 * <li>RESOLVED: has a success level; may have a check (absent for automatic steps, so no roll is
 * ever faked), a typed result and effects.</li>
 * <li>CANCELLED: never executed; has only a cancellation reason.</li>
 * <li>MECHANICS_UNAVAILABLE: valid, but its mechanics are not implemented; has only a reason. Not a
 * failure: nothing was attempted.</li>
 * </ul>
 */
public record StepOutcome(
		String stepId,
		ActionType actionType,
		StepStatus status,
		Optional<StepSuccess> success,
		Optional<CheckResult> check,
		Optional<StepResult> result,
		List<OutcomeEffect> effects,
		Optional<CancellationReason> cancellation,
		Optional<UnavailableReason> unavailable) {

	public StepOutcome {
		Refs.require(stepId, "stepId");
		Objects.requireNonNull(actionType, "actionType");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(success, "success");
		Objects.requireNonNull(check, "check");
		Objects.requireNonNull(result, "result");
		effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
		Objects.requireNonNull(cancellation, "cancellation");
		Objects.requireNonNull(unavailable, "unavailable");
		boolean onlyReason = success.isEmpty() && check.isEmpty() && result.isEmpty() && effects.isEmpty();
		switch (status) {
			case RESOLVED -> {
				if (success.isEmpty() || cancellation.isPresent() || unavailable.isPresent()) {
					throw new IllegalArgumentException("A resolved step has a success level and no cancellation or unavailability");
				}
			}
			case CANCELLED -> {
				if (!onlyReason || cancellation.isEmpty() || unavailable.isPresent()) {
					throw new IllegalArgumentException("A cancelled step has only a cancellation reason");
				}
			}
			case MECHANICS_UNAVAILABLE -> {
				if (!onlyReason || unavailable.isEmpty() || cancellation.isPresent()) {
					throw new IllegalArgumentException("An unavailable step has only an unavailability reason");
				}
			}
		}
	}

	static StepOutcome resolved(String stepId, ActionType type, StepSuccess success, Optional<CheckResult> check,
			StepResult result, List<OutcomeEffect> effects) {
		return new StepOutcome(stepId, type, StepStatus.RESOLVED, Optional.of(success), check, Optional.of(result), effects,
				Optional.empty(), Optional.empty());
	}

	static StepOutcome cancelled(String stepId, ActionType type, CancellationReason reason) {
		return new StepOutcome(stepId, type, StepStatus.CANCELLED, Optional.empty(), Optional.empty(), Optional.empty(),
				List.of(), Optional.of(reason), Optional.empty());
	}

	static StepOutcome unavailable(String stepId, ActionType type, UnavailableReason reason) {
		return new StepOutcome(stepId, type, StepStatus.MECHANICS_UNAVAILABLE, Optional.empty(), Optional.empty(),
				Optional.empty(), List.of(), Optional.empty(), Optional.of(reason));
	}

	boolean succeeded() {
		return status == StepStatus.RESOLVED && success.orElseThrow() == StepSuccess.SUCCESS;
	}
}
