package com.leeburke.springgame.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.interpreter.ActionInterpretationResult;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;

/**
 * Manual, opt-in check against the real OpenAI API. Excluded from the normal build by its tag; it
 * needs OPENAI_API_KEY and GAME_AI_MODEL. Run with:
 * {@code .\mvnw.cmd test -Dgroups=ai-smoke -DexcludedGroups=none -Dtest=OpenAiSmokeTest}
 */
@Tag("ai-smoke")
class OpenAiSmokeTest {

	@Test
	void realModelInterpretsASimpleAttack() {
		String key = System.getenv("OPENAI_API_KEY");
		String model = System.getenv("GAME_AI_MODEL");
		assumeTrue(key != null && !key.isBlank() && model != null && !model.isBlank(), "OPENAI_API_KEY and GAME_AI_MODEL are required");

		try (OpenAiProvider provider = OpenAiProvider.create(key, Optional.empty(), 1, Duration.ofSeconds(60))) {
			PromptLibrary prompts = new PromptLibrary();
			ActionInterpreter interpreter = new ActionInterpreter(provider, prompts.instructions(AiRole.ACTION_INTERPRETER),
					prompts.version(AiRole.ACTION_INTERPRETER), new AiGenerationSettings(model, 4000, Duration.ofSeconds(60),
							Optional.empty()));
			ActionInterpretationResult result = interpreter.interpret("I slash at the acolyte with my sword",
					InterpreterFixtures.setup());
			assertThat(result).isInstanceOf(ActionInterpretationResult.Interpreted.class);
		}
	}
}
