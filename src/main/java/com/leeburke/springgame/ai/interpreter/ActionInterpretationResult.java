package com.leeburke.springgame.ai.interpreter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.AiFailureKind;

/**
 * The result of interpreting player input. Only {@link Interpreted} carries an intent, and it is
 * already validated by Stage 10, so Stage 11 can resolve it directly. Unsupported and failed
 * interpretations are not mechanical failures: nothing was attempted in the world.
 */
public sealed interface ActionInterpretationResult {

	enum Source {
		/** The model's first answer. */
		AI,
		/** The model's answer after one repair. */
		AI_REPAIRED,
		/** A deterministic slash command. */
		COMMAND
	}

	enum Failure {
		EMPTY_INPUT,
		/** No usable model was reachable; slash commands still work. */
		AI_UNAVAILABLE,
		/** The model answered twice without producing a valid action. */
		INVALID_OUTPUT,
		/** A slash command could not be parsed. */
		INVALID_COMMAND
	}

	/** A validated intent (confidence is the intent's own; it has no mechanical effect). */
	record Interpreted(ValidatedActionIntent validated, Source source) implements ActionInterpretationResult {
		public Interpreted {
			Objects.requireNonNull(validated, "validated");
			Objects.requireNonNull(source, "source");
		}
	}

	/** ACTION_NOT_SUPPORTED: the input asks for something outside the action vocabulary. */
	record NotSupported() implements ActionInterpretationResult {
	}

	/** INTERPRETATION_FAILED, with player-safe details. */
	record Failed(Failure reason, Optional<AiFailureKind> aiFailure, List<String> details) implements ActionInterpretationResult {
		public Failed {
			Objects.requireNonNull(reason, "reason");
			Objects.requireNonNull(aiFailure, "aiFailure");
			details = List.copyOf(Objects.requireNonNull(details, "details"));
		}
	}
}
