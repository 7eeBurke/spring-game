package com.leeburke.springgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.world.view.PlayerSceneView;

/** How the player's words are matched to visible places (used by {@link RouteGrounding}). */
class DestinationCheckTest {

	private static final PlayerSceneView HUB = new PlayerSceneView("chapel_road",
			List.of(new PlayerSceneView.VisibleZone("chapel_road", "Chapel Road"), new PlayerSceneView.VisibleZone("lantern_hearth", "Lantern Hearth")),
			List.of(new PlayerSceneView.VisibleConnection("chapel_road", "lantern_hearth")),
			List.of(), List.of(), List.of(),
			List.of(new PlayerSceneView.KnownExit("road_to_chapel", "chapel_road"), new PlayerSceneView.KnownExit("hidden_name", "chapel_road")),
			List.of());

	@Test
	void placesAreTheVisibleZonesAndTheLabelledExits() {
		List<DestinationCheck.Place> places = DestinationCheck.places(HUB, Map.of("road_to_chapel", "the road to the Hollow Chapel"));

		assertThat(places).containsExactly(new DestinationCheck.Place("chapel_road", false, "Chapel Road"),
				new DestinationCheck.Place("lantern_hearth", false, "Lantern Hearth"),
				new DestinationCheck.Place("road_to_chapel", true, "the road to the Hollow Chapel"));
	}

	@Test
	void significantWordsAreWholeFoldedAndNotCommon() {
		assertThat(DestinationCheck.words("I go to the Hollow Chapel!")).containsExactlyInAnyOrder("hollow", "chapel");
		assertThat(DestinationCheck.words("Chapelle, côté")).containsExactlyInAnyOrder("chapelle", "cote");
		assertThat(DestinationCheck.words("I continue onward until I find something")).isEmpty();
		assertThat(DestinationCheck.words("Follow the road")).containsExactly("road");
	}
}
