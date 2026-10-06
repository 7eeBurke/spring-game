package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatValueTest {

	@ParameterizedTest
	@ValueSource(ints = { 3, 4, 5, 6, 7, 8, 9, 10 })
	void acceptsEveryValueInDocumentedRange(int value) {
		assertThat(new StatValue(value).value()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 11, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE })
	void rejectsValuesOutsideDocumentedRange(int value) {
		assertThatThrownBy(() -> new StatValue(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(String.valueOf(value));
	}

	@Test
	void boundsMatchDocumentation() {
		assertThat(StatValue.MIN).isEqualTo(3);
		assertThat(StatValue.MAX).isEqualTo(10);
	}

	@Test
	void equalityIsByValue() {
		assertThat(new StatValue(7)).isEqualTo(new StatValue(7)).hasSameHashCodeAs(new StatValue(7));
		assertThat(new StatValue(7)).isNotEqualTo(new StatValue(8));
	}
}
