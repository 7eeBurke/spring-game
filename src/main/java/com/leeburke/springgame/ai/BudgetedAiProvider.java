package com.leeburke.springgame.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Enforces a global daily cap on provider calls, counted per UTC day across all runs, so a publicly
 * reachable server cannot spend unbounded AI credit. Past the cap every call fails with
 * {@link AiFailureKind#RATE_LIMITED} without reaching the provider: the interpreter reports AI as
 * unavailable (slash commands still work) and narrators fall back. The count is kept in memory and
 * resets when the server restarts.
 */
public final class BudgetedAiProvider implements AiProvider, AutoCloseable {

	private final AiProvider delegate;
	private final long dailyLimit;
	private final Clock clock;
	private LocalDate day;
	private long used;

	public BudgetedAiProvider(AiProvider delegate, long dailyLimit, Clock clock) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		if (dailyLimit < 0) {
			throw new IllegalArgumentException("The daily AI call limit cannot be negative");
		}
		this.dailyLimit = dailyLimit;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public AiProvider delegate() {
		return delegate;
	}

	@Override
	public AiResponse generateStructured(AiStructuredRequest request) {
		return reserve() ? delegate.generateStructured(request) : AiResponse.Failure.of(AiFailureKind.RATE_LIMITED);
	}

	@Override
	public AiResponse generateText(AiTextRequest request) {
		return reserve() ? delegate.generateText(request) : AiResponse.Failure.of(AiFailureKind.RATE_LIMITED);
	}

	/** Calls made today. */
	public synchronized long usedToday() {
		roll();
		return used;
	}

	private synchronized boolean reserve() {
		roll();
		if (used >= dailyLimit) {
			return false;
		}
		used++;
		return true;
	}

	/** Closes the delegate if it holds resources (an HTTP client). */
	@Override
	public void close() throws Exception {
		if (delegate instanceof AutoCloseable closeable) {
			closeable.close();
		}
	}

	private void roll() {
		LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
		if (!today.equals(day)) {
			day = today;
			used = 0;
		}
	}
}
