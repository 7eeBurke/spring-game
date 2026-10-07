package com.leeburke.springgame.world.generation;

import static com.leeburke.springgame.world.generation.GenerationTestSupport.fixed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArchetypeSelectorTest {

	private static final List<String> NORMAL =
			List.of("RUINED_NAVE", "CLOISTER", "SACRISTY", "OSSUARY", "BELL_PASSAGE", "RELIQUARY");

	private static List<Integer> weights(String... recent) {
		return ArchetypeSelector.openingWeights(NORMAL, new GenerationContextSnapshot(List.of(recent)));
	}

	@Test
	void unseenArchetypesKeepFullWeight() {
		assertThat(weights()).containsExactly(8, 8, 8, 8, 8, 8);
	}

	@Test
	void mostRecentOpenerIsPenalisedMost() {
		assertThat(weights("RUINED_NAVE")).containsExactly(1, 8, 8, 8, 8, 8);
		assertThat(weights("CLOISTER", "RUINED_NAVE", "SACRISTY")).containsExactly(3, 1, 5, 8, 8, 8);
	}

	@Test
	void duplicateCodeUsesItsMostRecentPosition() {
		assertThat(weights("OSSUARY", "CLOISTER", "OSSUARY")).containsExactly(8, 3, 8, 1, 8, 8);
	}

	@Test
	void irrelevantCodesHaveNoEffect() {
		assertThat(weights("MIRE_CAUSEWAY", "ASHEN_GATE")).containsExactly(8, 8, 8, 8, 8, 8);
	}

	@Test
	void selectionStillWorksWhenEveryCandidateIsRecent() {
		List<String> three = List.of("A_ROOM", "B_ROOM", "C_ROOM");
		GenerationContextSnapshot allRecent = new GenerationContextSnapshot(List.of("A_ROOM", "B_ROOM", "C_ROOM"));
		assertThat(ArchetypeSelector.openingWeights(three, allRecent)).containsExactly(1, 3, 5);
		assertThat(ArchetypeSelector.pickOpening(three, allRecent, fixed(0))).isEqualTo("A_ROOM");
		assertThat(ArchetypeSelector.pickOpening(three, allRecent, fixed(Integer.MAX_VALUE))).isEqualTo("C_ROOM");
	}

	@ParameterizedTest
	@CsvSource({ "0,RUINED_NAVE", "1,CLOISTER", "8,CLOISTER", "9,SACRISTY", "40,RELIQUARY" })
	void cumulativeWeightBoundaries(int roll, String expected) {
		// Weights with RUINED_NAVE most recent: 1, 8, 8, 8, 8, 8 (total 41).
		assertThat(ArchetypeSelector.openingForRoll(NORMAL, weights("RUINED_NAVE"), roll)).isEqualTo(expected);
	}

	@Test
	void pickOpeningDrawsFromTheTotalWeight() {
		// With a fixed highest roll the last archetype is chosen; with the lowest, the first.
		assertThat(ArchetypeSelector.pickOpening(NORMAL, GenerationContextSnapshot.empty(), fixed(0))).isEqualTo("RUINED_NAVE");
		assertThat(ArchetypeSelector.pickOpening(NORMAL, GenerationContextSnapshot.empty(), fixed(Integer.MAX_VALUE)))
				.isEqualTo("RELIQUARY");
	}

	@Test
	void allArchetypesAreUsedBeforeAnyIsReusedAndNeighboursNeverMatch() {
		// A straight chain of 9 scenes, each picking with the lowest roll.
		Map<String, Integer> usage = new HashMap<>();
		List<String> chain = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			Set<String> neighbour = i == 0 ? Set.of() : Set.of(chain.get(i - 1));
			String pick = ArchetypeSelector.pickNext(NORMAL, neighbour, usage, fixed(0));
			chain.add(pick);
			usage.merge(pick, 1, Integer::sum);
		}
		assertThat(new HashSet<>(chain.subList(0, 6))).hasSize(6);
		for (int i = 1; i < chain.size(); i++) {
			assertThat(chain.get(i)).isNotEqualTo(chain.get(i - 1));
		}
	}

	@Test
	void bothAssignedNeighboursAreExcluded() {
		Map<String, Integer> usage = new HashMap<>(Map.of("RUINED_NAVE", 1, "CLOISTER", 1));
		Set<String> neighbours = Set.of("SACRISTY", "OSSUARY");
		for (int roll = 0; roll < 4; roll++) {
			assertThat(ArchetypeSelector.pickNext(NORMAL, neighbours, usage, fixed(roll)))
					.isIn("BELL_PASSAGE", "RELIQUARY");
		}
	}

	@Test
	void leastUsedAllowedArchetypesFormThePool() {
		Map<String, Integer> usage = new HashMap<>(Map.of("RUINED_NAVE", 1, "CLOISTER", 1, "SACRISTY", 1,
				"OSSUARY", 1, "BELL_PASSAGE", 1, "RELIQUARY", 2));
		// RELIQUARY is used most; the neighbour is RUINED_NAVE. Pool: CLOISTER..BELL_PASSAGE.
		assertThat(ArchetypeSelector.pickNext(NORMAL, Set.of("RUINED_NAVE"), usage, fixed(0))).isEqualTo("CLOISTER");
		assertThat(ArchetypeSelector.pickNext(NORMAL, Set.of("RUINED_NAVE"), usage, fixed(Integer.MAX_VALUE))).isEqualTo("BELL_PASSAGE");
	}
}
