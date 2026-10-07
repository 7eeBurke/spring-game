package com.leeburke.springgame.world.generation;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * The single procedural randomness strategy for world generation.
 * <p>
 * Generators use the {@code L64X128MixRandom} algorithm, which is precisely specified by the JDK's
 * {@code java.util.random} documentation (not an implementation default). Seeds are derived with
 * the SplitMix64 finalizer so that each attempt and each scene gets an independent stream.
 */
public final class WorldRandom {

	public static final String ALGORITHM = "L64X128MixRandom";

	static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
	static final long ATTEMPT_DOMAIN = 1;
	static final long SCENE_DOMAIN = 2;

	private static final RandomGeneratorFactory<RandomGenerator> FACTORY = RandomGeneratorFactory.of(ALGORITHM);

	private WorldRandom() {
	}

	public static RandomGenerator create(long seed) {
		return FACTORY.create(seed);
	}

	/** SplitMix64 finalizer. Arithmetic wraps on overflow by design. */
	static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	static long derive(long parent, long domain, long index) {
		return mix64(parent ^ mix64(domain + index * GOLDEN_GAMMA));
	}

	/** Seed for generation attempt {@code attempt} (0-based) of a run. */
	public static long attemptSeed(long runSeed, int attempt) {
		return derive(runSeed, ATTEMPT_DOMAIN, attempt);
	}

	/** Seed for the scene at {@code sceneIndex} in generation order within an attempt. */
	public static long sceneSeed(long attemptSeed, int sceneIndex) {
		return derive(attemptSeed, SCENE_DOMAIN, sceneIndex);
	}
}
