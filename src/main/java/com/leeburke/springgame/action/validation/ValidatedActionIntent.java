package com.leeburke.springgame.action.validation;

import java.util.Objects;

import com.leeburke.springgame.action.ActionIntent;

/**
 * Proof that an {@link ActionIntent} passed validation against a specific
 * {@link ActionValidationContext}. Only {@link ActionValidator#validated} can create one, so an
 * unvalidated intent cannot reach action resolution.
 */
public final class ValidatedActionIntent {

	private final ActionIntent intent;
	private final ActionValidationContext context;

	ValidatedActionIntent(ActionIntent intent, ActionValidationContext context) {
		this.intent = Objects.requireNonNull(intent, "intent");
		this.context = Objects.requireNonNull(context, "context");
	}

	public ActionIntent intent() {
		return intent;
	}

	/** The player-safe context the intent was validated against. */
	public ActionValidationContext context() {
		return context;
	}
}
