package com.leeburke.springgame.config;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiRoleSettings;
import com.leeburke.springgame.ai.BudgetedAiProvider;
import com.leeburke.springgame.ai.DisabledAiProvider;
import com.leeburke.springgame.ai.PromptLibrary;
import com.leeburke.springgame.ai.interpreter.ActionInterpreter;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.narration.CharacterIntroductionNarrator;
import com.leeburke.springgame.ai.narration.EnemyAttackNarrator;
import com.leeburke.springgame.ai.narration.LoreCatalog;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContextBuilder;
import com.leeburke.springgame.ai.narration.OutcomeNarrator;
import com.leeburke.springgame.ai.openai.OpenAiProvider;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.world.WorldContentCatalog;

/**
 * Wires the AI boundary. The four roles and their context builders are plain Java; only the
 * provider choice happens here. Startup never depends on AI: when AI is off, or the key or model
 * is missing, the provider is a {@link DisabledAiProvider} and every role falls back.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

	private static final Logger LOG = LoggerFactory.getLogger(AiConfiguration.class);

	static final Map<AiRole, Integer> DEFAULT_MAX_OUTPUT_TOKENS = Map.of(
			AiRole.ACTION_INTERPRETER, 1500,
			AiRole.OUTCOME_NARRATOR, 400,
			AiRole.ENEMY_ATTACK_NARRATOR, 200,
			AiRole.CHARACTER_INTRODUCTION, 600);

	/**
	 * The configured provider, wrapped in the global daily budget when it is a real one. Spring
	 * closes it (and so the HTTP client) on shutdown.
	 */
	@Bean
	AiProvider aiProvider(AiProperties properties, Clock clock) {
		AiProvider provider = provider(properties);
		return provider instanceof DisabledAiProvider ? provider : new BudgetedAiProvider(provider, properties.dailyCallLimit(), clock);
	}

	static AiProvider provider(AiProperties properties) {
		if (!properties.enabled()) {
			LOG.info("AI is disabled; all AI roles use their deterministic fallbacks");
			return new DisabledAiProvider(AiFailureKind.DISABLED);
		}
		if (properties.openai().apiKey().isBlank()) {
			LOG.warn("AI is enabled but OPENAI_API_KEY is not set; AI roles use their deterministic fallbacks");
			return new DisabledAiProvider(AiFailureKind.NOT_CONFIGURED);
		}
		if (properties.model().isBlank()) {
			LOG.warn("AI is enabled but GAME_AI_MODEL is not set; AI roles use their deterministic fallbacks");
			return new DisabledAiProvider(AiFailureKind.NOT_CONFIGURED);
		}
		Optional<String> baseUrl = Optional.of(properties.openai().baseUrl()).filter(url -> !url.isBlank());
		return OpenAiProvider.create(properties.openai().apiKey(), baseUrl, properties.maxRetries(), properties.timeout());
	}

	@Bean
	AiRoleSettings aiRoleSettings(AiProperties properties) {
		return roleSettings(properties);
	}

	static AiRoleSettings roleSettings(AiProperties properties) {
		Map<AiRole, AiGenerationSettings> settings = new EnumMap<>(AiRole.class);
		for (AiRole role : AiRole.values()) {
			AiProperties.Role overrides = switch (role) {
				case ACTION_INTERPRETER -> properties.roles().actionInterpreter();
				case OUTCOME_NARRATOR -> properties.roles().outcomeNarrator();
				case ENEMY_ATTACK_NARRATOR -> properties.roles().enemyAttackNarrator();
				case CHARACTER_INTRODUCTION -> properties.roles().characterIntroduction();
			};
			String model = overrides.model() != null && !overrides.model().isBlank() ? overrides.model() : properties.model();
			int maxTokens = overrides.maxOutputTokens() != null ? overrides.maxOutputTokens() : DEFAULT_MAX_OUTPUT_TOKENS.get(role);
			settings.put(role, new AiGenerationSettings(model, maxTokens, properties.timeout(),
					Optional.ofNullable(overrides.temperature())));
		}
		return new AiRoleSettings(settings);
	}

	@Bean
	PromptLibrary promptLibrary() {
		return new PromptLibrary();
	}

	@Bean
	LoreCatalog loreCatalog() {
		return LoreCatalog.loadBundled();
	}

	@Bean
	InterpretationContextBuilder interpretationContextBuilder(WorldContentCatalog world) {
		return new InterpretationContextBuilder(world);
	}

	@Bean
	OutcomeNarrationContextBuilder outcomeNarrationContextBuilder(GameContentCatalog content, WorldContentCatalog world) {
		return new OutcomeNarrationContextBuilder(content, world);
	}

	@Bean
	ActionInterpreter actionInterpreter(AiProvider provider, PromptLibrary prompts, AiRoleSettings settings) {
		AiRole role = AiRole.ACTION_INTERPRETER;
		return new ActionInterpreter(provider, prompts.instructions(role), prompts.version(role), settings.forRole(role));
	}

	@Bean
	OutcomeNarrator outcomeNarrator(AiProvider provider, PromptLibrary prompts, AiRoleSettings settings) {
		AiRole role = AiRole.OUTCOME_NARRATOR;
		return new OutcomeNarrator(provider, prompts.instructions(role), prompts.version(role), settings.forRole(role));
	}

	@Bean
	EnemyAttackNarrator enemyAttackNarrator(AiProvider provider, PromptLibrary prompts, AiRoleSettings settings) {
		AiRole role = AiRole.ENEMY_ATTACK_NARRATOR;
		return new EnemyAttackNarrator(provider, prompts.instructions(role), prompts.version(role), settings.forRole(role));
	}

	@Bean
	CharacterIntroductionNarrator characterIntroductionNarrator(AiProvider provider, PromptLibrary prompts,
			AiRoleSettings settings) {
		AiRole role = AiRole.CHARACTER_INTRODUCTION;
		return new CharacterIntroductionNarrator(provider, prompts.instructions(role), prompts.version(role),
				settings.forRole(role));
	}
}
