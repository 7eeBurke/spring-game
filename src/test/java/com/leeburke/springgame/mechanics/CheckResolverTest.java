package com.leeburke.springgame.mechanics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CheckResolverTest {

	private final CheckResolver resolver = new CheckResolver();

	private static DcAdjustment adj(DcAdjustmentSource source, int value) {
		return new DcAdjustment(source, value);
	}

	/** Stat 6 (+0), base DC 10, no suitability, no adjustments: margin = roll - 10. */
	private static CheckRequest neutralRequest() {
		return new CheckRequest(StatType.AGILITY, new StatValue(6), 10, Optional.empty(), List.of());
	}

	@Test
	void resultRetainsFullBreakdown() {
		CheckRequest request = new CheckRequest(StatType.MIGHT, new StatValue(8), 14,
				Optional.of(Suitability.TERRIBLE),
				List.of(adj(DcAdjustmentSource.INJURY, 1), adj(DcAdjustmentSource.POSITION, 2)));

		CheckResult result = resolver.resolve(request, 17);

		assertThat(result.stat()).isEqualTo(StatType.MIGHT);
		assertThat(result.statValue()).isEqualTo(new StatValue(8));
		assertThat(result.statModifier()).isEqualTo(2);
		assertThat(result.rawRoll()).isEqualTo(17);
		assertThat(result.rollTotal()).isEqualTo(19);
		assertThat(result.baseDc()).isEqualTo(14);
		assertThat(result.suitability()).contains(Suitability.TERRIBLE);
		assertThat(result.appliedAdjustments()).containsExactly(
				adj(DcAdjustmentSource.SUITABILITY, 3),
				adj(DcAdjustmentSource.INJURY, 1),
				adj(DcAdjustmentSource.POSITION, 2));
		assertThat(result.adjustmentTotal()).isEqualTo(6);
		assertThat(result.clampedAdjustmentTotal()).isEqualTo(5);
		assertThat(result.finalDc()).isEqualTo(19);
		assertThat(result.margin()).isZero();
		assertThat(result.degree()).isEqualTo(DegreeOfSuccess.SUCCESS);
	}

	@Test
	void totalInsideCapIsUnchanged() {
		CheckRequest request = new CheckRequest(StatType.PERCEPTION, new StatValue(6), 12,
				Optional.of(Suitability.GOOD), List.of(adj(DcAdjustmentSource.ENVIRONMENT, 2)));

		CheckResult result = resolver.resolve(request, 10);

		assertThat(result.adjustmentTotal()).isEqualTo(1);
		assertThat(result.clampedAdjustmentTotal()).isEqualTo(1);
		assertThat(result.finalDc()).isEqualTo(13);
	}

	@Test
	void positiveTotalIsClampedToPlusFive() {
		CheckRequest request = new CheckRequest(StatType.ARCANA, new StatValue(6), 14,
				Optional.of(Suitability.TERRIBLE),
				List.of(adj(DcAdjustmentSource.TARGETING, 2), adj(DcAdjustmentSource.SIMULTANEOUS_ACTION_COMPLEXITY, 3)));

		CheckResult result = resolver.resolve(request, 10);

		assertThat(result.adjustmentTotal()).isEqualTo(8);
		assertThat(result.clampedAdjustmentTotal()).isEqualTo(5);
		assertThat(result.finalDc()).isEqualTo(19);
	}

	@Test
	void negativeTotalIsClampedToMinusFive() {
		CheckRequest request = new CheckRequest(StatType.AGILITY, new StatValue(6), 14,
				Optional.of(Suitability.EXCELLENT),
				List.of(adj(DcAdjustmentSource.PASSIVE, -2), adj(DcAdjustmentSource.POSITION, -3)));

		CheckResult result = resolver.resolve(request, 10);

		assertThat(result.adjustmentTotal()).isEqualTo(-7);
		assertThat(result.clampedAdjustmentTotal()).isEqualTo(-5);
		assertThat(result.finalDc()).isEqualTo(9);
	}

	@Test
	void absentSuitabilityAddsNoSuitabilityEntry() {
		CheckRequest request = new CheckRequest(StatType.RESOLVE, new StatValue(6), 12,
				Optional.empty(), List.of(adj(DcAdjustmentSource.ENVIRONMENT, 2)));

		CheckResult result = resolver.resolve(request, 10);

		assertThat(result.suitability()).isEmpty();
		assertThat(result.appliedAdjustments()).containsExactly(adj(DcAdjustmentSource.ENVIRONMENT, 2));
		assertThat(result.adjustmentTotal()).isEqualTo(2);
	}

	@ParameterizedTest
	@CsvSource({
			"15,5,CRITICAL_SUCCESS",
			"14,4,SUCCESS",
			"10,0,SUCCESS",
			"9,-1,PARTIAL_SUCCESS",
			"6,-4,PARTIAL_SUCCESS",
			"5,-5,FAILURE" })
	void degreeBoundariesThroughResolver(int roll, int expectedMargin, DegreeOfSuccess expected) {
		CheckResult result = resolver.resolve(neutralRequest(), roll);
		assertThat(result.margin()).isEqualTo(expectedMargin);
		assertThat(result.degree()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 20 })
	void acceptsBoundaryRolls(int roll) {
		assertThat(resolver.resolve(neutralRequest(), roll).rawRoll()).isEqualTo(roll);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 21 })
	void rejectsOutOfRangeRolls(int roll) {
		assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(neutralRequest(), roll));
	}

	@Test
	void naturalOneHasNoSpecialFailure() {
		CheckRequest request = new CheckRequest(StatType.AGILITY, new StatValue(10), 10,
				Optional.of(Suitability.EXCELLENT), List.of(adj(DcAdjustmentSource.ENVIRONMENT, -3)));

		CheckResult result = resolver.resolve(request, 1);

		assertThat(result.clampedAdjustmentTotal()).isEqualTo(-5);
		assertThat(result.finalDc()).isEqualTo(5);
		assertThat(result.rollTotal()).isEqualTo(5);
		assertThat(result.margin()).isZero();
		assertThat(result.degree()).isEqualTo(DegreeOfSuccess.SUCCESS);
	}

	@Test
	void finalDcOverflowThrows() {
		CheckRequest request = new CheckRequest(StatType.MIGHT, new StatValue(6), Integer.MAX_VALUE,
				Optional.of(Suitability.POOR), List.of());
		assertThatThrownBy(() -> resolver.resolve(request, 10)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void marginOverflowThrows() {
		CheckRequest request = new CheckRequest(StatType.MIGHT, new StatValue(6), Integer.MIN_VALUE,
				Optional.empty(), List.of());
		assertThatThrownBy(() -> resolver.resolve(request, 10)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void sameSeedReproducesSameResults() {
		SplittableRandom first = new SplittableRandom(42);
		SplittableRandom second = new SplittableRandom(42);
		List<CheckResult> a = new ArrayList<>();
		List<CheckResult> b = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			a.add(resolver.resolve(neutralRequest(), first));
			b.add(resolver.resolve(neutralRequest(), second));
		}
		assertThat(a).isEqualTo(b);
	}

	@Test
	void seededRollsStayInRange() {
		for (long seed = 0; seed < 1000; seed++) {
			assertThat(resolver.resolve(neutralRequest(), new SplittableRandom(seed)).rawRoll()).isBetween(1, 20);
		}
	}

	@Test
	void d20RequestsUniformOneToTwentyInclusive() {
		RecordingGenerator rng = new RecordingGenerator(13);

		CheckResult result = resolver.resolve(neutralRequest(), rng);

		assertThat(rng.origin).isEqualTo(1);
		assertThat(rng.bound).isEqualTo(21);
		assertThat(result.rawRoll()).isEqualTo(13);
	}

	@Test
	void appliedAdjustmentsAreUnmodifiable() {
		CheckResult result = resolver.resolve(neutralRequest(), 10);
		assertThatThrownBy(() -> result.appliedAdjustments().add(adj(DcAdjustmentSource.INJURY, 1)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	/** Records the bounded-int request and returns a fixed value. */
	private static final class RecordingGenerator implements RandomGenerator {
		private final int value;
		private int origin;
		private int bound;

		RecordingGenerator(int value) {
			this.value = value;
		}

		@Override
		public int nextInt(int origin, int bound) {
			this.origin = origin;
			this.bound = bound;
			return value;
		}

		@Override
		public long nextLong() {
			throw new UnsupportedOperationException("Only nextInt(origin, bound) is expected");
		}
	}
}
