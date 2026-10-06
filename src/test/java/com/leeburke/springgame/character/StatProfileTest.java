package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StatProfileTest {

	@Test
	void everyValidShapeMatchesExactlyOneProfile() {
		for (StatShape shape : StatShapeCatalog.allShapes()) {
			List<StatProfile> matching = Arrays.stream(StatProfile.values()).filter(p -> p.matches(shape)).toList();
			assertThat(matching).as("profiles matching %s", shape.values()).hasSize(1);
		}
	}

	static Stream<Arguments> boundaryShapes() {
		return Stream.of(
				Arguments.of(List.of(7, 5, 5, 5, 5), StatProfile.BALANCED),     // max 7, spread 2
				Arguments.of(List.of(6, 6, 6, 5, 4), StatProfile.BALANCED),     // min 4, spread 2
				Arguments.of(List.of(7, 6, 6, 4, 4), StatProfile.SPECIALIZED),  // spread 3
				Arguments.of(List.of(7, 6, 5, 5, 4), StatProfile.SPECIALIZED),  // spread 3, max 7
				Arguments.of(List.of(8, 5, 5, 5, 4), StatProfile.SPECIALIZED),  // max 8
				Arguments.of(List.of(9, 9, 3, 3, 3), StatProfile.SPECIALIZED),  // max 9, min 3
				Arguments.of(List.of(10, 5, 4, 4, 4), StatProfile.EXTREME),
				Arguments.of(List.of(10, 8, 3, 3, 3), StatProfile.EXTREME));
	}

	@ParameterizedTest
	@MethodSource("boundaryShapes")
	void classifiesBoundaryShapes(List<Integer> values, StatProfile expected) {
		assertThat(StatProfile.classify(new StatShape(values))).isEqualTo(expected);
	}

	@Test
	void selectionWeightsMatchDocumentedPercentages() {
		assertThat(StatProfile.BALANCED.selectionWeight()).isEqualTo(20);
		assertThat(StatProfile.SPECIALIZED.selectionWeight()).isEqualTo(70);
		assertThat(StatProfile.EXTREME.selectionWeight()).isEqualTo(10);
	}
}
