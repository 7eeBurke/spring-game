package com.leeburke.springgame.enemy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A controlled random source: returns scripted values from bounded {@code nextInt(bound)} and
 * {@code nextLong(bound)} calls, records each bound, and fails on any other kind of draw.
 */
public final class ScriptedRandom implements RandomGenerator {

	private final Deque<Long> values = new ArrayDeque<>();
	private final List<Long> bounds = new ArrayList<>();

	public ScriptedRandom(long... values) {
		for (long value : values) {
			this.values.add(value);
		}
	}

	@Override
	public int nextInt(int bound) {
		return Math.toIntExact(next(bound));
	}

	@Override
	public long nextLong(long bound) {
		return next(bound);
	}

	@Override
	public long nextLong() {
		throw new AssertionError("Only bounded draws are expected");
	}

	private long next(long bound) {
		if (values.isEmpty()) {
			throw new AssertionError("More draws than scripted");
		}
		long value = values.poll();
		if (value < 0 || value >= bound) {
			throw new AssertionError("Scripted value " + value + " is outside [0, " + bound + ")");
		}
		bounds.add(bound);
		return value;
	}

	/** The bound of every draw made, in order. */
	public List<Long> bounds() {
		return List.copyOf(bounds);
	}
}
