package com.leeburke.springgame.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI settings ({@code game.ai.*}). AI is off by default, and nothing here is required for startup:
 * with AI off, or without a key or model, every role uses its deterministic fallback. There is no
 * default model ID. The API key comes from the environment and is never printed. {@code dailyCallLimit}
 * caps provider calls per UTC day across all runs.
 */
@ConfigurationProperties("game.ai")
public record AiProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("") String model,
		@DefaultValue("20s") Duration timeout,
		@DefaultValue("1") int maxRetries,
		@DefaultValue OpenAi openai,
		@DefaultValue Roles roles,
		@DefaultValue("500") long dailyCallLimit) {

	public record OpenAi(@DefaultValue("") String apiKey, @DefaultValue("") String baseUrl) {
		@Override
		public String toString() {
			return "OpenAi[apiKey=" + (apiKey.isBlank() ? "<unset>" : "<set>") + ", baseUrl=" + baseUrl + "]";
		}
	}

	/** Optional per-role overrides; a null value means the global or role default. */
	public record Role(String model, Integer maxOutputTokens, Double temperature) {
	}

	public record Roles(
			@DefaultValue Role actionInterpreter,
			@DefaultValue Role outcomeNarrator,
			@DefaultValue Role enemyAttackNarrator,
			@DefaultValue Role characterIntroduction) {
	}
}
