package com.leeburke.springgame.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class BudgetedAiProviderTest {

	private static final AiTextRequest TEXT = new AiTextRequest(AiRole.OUTCOME_NARRATOR, 1, "instructions", "{}",
			new AiGenerationSettings("m", 10, Duration.ofSeconds(1), Optional.empty()));

	private static final class MutableClock extends Clock {
		Instant now = Instant.parse("2026-10-08T23:59:00Z");

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}
	}

	@Test
	void callsPastTheDailyLimitFailAsRateLimitedWithoutReachingTheProvider() {
		FakeAiProvider fake = FakeAiProvider.answering("a", "b");
		BudgetedAiProvider budgeted = new BudgetedAiProvider(fake, 2, new MutableClock());

		assertThat(budgeted.generateText(TEXT)).isInstanceOf(AiResponse.Success.class);
		assertThat(budgeted.generateText(TEXT)).isInstanceOf(AiResponse.Success.class);
		AiResponse third = budgeted.generateText(TEXT);

		assertThat(third).isInstanceOfSatisfying(AiResponse.Failure.class,
				f -> assertThat(f.kind()).isEqualTo(AiFailureKind.RATE_LIMITED));
		assertThat(fake.calls()).isEqualTo(2);
		assertThat(budgeted.usedToday()).isEqualTo(2);
	}

	@Test
	void theBudgetResetsAtTheStartOfTheUtcDay() {
		MutableClock clock = new MutableClock();
		FakeAiProvider fake = FakeAiProvider.answering("a", "b");
		BudgetedAiProvider budgeted = new BudgetedAiProvider(fake, 1, clock);

		budgeted.generateText(TEXT);
		assertThat(budgeted.generateText(TEXT)).isInstanceOf(AiResponse.Failure.class);
		clock.now = clock.now.plus(Duration.ofMinutes(2));

		assertThat(budgeted.generateText(TEXT)).isInstanceOf(AiResponse.Success.class);
		assertThat(budgeted.usedToday()).isEqualTo(1);
	}

	@Test
	void aZeroLimitNeverCallsTheProvider() {
		FakeAiProvider fake = new FakeAiProvider();
		BudgetedAiProvider budgeted = new BudgetedAiProvider(fake, 0, new MutableClock());

		assertThat(budgeted.generateText(TEXT)).isInstanceOf(AiResponse.Failure.class);
		assertThat(fake.calls()).isZero();
	}
}
