package com.leeburke.springgame.character;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Test double: answers every {@code nextInt(bound)} with the lowest or highest possible value and
 * records the bounds requested. Any other kind of random call fails, exposing unexpected usage.
 */
final class FixedChoiceGenerator implements RandomGenerator {

	private final boolean highest;
	final List<Integer> requestedBounds = new ArrayList<>();

	private FixedChoiceGenerator(boolean highest) {
		this.highest = highest;
	}

	static FixedChoiceGenerator lowest() {
		return new FixedChoiceGenerator(false);
	}

	static FixedChoiceGenerator highest() {
		return new FixedChoiceGenerator(true);
	}

	@Override
	public int nextInt(int bound) {
		requestedBounds.add(bound);
		return highest ? bound - 1 : 0;
	}

	@Override
	public long nextLong() {
		throw new UnsupportedOperationException("Only nextInt(bound) is expected");
	}
}
