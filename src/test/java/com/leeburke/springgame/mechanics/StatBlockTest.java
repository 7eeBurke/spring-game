package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StatBlockTest {

	// Distinct values so that a swapped mapping in get(StatType) is detected.
	private static final StatBlock BLOCK = new StatBlock(
			new StatValue(3), new StatValue(4), new StatValue(6), new StatValue(8), new StatValue(10));

	@Test
	void getReturnsMatchingComponentForEveryStatType() {
		assertThat(BLOCK.get(StatType.MIGHT)).isEqualTo(new StatValue(3));
		assertThat(BLOCK.get(StatType.AGILITY)).isEqualTo(new StatValue(4));
		assertThat(BLOCK.get(StatType.PERCEPTION)).isEqualTo(new StatValue(6));
		assertThat(BLOCK.get(StatType.ARCANA)).isEqualTo(new StatValue(8));
		assertThat(BLOCK.get(StatType.RESOLVE)).isEqualTo(new StatValue(10));
	}

	@ParameterizedTest
	@EnumSource(StatType.class)
	void rejectsMissingStat(StatType missing) {
		assertThatNullPointerException()
				.isThrownBy(() -> blockWithout(missing))
				.withMessage(missing.name().toLowerCase(Locale.ROOT));
	}

	@Test
	void equalityIsByValue() {
		StatBlock same = new StatBlock(
				new StatValue(3), new StatValue(4), new StatValue(6), new StatValue(8), new StatValue(10));
		assertThat(same).isEqualTo(BLOCK).hasSameHashCodeAs(BLOCK);
	}

	private static StatBlock blockWithout(StatType missing) {
		StatValue v = new StatValue(6);
		return new StatBlock(
				missing == StatType.MIGHT ? null : v,
				missing == StatType.AGILITY ? null : v,
				missing == StatType.PERCEPTION ? null : v,
				missing == StatType.ARCANA ? null : v,
				missing == StatType.RESOLVE ? null : v);
	}
}
