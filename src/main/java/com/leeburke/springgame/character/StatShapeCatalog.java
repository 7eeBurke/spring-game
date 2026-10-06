package com.leeburke.springgame.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.leeburke.springgame.mechanics.StatValue;

/**
 * Every valid canonical {@link StatShape}, grouped by {@link StatProfile}.
 * <p>
 * Built once by enumerating non-increasing sequences, so each shape appears exactly once and
 * list order is stable. Seeded selection depends on that stable order.
 */
public final class StatShapeCatalog {

	private static final List<StatShape> ALL_SHAPES;
	private static final Map<StatProfile, List<StatShape>> SHAPES_BY_PROFILE;

	static {
		List<StatShape> all = new ArrayList<>();
		enumerate(new ArrayList<>(), StatValue.MAX, StatShape.TOTAL, all);
		ALL_SHAPES = List.copyOf(all);

		Map<StatProfile, List<StatShape>> byProfile = new EnumMap<>(StatProfile.class);
		for (StatProfile profile : StatProfile.values()) {
			byProfile.put(profile, ALL_SHAPES.stream().filter(profile::matches).toList());
		}
		SHAPES_BY_PROFILE = Collections.unmodifiableMap(byProfile);
	}

	private StatShapeCatalog() {
	}

	public static List<StatShape> allShapes() {
		return ALL_SHAPES;
	}

	public static List<StatShape> shapesFor(StatProfile profile) {
		return SHAPES_BY_PROFILE.get(profile);
	}

	private static void enumerate(List<Integer> prefix, int maxNext, int remaining, List<StatShape> out) {
		if (prefix.size() == StatShape.SIZE) {
			if (remaining == 0) {
				out.add(new StatShape(prefix));
			}
			return;
		}
		for (int value = maxNext; value >= StatValue.MIN; value--) {
			if (value > remaining) {
				continue;
			}
			prefix.add(value);
			enumerate(prefix, value, remaining - value, out);
			prefix.removeLast();
		}
	}
}
