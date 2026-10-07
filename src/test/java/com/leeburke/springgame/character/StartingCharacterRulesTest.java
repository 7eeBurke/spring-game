package com.leeburke.springgame.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.mechanics.StatValue;

class StartingCharacterRulesTest {

	@Test
	void fatedWeightsMatchDocumentedDistribution() {
		assertThat(StartingCharacterRules.FATED_WEIGHTS).containsExactly(25, 25, 22, 15, 9, 4);
		assertThat(StartingCharacterRules.FATED_WEIGHTS.stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
	}

	@ParameterizedTest
	@CsvSource({
			"0,0", "24,0",
			"25,1", "49,1",
			"50,2", "71,2",
			"72,3", "86,3",
			"87,4", "95,4",
			"96,5", "99,5" })
	void fatedBandBoundaries(int roll, int expectedFated) {
		assertThat(StartingCharacterRules.fatedForPercentileRoll(roll)).isEqualTo(new Fated(expectedFated));
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 100 })
	void rejectsOutOfRangePercentileRoll(int roll) {
		assertThatIllegalArgumentException().isThrownBy(() -> StartingCharacterRules.fatedForPercentileRoll(roll));
	}

	@Test
	void rollFatedDrawsAPercentileFromSuppliedGenerator() {
		RecordingGenerator rng = new RecordingGenerator(72);

		Fated fated = StartingCharacterRules.rollFated(rng);

		assertThat(rng.bound).isEqualTo(100);
		assertThat(fated).isEqualTo(new Fated(3));
	}

	@ParameterizedTest
	@CsvSource({ "3,23", "4,24", "5,25", "6,26", "7,27", "8,28", "9,29", "10,30" })
	void startingMaxHpFromResolve(int resolve, int expectedMaxHp) {
		assertThat(StartingCharacterRules.startingMaxHp(new StatValue(resolve))).isEqualTo(expectedMaxHp);
	}

	@Test
	void toolBeltCapacityIsFive() {
		assertThat(StartingCharacterRules.TOOL_BELT_CAPACITY).isEqualTo(5);
	}

	private static final class RecordingGenerator implements RandomGenerator {
		private final int value;
		private int bound;

		RecordingGenerator(int value) {
			this.value = value;
		}

		@Override
		public int nextInt(int bound) {
			this.bound = bound;
			return value;
		}

		@Override
		public long nextLong() {
			throw new UnsupportedOperationException();
		}
	}
}
