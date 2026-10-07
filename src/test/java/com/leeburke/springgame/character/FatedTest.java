package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FatedTest {

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
	void acceptsZeroThroughFive(int value) {
		assertThat(new Fated(value).value()).isEqualTo(value);
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 6 })
	void rejectsOutOfRange(int value) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Fated(value));
	}
}
