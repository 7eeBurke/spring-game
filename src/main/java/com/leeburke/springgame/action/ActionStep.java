package com.leeburke.springgame.action;

import java.util.Objects;

/**
 * One ordered step of an intent.
 *
 * @param sequence 1-based position in the intent
 * @param relation relation to the immediately preceding step only
 */
public record ActionStep(String id, int sequence, StepRelation relation, ActionPayload payload) {

	public ActionStep {
		Refs.require(id, "Step id");
		if (sequence < 1) {
			throw new IllegalArgumentException("Step sequence is 1-based, but was " + sequence);
		}
		Objects.requireNonNull(relation, "relation");
		Objects.requireNonNull(payload, "payload");
	}

	/** Derived from the payload; never stored separately. */
	public ActionType actionType() {
		return payload.type();
	}
}
