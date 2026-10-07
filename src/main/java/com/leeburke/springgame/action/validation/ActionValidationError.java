package com.leeburke.springgame.action.validation;

import java.util.Objects;
import java.util.Optional;

/**
 * One validation problem. The message names only what the intent itself supplied and never
 * describes hidden state.
 */
public record ActionValidationError(ActionValidationCode code, Optional<String> stepId, String message) {

	public ActionValidationError {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(stepId, "stepId");
		Objects.requireNonNull(message, "message");
	}
}
