package com.leeburke.springgame.ai.interpreter;

import static com.leeburke.springgame.ai.interpreter.ActionDocumentTest.attack;
import static com.leeburke.springgame.ai.interpreter.ActionDocumentTest.document;
import static com.leeburke.springgame.ai.interpreter.ActionDocumentTest.target;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.FakeAiProvider;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failed;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Failure;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.NotSupported;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Source;

class ActionInterpreterTest {

	static final String VALID = document(attack(target("ENTITY", "entity_1", null)));
	/** Parses and maps, but Stage 10 rejects it: an attack cannot target a zone. */
	static final String STAGE_TEN_INVALID = document(attack(target("ZONE", "zone_1", null)));

	private final InterpretationSetup setup = InterpreterFixtures.setup();

	private static ActionInterpreter interpreter(FakeAiProvider provider) {
		return new ActionInterpreter(provider, "INSTRUCTIONS", 1, FakeAiProvider.settings());
	}

	@Test
	void validFirstAnswerIsInterpretedAndValidated() {
		FakeAiProvider provider = FakeAiProvider.answering(VALID);
		ActionInterpretationResult result = interpreter(provider).interpret("I slash at the acolyte", setup);

		assertThat(result).isInstanceOfSatisfying(Interpreted.class, interpreted -> {
			assertThat(interpreted.source()).isEqualTo(Source.AI);
			assertThat(interpreted.validated().intent().steps()).hasSize(1);
			assertThat(interpreted.validated().intent().confidence()).isEqualTo(InterpretationConfidence.HIGH);
		});
		assertThat(provider.structuredRequests()).singleElement().satisfies(request -> {
			assertThat(request.role()).isEqualTo(AiRole.ACTION_INTERPRETER);
			assertThat(request.instructions()).isEqualTo("INSTRUCTIONS");
			assertThat(request.schemaName()).isEqualTo(ActionDocumentSchema.NAME);
			assertThat(request.schemaJson()).isEqualTo(ActionDocumentSchema.json());
		});
	}

	@Test
	void confidenceFromTheModelIsPreserved() {
		ActionInterpretationResult result = interpreter(FakeAiProvider.answering(VALID.replace("\"HIGH\"", "\"LOW\"")))
				.interpret("maybe hit it?", setup);
		assertThat(((Interpreted) result).validated().intent().confidence()).isEqualTo(InterpretationConfidence.LOW);
	}

	@Test
	void malformedAnswerIsRepairedOnce() {
		FakeAiProvider provider = FakeAiProvider.answering("{\"schemaVersion\":1}", VALID);
		ActionInterpretationResult result = interpreter(provider).interpret("I slash at the acolyte", setup);

		assertThat(result).isInstanceOfSatisfying(Interpreted.class, i -> assertThat(i.source()).isEqualTo(Source.AI_REPAIRED));
		String repair = provider.structuredRequests().get(1).inputJson();
		assertThat(repair).contains("\"previousOutput\":\"{\\\"schemaVersion\\\":1}\"", "did not match the required document structure");
	}

	@Test
	void stageTenInvalidAnswerIsRepairedWithSafeFeedback() {
		FakeAiProvider provider = FakeAiProvider.answering(STAGE_TEN_INVALID, VALID);
		ActionInterpretationResult result = interpreter(provider).interpret("I slash at the aisle", setup);

		assertThat(result).isInstanceOf(Interpreted.class);
		String repair = provider.structuredRequests().get(1).inputJson();
		assertThat(repair).contains("SCHEMA_INVALID");
		assertThat(repair.substring(repair.indexOf("\"problems\""))).doesNotContain("aisle", "acolyte_1", "pew_1");
	}

	/** The reported smoke-test failure: a named target marked UNSPECIFIED. */
	static final String UNSPECIFIED_TARGET = document(attack(target("ENTITY", "entity_1", null, "UNSPECIFIED")));

	@Test
	void unspecifiedNamedTargetIsRepairedWithPreciseGuidance() {
		FakeAiProvider provider = FakeAiProvider.answering(UNSPECIFIED_TARGET, VALID);
		ActionInterpretationResult result = interpreter(provider).interpret("I slash at the acolyte with my sword", setup);

		assertThat(result).isInstanceOfSatisfying(Interpreted.class, i -> {
			assertThat(i.source()).isEqualTo(Source.AI_REPAIRED);
			assertThat(i.validated().intent().steps().getFirst().payload()).isInstanceOfSatisfying(
					com.leeburke.springgame.action.ActionPayload.AttackPayload.class,
					attack -> assertThat(attack.target().specificity())
							.isEqualTo(com.leeburke.springgame.action.TargetSpecificity.EXPLICIT));
		});
		String repair = provider.structuredRequests().get(1).inputJson();
		String problems = repair.substring(repair.indexOf("\"problems\""));
		assertThat(problems).contains("steps[0].attack.target", "EXPLICIT (the player named it)",
				"INFERRED (you identified it from context)", "UNSPECIFIED is only for kind NONE");
		assertThat(problems).doesNotContain("acolyte_1", "com.leeburke", "Cannot construct");
	}

	@Test
	void repeatedUnspecifiedNamedTargetFailsWithTheSameGuidance() {
		FakeAiProvider provider = FakeAiProvider.answering(UNSPECIFIED_TARGET, UNSPECIFIED_TARGET);
		ActionInterpretationResult result = interpreter(provider).interpret("I slash at the acolyte with my sword", setup);

		assertThat(result).isInstanceOfSatisfying(Failed.class, failed -> {
			assertThat(failed.reason()).isEqualTo(Failure.INVALID_OUTPUT);
			assertThat(failed.details()).singleElement().asString().contains("steps[0].attack.target",
					"UNSPECIFIED is only for kind NONE");
		});
		assertThat(provider.calls()).isEqualTo(2);
	}

	@Test
	void promptAndSchemaExplainTargetSpecificity() {
		String prompt = new com.leeburke.springgame.ai.PromptLibrary().instructions(AiRole.ACTION_INTERPRETER);
		assertThat(prompt).contains("UNSPECIFIED is only ever used with kind NONE",
				"{\"kind\": \"ENTITY\", \"alias\": \"entity_1\", \"bodyPart\": null, \"specificity\": \"EXPLICIT\"}",
				"correct exactly the listed problems");
		assertThat(ActionDocumentSchema.json()).contains("UNSPECIFIED is only for kind NONE",
				"Every kind except NONE must be EXPLICIT or INFERRED");
	}

	@Test
	void repairReusesTheSameContext() {
		FakeAiProvider provider = FakeAiProvider.answering("nonsense", VALID);
		interpreter(provider).interpret("hit it", setup);
		String first = provider.structuredRequests().get(0).inputJson();
		String second = provider.structuredRequests().get(1).inputJson();
		String context = first.substring(first.indexOf("\"context\""), first.length() - 1);
		assertThat(second).contains(context);
	}

	@Test
	void twoInvalidAnswersFailWithoutGuessing() {
		FakeAiProvider provider = FakeAiProvider.answering("nonsense", STAGE_TEN_INVALID);
		ActionInterpretationResult result = interpreter(provider).interpret("hit it", setup);

		assertThat(result).isInstanceOfSatisfying(Failed.class, failed -> {
			assertThat(failed.reason()).isEqualTo(Failure.INVALID_OUTPUT);
			assertThat(failed.details()).isNotEmpty();
		});
		assertThat(provider.calls()).isEqualTo(2);
	}

	@Test
	void providerFailureIsReportedWithACommandHint() {
		FakeAiProvider provider = new FakeAiProvider().thenFail(AiFailureKind.TIMEOUT);
		ActionInterpretationResult result = interpreter(provider).interpret("hit it", setup);

		assertThat(result).isEqualTo(new Failed(Failure.AI_UNAVAILABLE, Optional.of(AiFailureKind.TIMEOUT),
				List.of(ActionInterpreter.COMMAND_HINT)));
		assertThat(provider.calls()).isEqualTo(1);
	}

	@Test
	void providerFailureDuringRepairIsAlsoUnavailable() {
		FakeAiProvider provider = new FakeAiProvider().then("nonsense").thenFail(AiFailureKind.RATE_LIMITED);
		assertThat(interpreter(provider).interpret("hit it", setup)).isInstanceOfSatisfying(Failed.class,
				failed -> assertThat(failed.aiFailure()).contains(AiFailureKind.RATE_LIMITED));
	}

	@Test
	void disabledAiStillFailsCleanly() {
		ActionInterpreter interpreter = new ActionInterpreter(new com.leeburke.springgame.ai.DisabledAiProvider(AiFailureKind.DISABLED),
				"INSTRUCTIONS", 1, FakeAiProvider.settings());
		assertThat(interpreter.interpret("hit it", setup)).isInstanceOfSatisfying(Failed.class,
				failed -> assertThat(failed.reason()).isEqualTo(Failure.AI_UNAVAILABLE));
	}

	@Test
	void unsupportedRequestIsNotAFailure() {
		FakeAiProvider provider = FakeAiProvider.answering(
				"{\"schemaVersion\":1,\"supported\":false,\"responseToAttack\":null,\"confidence\":\"HIGH\",\"steps\":[],\"unresolved\":[]}");
		assertThat(interpreter(provider).interpret("save my game please", setup)).isEqualTo(new NotSupported());
	}

	@Test
	void slashCommandsNeverReachTheModel() {
		FakeAiProvider provider = new FakeAiProvider();
		ActionInterpretationResult result = interpreter(provider).interpret("  /attack entity_1 slash  ", setup);
		assertThat(result).isInstanceOfSatisfying(Interpreted.class, i -> assertThat(i.source()).isEqualTo(Source.COMMAND));
		assertThat(provider.calls()).isZero();
	}

	@Test
	void blankInputIsRejectedWithoutACall() {
		FakeAiProvider provider = new FakeAiProvider();
		assertThat(interpreter(provider).interpret("   ", setup)).isInstanceOfSatisfying(Failed.class,
				failed -> assertThat(failed.reason()).isEqualTo(Failure.EMPTY_INPUT));
		assertThat(provider.calls()).isZero();
	}
}
