package com.leeburke.springgame.ai.narration;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.leeburke.springgame.ai.AiCallLog;
import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiGenerationSettings;
import com.leeburke.springgame.ai.AiJson;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiTextRequest;
import com.leeburke.springgame.ai.AiUsage;

/**
 * The shared shape of a narrator call: one text generation, a role-specific acceptance check, and
 * the deterministic fallback on any failure or rejected output. No retries beyond the provider's
 * own transport retries.
 */
final class TextRole {

	private final AiRole role;
	private final AiProvider provider;
	private final String instructions;
	private final int promptVersion;
	private final AiGenerationSettings settings;

	TextRole(AiRole role, AiProvider provider, String instructions, int promptVersion, AiGenerationSettings settings) {
		this.role = Objects.requireNonNull(role, "role");
		this.provider = Objects.requireNonNull(provider, "provider");
		this.instructions = Objects.requireNonNull(instructions, "instructions");
		this.promptVersion = promptVersion;
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	int promptVersion() {
		return promptVersion;
	}

	Narration narrate(Object input, Predicate<String> acceptable, Supplier<String> fallback) {
		long start = System.nanoTime();
		AiResponse response = provider.generateText(
				new AiTextRequest(role, promptVersion, instructions, AiJson.write(input), settings));
		Optional<AiFailureKind> failure;
		if (response instanceof AiResponse.Success success) {
			String text = success.text().strip();
			if (!text.isEmpty() && acceptable.test(text)) {
				log(start, "SUCCESS", success.usage());
				return new Narration(text, NarrationSource.AI, promptVersion, Optional.empty());
			}
			failure = Optional.of(AiFailureKind.MALFORMED_RESPONSE);
			log(start, "FALLBACK:" + failure.get(), success.usage());
			return new Narration(fallback.get(), NarrationSource.FALLBACK, promptVersion, failure);
		} else {
			failure = Optional.of(((AiResponse.Failure) response).kind());
		}
		log(start, "FALLBACK:" + failure.get(), Optional.empty());
		return new Narration(fallback.get(), NarrationSource.FALLBACK, promptVersion, failure);
	}

	private void log(long start, String outcome, Optional<AiUsage> usage) {
		AiCallLog.record(role, promptVersion, settings.model(), Duration.ofNanos(System.nanoTime() - start), 1, outcome, usage);
	}

	static Predicate<String> maxLength(int max) {
		return text -> text.length() <= max;
	}
}
