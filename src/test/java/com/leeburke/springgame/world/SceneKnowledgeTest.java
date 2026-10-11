package com.leeburke.springgame.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** What the player perceives, knows and can reach, over passages they know only. */
class SceneKnowledgeTest {

	/** threshold - racks - alcove in a line; a hidden passage from the threshold to a cellar. */
	private static SceneState sacristy(Optional<List<String>> seen) {
		return new SceneState(
				List.of(new SceneZone("threshold", "Vestry Threshold"), new SceneZone("racks", "Vestment Racks"),
						new SceneZone("alcove", "Narrow Alcove"), new SceneZone("cellar", "Cellar")),
				List.of(new ZoneConnection("t_r", "threshold", "racks"), new ZoneConnection("r_a", "racks", "alcove"),
						new ZoneConnection("t_c", "threshold", "cellar")),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
				List.of(new HiddenContentRef(HiddenContentKind.CONNECTION, "t_c")), List.of(), List.of(), seen);
	}

	@Test
	void perceptionIsHereAndWhatAKnownPassageJoinsToIt() {
		assertThat(SceneKnowledge.perceivable(sacristy(Optional.of(List.of())), "threshold")).containsExactly("threshold", "racks");
		assertThat(SceneKnowledge.perceivable(sacristy(Optional.of(List.of())), "racks")).containsExactlyInAnyOrder("racks", "threshold", "alcove");
	}

	@Test
	void knowledgeIsWhatWasSeenPlusWhatIsPerceivedNow() {
		assertThat(SceneKnowledge.knownZones(sacristy(Optional.of(List.of())), "threshold")).containsExactlyInAnyOrder("threshold", "racks");
		assertThat(SceneKnowledge.knownZones(sacristy(Optional.of(List.of("alcove"))), "threshold"))
				.containsExactlyInAnyOrder("threshold", "racks", "alcove");
		// Stored before seen zones were recorded: the whole scene is known.
		assertThat(SceneKnowledge.knownZones(sacristy(Optional.empty()), "threshold")).hasSize(4);
	}

	@Test
	void distancesFollowKnownPassagesOnlyAndNeverAHiddenOne() {
		Map<String, Integer> steps = SceneKnowledge.steps(sacristy(Optional.of(List.of("alcove"))), "threshold");

		assertThat(steps).containsEntry("threshold", 0).containsEntry("racks", 1).containsEntry("alcove", 2).doesNotContainKey("cellar");
		assertThat(SceneKnowledge.nextStepToward(sacristy(Optional.of(List.of("alcove"))), "threshold", "alcove")).contains("racks");
		assertThat(SceneKnowledge.nextStepToward(sacristy(Optional.empty()), "threshold", "cellar")).isEmpty();
	}

	@Test
	void seeingAddsToTheRecordOnlyForScenesThatKeepOne() {
		SceneState seen = sacristy(Optional.of(List.of())).seeing(List.of("threshold", "racks"));
		assertThat(seen.seenZones()).contains(List.of("threshold", "racks"));
		assertThat(sacristy(Optional.empty()).seeing(List.of("threshold"))).isEqualTo(sacristy(Optional.empty()));
	}
}
