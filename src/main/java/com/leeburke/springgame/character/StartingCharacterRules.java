package com.leeburke.springgame.character;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.mechanics.StatValue;

/**
 * V1 starting-character rules. See docs/CHARACTER_GENERATION.md.
 * <p>
 * Fated rules take no stat input, so Fated is independent of the stat profile. The HP rule is a
 * character-generation rule of its own and deliberately does not reuse check stat modifiers.
 */
public final class StartingCharacterRules {

	/** Fated generation weights in percent, indexed by Fated value 0..5. They total 100. */
	public static final List<Integer> FATED_WEIGHTS = List.of(25, 25, 22, 15, 9, 4);

	/** Exclusive upper bound of the percentile roll used to draw Fated. */
	public static final int PERCENTILE = 100;

	public static final int BASE_HP = 26;
	public static final int HP_RESOLVE_BASELINE = 6;

	/** Tool-belt slots; the weapon, recovery item and utility item each occupy one. */
	public static final int TOOL_BELT_CAPACITY = 5;

	private StartingCharacterRules() {
	}

	/**
	 * Maps a percentile roll in [0, 100) to Fated:
	 * 0-24 → 0, 25-49 → 1, 50-71 → 2, 72-86 → 3, 87-95 → 4, 96-99 → 5.
	 */
	public static Fated fatedForPercentileRoll(int roll) {
		if (roll < 0 || roll >= PERCENTILE) {
			throw new IllegalArgumentException("Percentile roll must be in [0, " + PERCENTILE + "), but was " + roll);
		}
		int remaining = roll;
		for (int value = 0; value < FATED_WEIGHTS.size(); value++) {
			remaining -= FATED_WEIGHTS.get(value);
			if (remaining < 0) {
				return new Fated(value);
			}
		}
		throw new IllegalStateException("Fated weights do not cover roll " + roll);
	}

	public static Fated rollFated(RandomGenerator rng) {
		Objects.requireNonNull(rng, "rng");
		return fatedForPercentileRoll(rng.nextInt(PERCENTILE));
	}

	/** {@code 26 + (Resolve - 6)}: Resolve 3..10 gives 23..30. */
	public static int startingMaxHp(StatValue resolve) {
		Objects.requireNonNull(resolve, "resolve");
		return Math.addExact(BASE_HP, Math.subtractExact(resolve.value(), HP_RESOLVE_BASELINE));
	}
}
