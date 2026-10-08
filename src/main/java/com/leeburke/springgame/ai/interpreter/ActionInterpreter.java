package com.leeburke.springgame.ai.interpreter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.validation.ActionValidationResult;
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.AiCallLog;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiJson;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiStructuredRequest;
import com.leeburke.springgame.ai.AiUsage;
import com.leeburke.springgame.ai.interpreter.ActionDocumentMapper.DocumentMappingException;
import com.leeburke.springgame.ai.interpreter.ActionDocumentParser.DocumentParseException;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failed;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failure;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.NotSupported;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Source;

/**
 * The Action Interpreter role. Input starting with {@code /} is a deterministic command and never
 * reaches a model. Other input goes to the model as structured output, then through strict
 * parsing, alias resolution and Stage 10 validation. One repair is allowed, carrying the same
 * context and only model-safe feedback; then the interpretation fails. Nothing is guessed, and the
 * model can never submit mechanics: the document has no field for them.
 */
public final class ActionInterpreter {

	static final String COMMAND_HINT = "Free-text actions need the AI interpreter; slash commands still work, for example /attack entity_1 slash.";

	private final AiProvider provider;
	private final String instructions;
	private final int promptVersion;
	private final AiGenerationSettings settings;
	private final ActionValidator validator = new ActionValidator();
	private final CommandInterpreter commands = new CommandInterpreter();
	private final String schema = ActionDocumentSchema.json();

	public ActionInterpreter(AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings) {
		this.provider = Objects.requireNonNull(provider, "provider");
		this.instructions = Objects.requireNonNull(instructions, "instructions");
		this.promptVersion = promptVersion;
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	public ActionInterpretationResult interpret(String playerInput, InterpretationSetup setup) {
		Objects.requireNonNull(playerInput, "playerInput");
		Objects.requireNonNull(setup, "setup");
		String input = playerInput.strip();
		if (input.isEmpty()) {
			return new Failed(Failure.EMPTY_INPUT, Optional.empty(), List.of());
		}
		if (input.startsWith("/")) {
			return commands.interpret(input, setup);
		}

		long start = System.nanoTime();
		AiResponse first = provider.generateStructured(request(AiJson.write(
				new InterpreterInput(ActionInterpretationContext.CURRENT_SCHEMA_VERSION, input, setup.context()))));
		if (first instanceof AiResponse.Failure failure) {
			return unavailable(failure.kind(), start, 1, Optional.empty());
		}
		AiResponse.Success firstSuccess = (AiResponse.Success) first;
		String firstOutput = firstSuccess.text();
		Optional<AiUsage> usage = firstSuccess.usage();
		Attempt attempt = evaluate(firstOutput, setup);
		if (attempt.result().isPresent()) {
			log(start, 1, "SUCCESS", usage);
			return attempt.result().get();
		}

		AiResponse second = provider.generateStructured(request(AiJson.write(new RepairInput(
				ActionInterpretationContext.CURRENT_SCHEMA_VERSION, input, setup.context(), firstOutput, attempt.problems()))));
		if (second instanceof AiResponse.Failure failure) {
			return unavailable(failure.kind(), start, 2, usage);
		}
		AiResponse.Success secondSuccess = (AiResponse.Success) second;
		usage = AiUsage.combine(usage, secondSuccess.usage());
		Attempt repaired = evaluate(secondSuccess.text(), setup);
		if (repaired.result().isPresent()) {
			log(start, 2, "REPAIRED", usage);
			return repaired.result().map(r -> r instanceof Interpreted i ? new Interpreted(i.validated(), Source.AI_REPAIRED) : r)
					.orElseThrow();
		}
		log(start, 2, "FAILED:INVALID_OUTPUT", usage);
		return new Failed(Failure.INVALID_OUTPUT, Optional.empty(), repaired.problems());
	}

	private Attempt evaluate(String output, InterpretationSetup setup) {
		ActionDocument document;
		try {
			document = ActionDocumentParser.parse(output);
		} catch (DocumentParseException e) {
			return Attempt.problems(List.of(RepairFeedback.parseProblem(e.getMessage())));
		}
		if (!document.supported()) {
			return Attempt.of(new NotSupported());
		}
		ActionIntent intent;
		try {
			intent = new ActionDocumentMapper(setup.aliases()).toIntent(document);
		} catch (DocumentMappingException e) {
			return Attempt.problems(e.problems());
		}
		Optional<ValidatedActionIntent> validated = validator.validated(intent, setup.validation());
		if (validated.isPresent()) {
			return Attempt.of(new Interpreted(validated.get(), Source.AI));
		}
		ActionValidationResult result = validator.validate(intent, setup.validation());
		return Attempt.problems(RepairFeedback.validationProblems(result.errors()));
	}

	private AiStructuredRequest request(String inputJson) {
		return new AiStructuredRequest(AiRole.ACTION_INTERPRETER, promptVersion, instructions, inputJson,
				ActionDocumentSchema.NAME, schema, settings);
	}

	private Failed unavailable(AiFailureKind kind, long start, int attempts, Optional<AiUsage> usage) {
		log(start, attempts, "FAILED:" + kind, usage);
		return new Failed(Failure.AI_UNAVAILABLE, Optional.of(kind), List.of(COMMAND_HINT));
	}

	/** Logs the call, with token usage summed over its attempts. */
	private void log(long start, int attempts, String outcome, Optional<AiUsage> usage) {
		AiCallLog.record(AiRole.ACTION_INTERPRETER, promptVersion, settings.model(),
				Duration.ofNanos(System.nanoTime() - start), attempts, outcome, usage);
	}

	/** The first request's user message. Player text is only ever this string value. */
	record InterpreterInput(int schemaVersion, String playerInput, ActionInterpretationContext context) {
	}

	/** The repair request's user message: the same context, the previous output and safe problems. */
	record RepairInput(int schemaVersion, String playerInput, ActionInterpretationContext context, String previousOutput,
			List<String> problems) {
	}

	private record Attempt(Optional<ActionInterpretationResult> result, List<String> problems) {
		static Attempt of(ActionInterpretationResult result) {
			return new Attempt(Optional.of(result), List.of());
		}

		static Attempt problems(List<String> problems) {
			return new Attempt(Optional.empty(), List.copyOf(problems));
		}
	}
}
