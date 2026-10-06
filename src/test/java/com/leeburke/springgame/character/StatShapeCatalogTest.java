package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.mechanics.StatValue;

class StatShapeCatalogTest {

	@Test
	void containsNoDuplicates() {
		List<StatShape> all = StatShapeCatalog.allShapes();
		assertThat(new HashSet<>(all)).hasSameSizeAs(all);
	}

	@Test
	void everyShapeHasFiveInRangeValuesTotalling27() {
		for (StatShape shape : StatShapeCatalog.allShapes()) {
			assertThat(shape.values()).hasSize(5).allSatisfy(v -> assertThat(v).isBetween(3, 10));
			assertThat(shape.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(27);
		}
	}

	@Test
	void matchesIndependentBruteForceEnumeration() {
		Set<List<Integer>> expected = new HashSet<>();
		int min = StatValue.MIN;
		int max = StatValue.MAX;
		for (int a = min; a <= max; a++)
			for (int b = min; b <= max; b++)
				for (int c = min; c <= max; c++)
					for (int d = min; d <= max; d++)
						for (int e = min; e <= max; e++) {
							if (a + b + c + d + e == 27) {
								List<Integer> sorted = new ArrayList<>(List.of(a, b, c, d, e));
								sorted.sort((x, y) -> y - x);
								expected.add(List.copyOf(sorted));
							}
						}

		Set<List<Integer>> actual = new HashSet<>();
		StatShapeCatalog.allShapes().forEach(s -> actual.add(s.values()));
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void profileListsPartitionAllShapes() {
		List<StatShape> combined = new ArrayList<>();
		for (StatProfile profile : StatProfile.values()) {
			assertThat(StatShapeCatalog.shapesFor(profile)).as(profile.name()).isNotEmpty()
					.allSatisfy(s -> assertThat(StatProfile.classify(s)).isEqualTo(profile));
			combined.addAll(StatShapeCatalog.shapesFor(profile));
		}
		assertThat(combined).containsExactlyInAnyOrderElementsOf(StatShapeCatalog.allShapes());
	}

	@Test
	void listsAreUnmodifiable() {
		assertThatThrownBy(() -> StatShapeCatalog.allShapes().clear())
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> StatShapeCatalog.shapesFor(StatProfile.BALANCED).clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}
}
