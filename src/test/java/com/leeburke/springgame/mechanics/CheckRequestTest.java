package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CheckRequestTest {

	private static final StatValue SIX = new StatValue(6);

	@Test
	void rejectsImpossibleSuitability() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CheckRequest(
				StatType.MIGHT, SIX, 14, Optional.of(Suitability.IMPOSSIBLE), List.of()));
	}

	@Test
	void rejectsSuitabilityPassedAsAdjustment() {
		List<DcAdjustment> adjustments = List.of(new DcAdjustment(DcAdjustmentSource.SUITABILITY, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new CheckRequest(
				StatType.MIGHT, SIX, 14, Optional.empty(), adjustments));
	}

	@Test
	void rejectsNullComponents() {
		assertThatNullPointerException().isThrownBy(() -> new CheckRequest(null, SIX, 14, Optional.empty(), List.of()));
		assertThatNullPointerException().isThrownBy(() -> new CheckRequest(StatType.MIGHT, null, 14, Optional.empty(), List.of()));
		assertThatNullPointerException().isThrownBy(() -> new CheckRequest(StatType.MIGHT, SIX, 14, null, List.of()));
		assertThatNullPointerException().isThrownBy(() -> new CheckRequest(StatType.MIGHT, SIX, 14, Optional.empty(), null));
	}

	@Test
	void rejectsNullAdjustmentElement() {
		List<DcAdjustment> withNull = new ArrayList<>();
		withNull.add(null);
		assertThatNullPointerException().isThrownBy(() -> new CheckRequest(
				StatType.MIGHT, SIX, 14, Optional.empty(), withNull));
	}

	@Test
	void copiesAdjustmentsDefensively() {
		List<DcAdjustment> source = new ArrayList<>();
		source.add(new DcAdjustment(DcAdjustmentSource.INJURY, 1));
		CheckRequest request = new CheckRequest(StatType.MIGHT, SIX, 14, Optional.empty(), source);

		source.add(new DcAdjustment(DcAdjustmentSource.POSITION, 2));

		assertThat(request.adjustments()).containsExactly(new DcAdjustment(DcAdjustmentSource.INJURY, 1));
	}

	@Test
	void allowsAbsentSuitability() {
		CheckRequest request = new CheckRequest(StatType.RESOLVE, SIX, 12, Optional.empty(), List.of());
		assertThat(request.suitability()).isEmpty();
	}
}
