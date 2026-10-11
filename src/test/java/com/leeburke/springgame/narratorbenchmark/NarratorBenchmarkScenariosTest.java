package com.leeburke.springgame.narratorbenchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.NarratorBenchmarkAccess;
import com.leeburke.springgame.narratorbenchmark.BenchmarkScenarios.Scenario;

/**
 * The narrator benchmark's fixtures are honest: each scenario is a real, Java-resolved narrator
 * context with the facts its title promises, built from bundled content only, with nothing hidden
 * or internal in what the narrator would receive. Offline: no model is called.
 */
class NarratorBenchmarkScenariosTest {

	private static List<Scenario> scenarios;

	@BeforeAll
	static void build() {
		scenarios = BenchmarkScenarios.build();
	}

	private static List<String> facts(int number) {
		return scenarios.get(number - 1).context().facts().stream().map(f -> f.getClass().getSimpleName()).toList();
	}

	@Test
	void eightScenariosInOrderEachWithTheFactsItsTitlePromises() {
		assertThat(scenarios).extracting(Scenario::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
		assertThat(facts(1)).containsExactly("CrossedInto");
		assertThat(facts(2)).containsExactly("CrossedInto");
		assertThat(facts(3)).containsExactly("WalkedTo");
		assertThat(facts(4)).containsExactly("OpenedContainer");
		assertThat(facts(5)).containsExactly("TookItem");
		assertThat(facts(6)).containsExactly("SoughtWays");
		assertThat(facts(7)).containsExactly("Perceived");
		assertThat(facts(8)).containsExactly("PlayerAttacked");
	}

	@Test
	void theSubjectsAreTheOnesAsked() {
		NarrationFact.CrossedInto chapel = (NarrationFact.CrossedInto) scenarios.get(0).context().facts().getFirst();
		assertThat(chapel.scene()).isEqualTo("Ruined Nave");
		assertThat(chapel.behind()).hasValueSatisfying(back -> assertThat(back).contains("west doors"));
		NarrationFact.CrossedInto bells = (NarrationFact.CrossedInto) scenarios.get(1).context().facts().getFirst();
		assertThat(bells.scene()).isEqualTo("Bell Passage");
		assertThat(scenarios.get(1).wording()).as("the words name the passage actually crossed").endsWith(bells.through());
		assertThat(((NarrationFact.OpenedContainer) scenarios.get(3).context().facts().getFirst()).contents())
				.containsExactly(new NarrationFact.Found("Restorative Salve", "A small stoppered clay vial of thick, bitter-smelling ointment."));
		NarrationFact.TookItem took = (NarrationFact.TookItem) scenarios.get(4).context().facts().getFirst();
		assertThat(took.item()).isEqualTo("Restorative Salve");
		assertThat(took.description()).isEqualTo("A small stoppered clay vial of thick, bitter-smelling ointment.");
		NarrationFact.SoughtWays ways = (NarrationFact.SoughtWays) scenarios.get(5).context().facts().getFirst();
		assertThat(ways.unexplored()).isNotEmpty();
		assertThat(ways.nothingKnownLeft()).isFalse();
		assertThat(((NarrationFact.Perceived) scenarios.get(6).context().facts().getFirst()).unchanged()).isTrue();
		NarrationFact.PlayerAttacked blow = (NarrationFact.PlayerAttacked) scenarios.get(7).context().facts().getFirst();
		assertThat(blow.noContact()).isFalse();
	}

	@Test
	void theNarratorRequestHoldsNothingInternal() {
		for (Scenario scenario : scenarios) {
			String request = NarratorBenchmarkAccess.requestJson(scenario.context());
			assertThat(request).as("scenario " + scenario.number())
					.doesNotContain("first_find", "nave_entrance", "central_aisle", "lantern_road", "exit_", "zone_", "object_", "entity_")
					.doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-");
			assertThat(request).contains(scenario.wording());
		}
	}

	@Test
	void theSameSeedGivesTheSameScenarios() {
		Map<Integer, String> again = new java.util.HashMap<>();
		BenchmarkScenarios.build().forEach(s -> again.put(s.number(), NarratorBenchmarkAccess.requestJson(s.context())));
		scenarios.forEach(s -> assertThat(again.get(s.number())).isEqualTo(NarratorBenchmarkAccess.requestJson(s.context())));
	}
}
