package com.leeburke.springgame.world.generation;

import java.nio.charset.StandardCharsets;
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
	static final long ENTITY_DOMAIN = 3;
	static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
	static final long FNV_PRIME = 0x100000001B3L;

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

	/**
	 * Derives an independent child seed. Public so gameplay can derive per-turn streams from the run
	 * seed with its own domains; domains 1-3 belong to world generation.
	 */
	public static long derive(long parent, long domain, long index) {
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

	/**
	 * Seed for one placed entity's own generation (for example its enemy state), from its scene's
	 * seed and its scene-local ID. Each entity gets an independent stream, so adding, removing or
	 * reordering other entities never changes it.
	 */
	public static long entitySeed(long sceneSeed, String localId) {
		return derive(sceneSeed, ENTITY_DOMAIN, stableHash(localId));
	}

	/**
	 * 64-bit FNV-1a over the UTF-8 bytes. Fully specified here, unlike {@code String.hashCode}'s
	 * 32-bit range, and independent of any collection iteration order.
	 */
	static long stableHash(String value) {
		long hash = FNV_OFFSET_BASIS;
		for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
			hash ^= b & 0xFF;
			hash *= FNV_PRIME;
		}
		return hash;
	}
}
