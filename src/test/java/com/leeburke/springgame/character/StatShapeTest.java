package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;

class StatShapeTest {

	@Test
	void normalisesToNonIncreasingOrder() {
		StatShape shape = new StatShape(List.of(5, 5, 7, 5, 5));
		assertThat(shape.values()).containsExactly(7, 5, 5, 5, 5);
		assertThat(shape).isEqualTo(new StatShape(List.of(7, 5, 5, 5, 5)));
		assertThat(shape.max()).isEqualTo(7);
		assertThat(shape.min()).isEqualTo(5);
	}

	static Stream<List<Integer>> invalidShapes() {
		return Stream.of(
				List.of(7, 7, 7, 6),          // size 4
				List.of(6, 5, 4, 4, 4, 4),    // size 6
				List.of(10, 10, 2, 2, 3),     // value below range (total 27)
				List.of(11, 4, 4, 4, 4),      // value above range (total 27)
				List.of(6, 5, 5, 5, 5),       // total 26
				List.of(6, 6, 6, 5, 5));      // total 28
	}

	@ParameterizedTest
	@MethodSource("invalidShapes")
	void rejectsInvalidShapes(List<Integer> values) {
		assertThatIllegalArgumentException().isThrownBy(() -> new StatShape(values));
	}

	@Test
	void rejectsNull() {
		assertThatNullPointerException().isThrownBy(() -> new StatShape(null));
	}

	@Test
	void ofStatBlockGivesSortedValues() {
		StatBlock block = new StatBlock(
				new StatValue(4), new StatValue(9), new StatValue(5), new StatValue(3), new StatValue(6));
		assertThat(StatShape.of(block).values()).containsExactly(9, 6, 5, 4, 3);
	}
}
