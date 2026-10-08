package com.leeburke.springgame.game.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.leeburke.springgame.ai.AiFailureKind;
import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiStructuredRequest;
import com.leeburke.springgame.ai.AiTextRequest;
import com.leeburke.springgame.ai.DisabledAiProvider;

/**
 * Test doubles for the application layer: an AI provider each test can script (disabled by
 * default, so slash commands and fallbacks run with no network), a clock tests can move, and
 * reproducible run seeds. Never a live model.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GameTestConfiguration {

	@Bean
	@Primary
	SwitchableAi switchableAi() {
		return new SwitchableAi();
	}

	@Bean
	@Primary
	MovableClock movableClock() {
		return new MovableClock();
	}

	@Bean
	@Primary
	SecureRandom fixedRunSeeds() {
		return new SequentialSeeds();
	}

	/** Delegates to whichever provider the current test installed. */
	public static final class SwitchableAi implements AiProvider {
		private final AtomicReference<AiProvider> delegate = new AtomicReference<>(new DisabledAiProvider(AiFailureKind.DISABLED));

		public void use(AiProvider provider) {
			delegate.set(provider);
		}

		public void reset() {
			delegate.set(new DisabledAiProvider(AiFailureKind.DISABLED));
		}

		@Override
		public AiResponse generateStructured(AiStructuredRequest request) {
			return delegate.get().generateStructured(request);
		}

		@Override
		public AiResponse generateText(AiTextRequest request) {
			return delegate.get().generateText(request);
		}
	}

	/** A clock that only moves when a test says so. */
	public static final class MovableClock extends Clock {
		private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-08T12:00:00Z"));

		public void advance(Duration duration) {
			now.updateAndGet(t -> t.plus(duration));
		}

		@Override
		public Instant instant() {
			return now.get();
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	/** Run seeds 1, 2, 3, ... so failures reproduce. */
	static final class SequentialSeeds extends SecureRandom {
		private static final long serialVersionUID = 1L;
		private final AtomicLong next = new AtomicLong(1000);

		@Override
		public long nextLong() {
			return next.incrementAndGet();
		}
	}
}
