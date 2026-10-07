package com.leeburke.springgame.action.validation;

import java.util.List;
import java.util.Objects;

/** The deterministic, ordered outcome of validating an intent. Valid when there are no errors. */
public record ActionValidationResult(List<ActionValidationError> errors) {

	public ActionValidationResult {
		errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
	}

	public static ActionValidationResult success() {
		return new ActionValidationResult(List.of());
	}

	public boolean valid() {
		return errors.isEmpty();
	}
}
