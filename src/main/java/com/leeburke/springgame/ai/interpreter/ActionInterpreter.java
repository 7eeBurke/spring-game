package com.leeburke.springgame.ai.interpreter;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.action.validation.ActionValidationCode;
import com.leeburke.springgame.action.validation.ActionValidationError;
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
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Unclear;

/**
 * The Action Interpreter role. Input starting with {@code /} is a deterministic command and never
 * reaches a model. Other input goes to the model as structured output, then through strict
 * parsing, alias resolution and Stage 10 validation. One repair is allowed for a fixable problem,
 * carrying the same context and only model-safe feedback; then the interpretation fails. An answer
 * whose only problem is that the model could not tell what a phrase means is {@link Unclear} and is
 * never repaired: a repair could only invite a guess. Nothing is guessed, and the model can never
 * submit mechanics: the document has no field for them.
 */
public final class ActionInterpreter {

	static final String COMMAND_HINT = "Free-text actions need the AI interpreter; slash commands still work, for example /attack entity_1 slash.";

	private final AiProvider provider;
	private final String instructions;
	private final int promptVersion;
	private final AiGenerationSettings settings;
	private final Consumer<InterpretationTrace> trace;
	private final ActionValidator validator = new ActionValidator();
	private final CommandInterpreter commands = new CommandInterpreter();
	private final String schema = ActionDocumentSchema.json();

	public ActionInterpreter(AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings) {
		this(provider, instructions, promptVersion, settings, t -> {
		});
	}

	/**
	 * @param trace receives each model interpretation's attempts and first-answer problems; for
	 *              diagnostic tests only (it sees model output, which is never logged)
	 */
	public ActionInterpreter(AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings,
			Consumer<InterpretationTrace> trace) {
		this.provider = Objects.requireNonNull(provider, "provider");
		this.instructions = Objects.requireNonNull(instructions, "instructions");
		this.promptVersion = promptVersion;
		this.settings = Objects.requireNonNull(settings, "settings");
		this.trace = Objects.requireNonNull(trace, "trace");
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
			ActionInterpretationResult result = attempt.result().get();
			log(start, 1, result instanceof Unclear ? "UNCLEAR" : "SUCCESS", usage, Optional.empty());
			trace.accept(new InterpretationTrace(1, firstOutput, List.of(), Optional.empty()));
			return result;
		}

		AiResponse second = provider.generateStructured(request(AiJson.write(new RepairInput(
				ActionInterpretationContext.CURRENT_SCHEMA_VERSION, input, setup.context(), firstOutput, attempt.problems()))));
		if (second instanceof AiResponse.Failure failure) {
			return unavailable(failure.kind(), start, 2, usage);
		}
		AiResponse.Success secondSuccess = (AiResponse.Success) second;
		usage = AiUsage.combine(usage, secondSuccess.usage());
		trace.accept(new InterpretationTrace(2, firstOutput, attempt.problems(), Optional.of(attempt.cause())));
		Attempt repaired = evaluate(secondSuccess.text(), setup);
		if (repaired.result().isPresent()) {
			ActionInterpretationResult result = repaired.result().get();
			log(start, 2, result instanceof Unclear ? "UNCLEAR" : "REPAIRED", usage, Optional.of(attempt.cause()));
			return result instanceof Interpreted i ? new Interpreted(i.validated(), Source.AI_REPAIRED) : result;
		}
		log(start, 2, "FAILED:INVALID_OUTPUT", usage, Optional.of(attempt.cause()));
		return new Failed(Failure.INVALID_OUTPUT, Optional.empty(), repaired.problems());
	}

	private Attempt evaluate(String output, InterpretationSetup setup) {
		ActionDocument document;
		try {
			document = ActionDocumentParser.parse(output);
		} catch (DocumentParseException e) {
			return Attempt.problems(List.of(RepairFeedback.parseProblem(e.getMessage())), parseCause(e.getMessage()));
		}
		if (!document.supported()) {
			return Attempt.of(new NotSupported());
		}
		if (document.steps().isEmpty()) {
			// Only possible with unresolved phrases: the model could not tell what the player meant.
			return Attempt.of(new Unclear(Optional.empty(), document.unresolved().stream().map(ActionDocument.Unresolved::phrase).toList()));
		}
		ActionIntent intent;
		try {
			intent = new ActionDocumentMapper(setup.aliases()).toIntent(document);
		} catch (DocumentMappingException e) {
			return Attempt.problems(e.problems(), "MAPPING");
		}
		Optional<ValidatedActionIntent> validated = validator.validated(intent, setup.validation());
		if (validated.isPresent()) {
			return Attempt.of(new Interpreted(validated.get(), Source.AI));
		}
		ActionValidationResult result = validator.validate(intent, setup.validation());
		boolean onlyUnclear = result.errors().stream().allMatch(e -> e.code() == ActionValidationCode.UNRESOLVED_REFERENCE);
		if (onlyUnclear) {
			return Attempt.of(new Unclear(Optional.of(intent),
					intent.unresolvedReferences().stream().map(UnresolvedReference::phrase).toList()));
		}
		List<ActionValidationError> fixable = result.errors().stream()
				.filter(e -> e.code() != ActionValidationCode.UNRESOLVED_REFERENCE).toList();
		return Attempt.problems(RepairFeedback.validationProblems(result.errors()), "VALIDATION:" + fixable.stream()
				.map(e -> e.code().name()).distinct().sorted().collect(Collectors.joining(",")));
	}

	/**
	 * {@code PARSE} plus where: the document field path the parser reported ("steps[0].move: ..."),
	 * or, for a rule on the document itself, the top-level field the rule names ("schemaVersion must
	 * be 1"). Only field names of the document can appear, never free text.
	 */
	static String parseCause(String detail) {
		int colon = detail.indexOf(':');
		String path = colon > 0 ? detail.substring(0, colon) : "";
		if (path.matches("[A-Za-z0-9_.\\[\\]]+")) {
			return "PARSE:" + path;
		}
		String firstWord = detail.split("\\s+", 2)[0];
		return DOCUMENT_FIELDS.contains(firstWord) ? "PARSE:" + firstWord : "PARSE";
	}

	private static final java.util.Set<String> DOCUMENT_FIELDS = java.util.Arrays.stream(ActionDocument.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toUnmodifiableSet());

	private AiStructuredRequest request(String inputJson) {
		return new AiStructuredRequest(AiRole.ACTION_INTERPRETER, promptVersion, instructions, inputJson,
				ActionDocumentSchema.NAME, schema, settings);
	}

	private Failed unavailable(AiFailureKind kind, long start, int attempts, Optional<AiUsage> usage) {
		log(start, attempts, "FAILED:" + kind, usage, Optional.empty());
		return new Failed(Failure.AI_UNAVAILABLE, Optional.of(kind), List.of(COMMAND_HINT));
	}

	/** Logs the call, with token usage summed over its attempts and, after a repair, why it was needed. */
	private void log(long start, int attempts, String outcome, Optional<AiUsage> usage, Optional<String> repairCause) {
		AiCallLog.record(AiRole.ACTION_INTERPRETER, promptVersion, settings.model(),
				Duration.ofNanos(System.nanoTime() - start), attempts, outcome, usage, repairCause);
	}

	/**
	 * What one model interpretation went through, for diagnostic tests.
	 *
	 * @param firstOutput   the model's first answer, verbatim
	 * @param firstProblems the model-safe problems that sent it back for repair (empty if accepted)
	 * @param repairCause   the problem category and codes, as logged
	 */
	public record InterpretationTrace(int attempts, String firstOutput, List<String> firstProblems, Optional<String> repairCause) {
	}

	/**
	 * The first request's user message. Player text is only ever this string value. The context's
	 * version is named {@code contextVersion} so it is never mistaken for the answer document's own
	 * {@code schemaVersion}.
	 */
	record InterpreterInput(int contextVersion, String playerInput, ActionInterpretationContext context) {
	}

	/** The repair request's user message: the same context, the previous output and safe problems. */
	record RepairInput(int contextVersion, String playerInput, ActionInterpretationContext context, String previousOutput,
			List<String> problems) {
	}

	private record Attempt(Optional<ActionInterpretationResult> result, List<String> problems, String cause) {
		static Attempt of(ActionInterpretationResult result) {
			return new Attempt(Optional.of(result), List.of(), "");
		}

		static Attempt problems(List<String> problems, String cause) {
			return new Attempt(Optional.empty(), List.copyOf(problems), cause);
		}
	}
}
