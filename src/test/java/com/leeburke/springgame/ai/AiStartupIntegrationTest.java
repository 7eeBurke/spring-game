package com.leeburke.springgame.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.leeburke.springgame.PostgresTestcontainersConfiguration;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;

/** The application starts with AI switched on but no key, and every role falls back. No network. */
@SpringBootTest(properties = { "game.ai.enabled=true", "game.ai.openai.api-key=", "game.ai.model=some-model" })
@Import(PostgresTestcontainersConfiguration.class)
class AiStartupIntegrationTest {

	@Autowired
	private AiProvider provider;

	@Autowired
	private ActionInterpreter interpreter;

	@Autowired
	private PromptLibrary prompts;

	@Autowired
	private AiRoleSettings settings;

	@Test
	void enabledWithoutKeyStartsWithTheDisabledProvider() {
		assertThat(provider).isInstanceOfSatisfying(DisabledAiProvider.class,
				p -> assertThat(p.reason()).isEqualTo(AiFailureKind.NOT_CONFIGURED));
		assertThat(interpreter.interpret("/hold", InterpreterFixtures.setup()))
				.isInstanceOf(ActionInterpretationResult.Interpreted.class);
		assertThat(interpreter.interpret("I wait", InterpreterFixtures.setup()))
				.isInstanceOf(ActionInterpretationResult.Failed.class);
		for (AiRole role : AiRole.values()) {
			assertThat(prompts.instructions(role)).isNotBlank();
			assertThat(prompts.version(role)).isEqualTo(switch (role) {
				case ACTION_INTERPRETER -> 7;
				case OUTCOME_NARRATOR -> 7;
				case CHARACTER_INTRODUCTION -> 2;
				case ENEMY_ATTACK_NARRATOR -> 1;
			});
		}
	}

	@Test
	void theNarratorHasItsOwnModelAndEffortAndEveryOtherRoleKeepsTheGlobalModel() {
		assertThat(settings.forRole(AiRole.OUTCOME_NARRATOR).model()).isEqualTo("gpt-5.4-mini");
		assertThat(settings.forRole(AiRole.OUTCOME_NARRATOR).reasoningEffort()).contains("none");
		for (AiRole role : List.of(AiRole.ACTION_INTERPRETER, AiRole.ENEMY_ATTACK_NARRATOR, AiRole.CHARACTER_INTRODUCTION)) {
			assertThat(settings.forRole(role).model()).as(role.name()).isEqualTo("some-model");
			assertThat(settings.forRole(role).reasoningEffort()).as(role.name()).isEmpty();
		}
	}
}
