package com.leeburke.springgame.world.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationContextSnapshotTest {

	@Test
	void emptySnapshot() {
		assertThat(GenerationContextSnapshot.empty().recentOpeningArchetypeCodes()).isEmpty();
	}

	@Test
	void acceptsOneToThreeCodes() {
		assertThat(new GenerationContextSnapshot(List.of("CLOISTER")).recentOpeningArchetypeCodes()).hasSize(1);
		assertThat(new GenerationContextSnapshot(List.of("CLOISTER", "OSSUARY", "CLOISTER")).recentOpeningArchetypeCodes())
				.containsExactly("CLOISTER", "OSSUARY", "CLOISTER");
	}

	@Test
	void acceptsCodesFromOtherRegions() {
		assertThat(new GenerationContextSnapshot(List.of("MIRE_CAUSEWAY")).recentOpeningArchetypeCodes()).hasSize(1);
	}

	@Test
	void rejectsMoreThanThree() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new GenerationContextSnapshot(List.of("CLOISTER", "OSSUARY", "SACRISTY", "RELIQUARY")));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", " CLOISTER", "CLOISTER ", "cloister" })
	void rejectsBlankUntrimmedOrBadCodes(String code) {
		assertThatIllegalArgumentException().isThrownBy(() -> new GenerationContextSnapshot(List.of(code)));
	}

	@Test
	void rejectsNulls() {
		assertThatNullPointerException().isThrownBy(() -> new GenerationContextSnapshot(null));
		assertThatNullPointerException().isThrownBy(() -> new GenerationContextSnapshot(Arrays.asList("CLOISTER", null)));
	}

	@Test
	void copiesCallerListAndIsImmutable() {
		List<String> source = new ArrayList<>(List.of("CLOISTER"));
		GenerationContextSnapshot snapshot = new GenerationContextSnapshot(source);
		source.add("OSSUARY");
		assertThat(snapshot.recentOpeningArchetypeCodes()).containsExactly("CLOISTER");
		assertThatThrownBy(() -> snapshot.recentOpeningArchetypeCodes().add("X")).isInstanceOf(UnsupportedOperationException.class);
	}
}
