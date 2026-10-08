package com.leeburke.springgame.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult.Interpreted;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * Manual, opt-in checks against the real OpenAI API. Excluded from the normal build by their tag;
 * they need OPENAI_API_KEY and GAME_AI_MODEL and are skipped without them. Run with:
 * {@code .\mvnw.cmd test -Dgroups=ai-smoke -DexcludedGroups=none -Dtest=OpenAiSmokeTest}
 * <p>
 * They only interpret: the result must pass Stage 10 validation, and nothing is resolved. In the
 * fixture situation the acolyte is {@code entity_1} (backend {@code acolyte_1}) and the player's
 * Longsword is {@code weapon_1}.
 */
@Tag("ai-smoke")
class OpenAiSmokeTest {

	@Test
	void realModelInterpretsASimpleAttack() {
		try (Session session = session()) {
			ActionInterpretationResult result = session.interpreter().interpret("I slash at the acolyte with my sword",
					InterpreterFixtures.setup());
			assertThat(result).isInstanceOf(Interpreted.class);
		}
	}

	@Test
	void realModelInterpretsACautiousThrustThenASlash() {
		try (Session session = session()) {
			ActionInterpretationResult result = session.interpreter().interpret(
					"I cautiously thrust my sword at the acolyte's chest, then slash at its left arm.", InterpreterFixtures.setup());

			assertThat(result).isInstanceOf(Interpreted.class);
			ActionIntent intent = ((Interpreted) result).validated().intent();
			List<ActionStep> steps = intent.steps();
			assertThat(steps).hasSize(2);
			assertThat(steps).extracting(ActionStep::relation).containsExactly(StepRelation.START, StepRelation.THEN);
			assertThat(steps).allSatisfy(step -> assertThat(step.payload()).isInstanceOf(AttackPayload.class));

			AttackPayload thrust = (AttackPayload) steps.get(0).payload();
			assertThat(thrust.method()).isEqualTo(WeaponMethod.THRUST);
			assertThat(thrust.approach()).isEqualTo(ActionApproach.CAUTIOUS);
			assertThat(thrust.weaponRef()).isEqualTo("weapon_1");
			assertAcolyteAt(thrust.target(), BodyPart.CHEST);

			AttackPayload slash = (AttackPayload) steps.get(1).payload();
			assertThat(slash.method()).isEqualTo(WeaponMethod.SLASH);
			assertThat(slash.weaponRef()).isEqualTo("weapon_1");
			assertAcolyteAt(slash.target(), BodyPart.LEFT_ARM);
		}
	}

	/** The target is the acolyte at the given part; specificity may be EXPLICIT or INFERRED ("its"). */
	private static void assertAcolyteAt(ActionTarget target, BodyPart part) {
		assertThat(target).isInstanceOfSatisfying(ActionTarget.EntityTarget.class, entity -> {
			assertThat(entity.entityId()).isEqualTo("acolyte_1");
			assertThat(entity.bodyPart()).contains(part);
		});
	}

	/** Skips the test unless a key and model are configured, then builds the real provider and interpreter. */
	private static Session session() {
		String key = System.getenv("OPENAI_API_KEY");
		String model = System.getenv("GAME_AI_MODEL");
		assumeTrue(key != null && !key.isBlank() && model != null && !model.isBlank(), "OPENAI_API_KEY and GAME_AI_MODEL are required");

		OpenAiProvider provider = OpenAiProvider.create(key, Optional.empty(), 1, Duration.ofSeconds(60));
		PromptLibrary prompts = new PromptLibrary();
		ActionInterpreter interpreter = new ActionInterpreter(provider, prompts.instructions(AiRole.ACTION_INTERPRETER),
				prompts.version(AiRole.ACTION_INTERPRETER),
				new AiGenerationSettings(model, 4000, Duration.ofSeconds(60), Optional.empty()));
		return new Session(provider, interpreter);
	}

	private record Session(OpenAiProvider provider, ActionInterpreter interpreter) implements AutoCloseable {
		@Override
		public void close() {
			provider.close();
		}
	}
}
