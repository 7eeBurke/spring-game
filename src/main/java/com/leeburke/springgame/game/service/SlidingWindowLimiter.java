package com.leeburke.springgame.game.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * At most {@code limit} events per key within a sliding {@code window}, in memory. Keys with no
 * recent events are dropped so the map stays small.
 */
final class SlidingWindowLimiter {

	private static final int PRUNE_ABOVE_KEYS = 10_000;

	private final int limit;
	private final Duration window;
	private final Clock clock;
	private final Map<String, Deque<Instant>> events = new HashMap<>();

	SlidingWindowLimiter(int limit, Duration window, Clock clock) {
		if (limit < 0) {
			throw new IllegalArgumentException("A limit cannot be negative");
		}
		this.limit = limit;
		this.window = Objects.requireNonNull(window, "window");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/** Whether another event for the key would still be within the limit. */
	synchronized boolean allows(String key) {
		return recent(key).size() < limit;
	}

	synchronized void record(String key) {
		recent(key).addLast(clock.instant());
	}

	/** Records the event if it is within the limit. */
	synchronized boolean tryAcquire(String key) {
		if (!allows(key)) {
			return false;
		}
		record(key);
		return true;
	}

	private Deque<Instant> recent(String key) {
		Instant cutoff = clock.instant().minus(window);
		if (events.size() > PRUNE_ABOVE_KEYS) {
			events.values().forEach(deque -> prune(deque, cutoff));
			events.values().removeIf(Deque::isEmpty);
		}
		Deque<Instant> deque = events.computeIfAbsent(key, k -> new ArrayDeque<>());
		prune(deque, cutoff);
		return deque;
	}

	private static void prune(Deque<Instant> deque, Instant cutoff) {
		while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
			deque.removeFirst();
		}
	}
}
