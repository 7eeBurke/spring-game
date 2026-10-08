package com.leeburke.springgame.game.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.leeburke.springgame.ai.AiProvider;
import com.leeburke.springgame.ai.AiResponse;
import com.leeburke.springgame.ai.AiRole;
import com.leeburke.springgame.ai.AiStructuredRequest;
import com.leeburke.springgame.ai.AiTextRequest;

/**
 * A scripted provider for orchestration tests: counts calls per role, fails the test if a model is
 * called while a database transaction is open, and answers text roles with fixed prose (or
 * crashes, to simulate a process dying after mechanics commit). Structured answers are queued.
 */
final class CountingAi implements AiProvider {

	static final String PROSE = "Steel rings in the dark.";

	private final Map<AiRole, AtomicInteger> calls = new ConcurrentHashMap<>();
	private final Deque<AiResponse> structured = new ArrayDeque<>();
	private volatile boolean crashText;
	private volatile Duration textDelay = Duration.ZERO;
	private volatile Runnable beforeStructured = () -> {
	};
	private volatile boolean transactionSeen;

	CountingAi crashingText() {
		crashText = true;
		return this;
	}

	CountingAi slowText(Duration delay) {
		textDelay = delay;
		return this;
	}

	CountingAi answerStructured(String json) {
		structured.add(new AiResponse.Success(json, Duration.ofMillis(1), Optional.empty()));
		return this;
	}

	CountingAi failStructured(AiResponse.Failure failure) {
		structured.add(failure);
		return this;
	}

	CountingAi beforeStructured(Runnable hook) {
		beforeStructured = hook;
		return this;
	}

	int calls(AiRole role) {
		return calls.getOrDefault(role, new AtomicInteger()).get();
	}

	int total() {
		return List.of(AiRole.values()).stream().mapToInt(this::calls).sum();
	}

	boolean calledInsideTransaction() {
		return transactionSeen;
	}

	@Override
	public AiResponse generateStructured(AiStructuredRequest request) {
		record(request.role());
		beforeStructured.run();
		synchronized (structured) {
			if (structured.isEmpty()) {
				throw new AssertionError("Unscripted structured call");
			}
			return structured.poll();
		}
	}

	@Override
	public AiResponse generateText(AiTextRequest request) {
		record(request.role());
		if (crashText) {
			throw new IllegalStateException("simulated crash while narrating");
		}
		if (!textDelay.isZero()) {
			try {
				Thread.sleep(textDelay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return new AiResponse.Success(PROSE, Duration.ofMillis(1), Optional.empty());
	}

	private void record(AiRole role) {
		transactionSeen |= TransactionSynchronizationManager.isActualTransactionActive();
		calls.computeIfAbsent(role, r -> new AtomicInteger()).incrementAndGet();
	}
}
