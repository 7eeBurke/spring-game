package com.leeburke.springgame.ai.interpreter;

import java.util.List;

import com.leeburke.springgame.action.validation.ActionValidationCode;
import com.leeburke.springgame.action.validation.ActionValidationError;

/**
 * Model-safe descriptions of why an interpretation was rejected. Stage 10 validation errors are
 * reported by code, step number and a fixed description; their raw messages contain backend
 * references the model never saw and are never forwarded.
 */
public final class RepairFeedback {

	private RepairFeedback() {
	}

	public static String parseProblem(String detail) {
		return "The output did not match the required document structure: " + detail;
	}

	public static List<String> validationProblems(List<ActionValidationError> errors) {
		return errors.stream().map(RepairFeedback::describe).toList();
	}

	static String describe(ActionValidationError error) {
		String where = error.stepId().map(id -> "step " + id.substring(1) + ": ").orElse("");
		return where + error.code() + " - " + description(error.code());
	}

	static String description(ActionValidationCode code) {
		return switch (code) {
			case UNSUPPORTED_SCHEMA_VERSION -> "use schemaVersion 1";
			case SCHEMA_INVALID -> "the action and its target or fields do not fit together";
			case UNRESOLVED_REFERENCE -> "a phrase could not be matched to anything in the context; the action cannot go ahead";
			case UNKNOWN_SCENE_REFERENCE -> "a target is not one of the scene aliases in the context";
			case UNKNOWN_PLAYER_REFERENCE -> "a weapon, item or ability is not one the player owns";
			case UNKNOWN_INCOMING_ATTACK -> "responseToAttack must be an incoming attack alias from the context";
			case ACTION_PHYSICALLY_IMPOSSIBLE -> "the action is physically impossible as described";
		};
	}
}
