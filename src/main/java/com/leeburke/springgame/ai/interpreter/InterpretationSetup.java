package com.leeburke.springgame.ai.interpreter;

import java.util.Objects;

import com.leeburke.springgame.action.validation.ActionValidationContext;

/**
 * One interpretation request's three views of the same situation: the AI-facing context, the
 * backend-only alias table that maps it back, and the Stage 10 validation context.
 */
public record InterpretationSetup(ActionInterpretationContext context, AliasTable aliases,
		ActionValidationContext validation) {

	public InterpretationSetup {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(aliases, "aliases");
		Objects.requireNonNull(validation, "validation");
	}
}
