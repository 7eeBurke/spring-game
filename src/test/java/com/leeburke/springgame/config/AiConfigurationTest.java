package com.leeburke.springgame.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.DisabledAiProvider;
import com.leeburke.springgame.ai.openai.OpenAiProvider;

/** AI is optional: every incomplete configuration gives a disabled provider, never a startup failure. */
class AiConfigurationTest {

	private static final AiProperties.Role NO_OVERRIDE = new AiProperties.Role(null, null, null);
	private static final AiProperties.Roles DEFAULT_ROLES = new AiProperties.Roles(NO_OVERRIDE, NO_OVERRIDE, NO_OVERRIDE, NO_OVERRIDE);

	private static AiProperties properties(boolean enabled, String model, String key) {
		return new AiProperties(enabled, model, Duration.ofSeconds(20), 1, new AiProperties.OpenAi(key, ""), DEFAULT_ROLES, 500);
	}

	@Test
	void disabledByDefault() {
		assertThat(AiConfiguration.provider(properties(false, "m", "k")))
				.isInstanceOfSatisfying(DisabledAiProvider.class, p -> assertThat(p.reason()).isEqualTo(AiFailureKind.DISABLED));
	}

	@Test
	void enabledWithoutKeyOrModelIsNotConfigured() {
		assertThat(AiConfiguration.provider(properties(true, "m", "")))
				.isInstanceOfSatisfying(DisabledAiProvider.class, p -> assertThat(p.reason()).isEqualTo(AiFailureKind.NOT_CONFIGURED));
		assertThat(AiConfiguration.provider(properties(true, " ", "k")))
				.isInstanceOfSatisfying(DisabledAiProvider.class, p -> assertThat(p.reason()).isEqualTo(AiFailureKind.NOT_CONFIGURED));
	}

	@Test
	void enabledAndConfiguredBuildsTheOpenAiAdapterWithoutCallingIt() {
		try (OpenAiProvider provider = (OpenAiProvider) AiConfiguration.provider(properties(true, "m", "sk-test"))) {
			assertThat(provider).isNotNull();
		}
	}

	@Test
	void roleSettingsUseDefaultsAndOverrides() {
		AiProperties.Roles roles = new AiProperties.Roles(new AiProperties.Role("small-model", 900, 0.0), NO_OVERRIDE, NO_OVERRIDE,
				new AiProperties.Role(null, null, 0.9));
		AiProperties properties = new AiProperties(true, "main-model", Duration.ofSeconds(9), 1, new AiProperties.OpenAi("k", ""),
				roles, 500);

		var settings = AiConfiguration.roleSettings(properties);
		assertThat(settings.forRole(AiRole.ACTION_INTERPRETER))
				.isEqualTo(new AiGenerationSettings("small-model", 900, Duration.ofSeconds(9), Optional.of(0.0)));
		assertThat(settings.forRole(AiRole.OUTCOME_NARRATOR))
				.isEqualTo(new AiGenerationSettings("main-model", 400, Duration.ofSeconds(9), Optional.empty()));
		assertThat(settings.forRole(AiRole.ENEMY_ATTACK_NARRATOR).maxOutputTokens()).isEqualTo(200);
		assertThat(settings.forRole(AiRole.CHARACTER_INTRODUCTION))
				.isEqualTo(new AiGenerationSettings("main-model", 600, Duration.ofSeconds(9), Optional.of(0.9)));
	}

	@Test
	void keyIsNeverPrinted() {
		assertThat(properties(true, "m", "sk-very-secret").toString()).doesNotContain("sk-very-secret").contains("<set>");
		assertThat(properties(true, "m", "").toString()).contains("<unset>");
	}
}
