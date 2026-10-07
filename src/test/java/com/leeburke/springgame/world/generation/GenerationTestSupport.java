package com.leeburke.springgame.world.generation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.content.world.WorldContentCatalog;
import com.leeburke.springgame.content.world.WorldContentLoader;

/** Deterministic helpers for generation tests. */
final class GenerationTestSupport {

	static final WorldContentCatalog CONTENT = WorldContentLoader.loadBundled();
	static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-00000000aaaa");

	private GenerationTestSupport() {
	}

	/** Deterministic IDs: 00000000-0000-0000-0000-000000000001, ...002, ... */
	static IdSource sequentialIds() {
		long[] next = { 0 };
		return () -> new UUID(0, ++next[0]);
	}

	/** Deterministic IDs issued in a different order (counting down). */
	static IdSource descendingIds() {
		long[] next = { 1_000_000 };
		return () -> new UUID(7, next[0]--);
	}

	/** Answers every {@code nextInt(bound)} with {@code min(value, bound - 1)}; anything else fails. */
	static RandomGenerator fixed(int value) {
		return new RandomGenerator() {
			@Override
			public int nextInt(int bound) {
				return Math.min(value, bound - 1);
			}

			@Override
			public long nextLong() {
				throw new UnsupportedOperationException("Only nextInt(bound) is expected");
			}
		};
	}

	/**
	 * A UUID-independent description of a generated world: every runtime ID is replaced by its role
	 * and generation position (HUB, REGION, S0, S1, ...). Two worlds with equal shapes have the same
	 * topology, archetypes, contents, exits and scene seeds.
	 */
	static String shape(GeneratedRunWorld world) {
		Map<UUID, String> names = new LinkedHashMap<>();
		names.put(world.hub().id(), "HUB");
		names.put(world.runId(), "RUN");
		return rename(world.toString(), names, world.region());
	}

	/** As {@link #shape(GeneratedRunWorld)} for a region alone; the hub ID is named HUB. */
	static String shape(GeneratedRegion region, UUID hubId) {
		Map<UUID, String> names = new LinkedHashMap<>();
		names.put(hubId, "HUB");
		names.put(region.region().runId(), "RUN");
		return rename(region.toString(), names, region);
	}

	private static String rename(String text, Map<UUID, String> names, GeneratedRegion region) {
		names.put(region.region().id(), "REGION");
		for (int i = 0; i < region.scenes().size(); i++) {
			names.put(region.scenes().get(i).id(), "S" + i);
		}
		String result = text;
		for (Map.Entry<UUID, String> entry : names.entrySet()) {
			result = result.replace(entry.getKey().toString(), entry.getValue());
		}
		return result;
	}
}
