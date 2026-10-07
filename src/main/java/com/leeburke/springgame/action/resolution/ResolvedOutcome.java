package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.Refs;

/**
 * Confirmed backend truth about how an intent resolved: never AI output, never prose. Contains the
 * ordered step outcomes with their typed results and effects. Narration facts are derived later
 * from these typed outcomes.
 */
public record ResolvedOutcome(
		int schemaVersion,
		Optional<String> responseToAttack,
		OverallResult overall,
		List<StepOutcome> steps,
		ResolutionMetadata metadata) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public ResolvedOutcome {
		if (schemaVersion < 1) {
			throw new IllegalArgumentException("Schema version must be at least 1");
		}
		Objects.requireNonNull(responseToAttack, "responseToAttack");
		responseToAttack.ifPresent(ref -> Refs.require(ref, "responseToAttack"));
		Objects.requireNonNull(overall, "overall");
		steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
		if (steps.isEmpty()) {
			throw new IllegalArgumentException("An outcome has at least one step");
		}
		Objects.requireNonNull(metadata, "metadata");
	}

	/** Every step's effects, in step order. */
	public List<OutcomeEffect> effects() {
		return steps.stream().flatMap(step -> step.effects().stream()).toList();
	}

	/**
	 * Deterministic aggregation: any PLAYER_DOWN cancellation is INTERRUPTED; no resolved step is
	 * MECHANICS_UNAVAILABLE; every step resolved as SUCCESS is COMPLETE_SUCCESS; every resolved step
	 * a FAILURE is FAILURE; anything else is PARTIAL_SUCCESS.
	 */
	public static OverallResult aggregate(List<StepOutcome> steps) {
		if (steps.stream().anyMatch(s -> s.cancellation().filter(r -> r == CancellationReason.PLAYER_DOWN).isPresent())) {
			return OverallResult.INTERRUPTED;
		}
		List<StepOutcome> resolved = steps.stream().filter(s -> s.status() == StepStatus.RESOLVED).toList();
		if (resolved.isEmpty()) {
			return OverallResult.MECHANICS_UNAVAILABLE;
		}
		if (resolved.size() == steps.size() && resolved.stream().allMatch(StepOutcome::succeeded)) {
			return OverallResult.COMPLETE_SUCCESS;
		}
		if (resolved.stream().allMatch(s -> s.success().orElseThrow() == StepSuccess.FAILURE)) {
			return OverallResult.FAILURE;
		}
		return OverallResult.PARTIAL_SUCCESS;
	}
}
