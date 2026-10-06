package com.leeburke.springgame.character;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.StatValue;

/**
 * A valid player stat spread before its values are assigned to named stats: five values,
 * each within the stat range, totalling {@value #TOTAL}.
 * <p>
 * Values are stored in canonical (non-increasing) order, so two shapes made from the same
 * values in any order are equal. See docs/CHARACTER_GENERATION.md "Stat Budget".
 */
public record StatShape(List<Integer> values) {

	public static final int SIZE = 5;
	public static final int TOTAL = 27;

	public StatShape {
		Objects.requireNonNull(values, "values");
		values = List.copyOf(values);
		if (values.size() != SIZE) {
			throw new IllegalArgumentException("A stat shape needs " + SIZE + " values, but had " + values.size());
		}
		for (int value : values) {
			if (value < StatValue.MIN || value > StatValue.MAX) {
				throw new IllegalArgumentException("Stat shape value out of range: " + value);
			}
		}
		int total = values.stream().mapToInt(Integer::intValue).sum();
		if (total != TOTAL) {
			throw new IllegalArgumentException("A stat shape must total " + TOTAL + ", but totalled " + total);
		}
		values = values.stream().sorted(Comparator.reverseOrder()).toList();
	}

	public static StatShape of(StatBlock block) {
		Objects.requireNonNull(block, "block");
		return new StatShape(Arrays.stream(StatType.values()).map(type -> block.get(type).value()).toList());
	}

	public int max() {
		return values.getFirst();
	}

	public int min() {
		return values.getLast();
	}
}
