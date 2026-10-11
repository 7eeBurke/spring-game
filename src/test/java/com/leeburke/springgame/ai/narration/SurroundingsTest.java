package com.leeburke.springgame.ai.narration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.AiJson;
import com.leeburke.springgame.ai.interpreter.InterpreterFixtures;
import com.leeburke.springgame.world.WorldFixtures;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneViewProjector;

class SurroundingsTest {

	private static Surroundings from(PlayerSceneView view, Set<String> fallen, Map<String, String> labels, String zone) {
		return Surroundings.of(view, NarrationNames.of(view, InterpreterFixtures.WORLD), fallen, labels, zone);
	}

	@Test
	void describesWhereYouAreWhereYouCanGoAndWhatIsInSight() {
		Surroundings here = from(InterpreterFixtures.view(), Set.of(), Map.of("north_door", "the way to the Ossuary"), "entrance");

		assertThat(here.describe()).isEqualTo("You are in the Nave Entrance. From here you can go to the Side Aisle. "
				+ "You see the Hollow Acolyte in the Side Aisle. Nearby: the Wooden Pew here and the Fire in the Side Aisle. "
				+ "The way to the Ossuary leaves from here.");
	}

	@Test
	void anUnlabelledExitIsOnlyAnUnexploredWay() {
		Surroundings fromAisle = from(InterpreterFixtures.view(), Set.of(), Map.of(), "aisle");

		assertThat(fromAisle.ways()).containsExactly(new Surroundings.Way(Surroundings.UNEXPLORED, "Nave Entrance", false));
		assertThat(fromAisle.describe()).contains("You see the Hollow Acolyte here.")
				.endsWith("An unexplored way leaves from the Nave Entrance.");
	}

	@Test
	void fallenCreaturesAreNotThreats() {
		Surroundings here = from(InterpreterFixtures.view(), Set.of("acolyte_1"), Map.of(), "entrance");

		assertThat(here.creatures()).extracting(Surroundings.Seen::fallen).containsExactly(true);
		assertThat(here.describe()).doesNotContain("Acolyte");
	}

	@Test
	void hiddenContentNeverAppears() {
		PlayerSceneView view = PlayerSceneViewProjector.project(WorldFixtures.richScene(), WorldFixtures.ENTRANCE,
				Set.of(WorldFixtures.ENTRANCE, WorldFixtures.AISLE, WorldFixtures.ALTAR));
		Surroundings here = from(view, Set.of(), Map.of(), WorldFixtures.ENTRANCE);

		String everything = AiJson.write(here) + here.describe();
		assertThat(everything).doesNotContainIgnoringCase("warden").doesNotContainIgnoringCase("relic")
				.doesNotContainIgnoringCase("secret").doesNotContainIgnoringCase("crypt").doesNotContainIgnoringCase("ghoul")
				.doesNotContainIgnoringCase("chest").doesNotContainIgnoringCase("tunnel");
		assertThat(here.describe()).startsWith("You are in the ");
	}

	@Test
	void aZoneThePlayerCannotSeeIsRejected() {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> from(InterpreterFixtures.view(), Set.of(), Map.of(), "crypt"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void identicalUnexploredWaysAreCountedNotRepeated() {
		Surroundings here = new Surroundings("Cloister Walk", List.of(), List.of(), List.of(), List.of(), List.of(
				new Surroundings.Way(Surroundings.UNEXPLORED, "Cloister Walk", true),
				new Surroundings.Way(Surroundings.UNEXPLORED, "Cloister Walk", true),
				new Surroundings.Way(Surroundings.UNEXPLORED, "Broken Arcade", false),
				new Surroundings.Way("the way to The Last Lantern", "Cloister Walk", true)));

		assertThat(here.describe()).isEqualTo("You are in the Cloister Walk. Two unexplored ways leave from here. "
				+ "An unexplored way leaves from the Broken Arcade. The way to The Last Lantern leaves from here.");
	}

	@Test
	void namesAreListedNaturally() {
		Surroundings many = new Surroundings("Nave", List.of("Aisle", "Apse", "Crossing"), List.of(), List.of(), List.of(), List.of());
		assertThat(many.describe()).isEqualTo("You are in the Nave. From here you can go to the Aisle, the Apse and the Crossing.");
	}
}
