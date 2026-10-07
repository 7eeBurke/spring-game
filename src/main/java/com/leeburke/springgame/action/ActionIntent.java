package com.leeburke.springgame.action;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What the player attempts, as the action interpreter expresses it. Intent only: it never contains
 * stats, DCs, suitability, rolls, damage, outcomes, hidden facts or backend identities (run, scene,
 * revision or database IDs).
 * <p>
 * Structure is enforced here: at least one step, unique step IDs, 1-based contiguous sequences in
 * list order, START on the first step only, and unresolved references pointing at real steps.
 * Whether the schema version is supported, and whether references are valid in the current
 * context, are validation results, not construction rules.
 *
 * @param responseToAttack the single incoming attack this intent responds to, if any
 */
public record ActionIntent(
		int schemaVersion,
		Optional<String> responseToAttack,
		List<ActionStep> steps,
		InterpretationConfidence confidence,
		List<UnresolvedReference> unresolvedReferences) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public ActionIntent {
		if (schemaVersion < 1) {
			throw new IllegalArgumentException("Schema version must be at least 1, but was " + schemaVersion);
		}
		Objects.requireNonNull(responseToAttack, "responseToAttack");
		responseToAttack.ifPresent(ref -> Refs.require(ref, "Incoming attack reference"));
		steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
		Objects.requireNonNull(confidence, "confidence");
		unresolvedReferences = List.copyOf(Objects.requireNonNull(unresolvedReferences, "unresolvedReferences"));

		if (steps.isEmpty()) {
			throw new IllegalArgumentException("An intent needs at least one step");
		}
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < steps.size(); i++) {
			ActionStep step = steps.get(i);
			if (!ids.add(step.id())) {
				throw new IllegalArgumentException("Duplicate step id " + step.id());
			}
			if (step.sequence() != i + 1) {
				throw new IllegalArgumentException("Step " + step.id() + " has sequence " + step.sequence()
						+ " but is at position " + (i + 1));
			}
			boolean first = i == 0;
			if (first != (step.relation() == StepRelation.START)) {
				throw new IllegalArgumentException(first
						? "The first step must use START"
						: "Only the first step may use START, but step " + step.id() + " does");
			}
		}
		for (UnresolvedReference unresolved : unresolvedReferences) {
			unresolved.stepId().ifPresent(id -> {
				if (!ids.contains(id)) {
					throw new IllegalArgumentException("Unresolved reference names unknown step " + id);
				}
			});
		}
	}
}
