package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.ai.narration.PlaceDescriber;
import com.leeburke.springgame.content.world.WorldContentLoader;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/** Refusal hints say where things are in the world's words, and only what the player knows. */
class HintWriterTest {

	/** At the sacristy's threshold, with the racks beside it known and a crate there; the alcove not yet seen. */
	private static HintWriter atTheThreshold() {
		PlayerSceneView known = new PlayerSceneView("vestry_door",
				List.of(new VisibleZone("vestry_door", "Vestry Threshold"), new VisibleZone("vestment_racks", "Vestment Racks")),
				List.of(new VisibleConnection("vestry_door", "vestment_racks")), List.of(),
				List.of(new VisibleObject("sacristy_crate", "CRATE", "vestment_racks")), List.of(), List.of(new KnownExit("back", "vestry_door")),
				List.of());
		PlaceDescriber places = new PlaceDescriber(WorldContentLoader.loadBundled(), "SACRISTY", known, Set.of(),
				Map.of("back", "the way to The Last Lantern"), code -> "Bandage");
		return new HintWriter(places, "vestry_door");
	}

	@Test
	void somethingOutOfReachIsPlacedInTheWorldsWordsWithWhatToDo() {
		String hint = atTheThreshold().outOfReach("sacristy_crate");

		assertThat(hint).startsWith("The crate is in ").endsWith(", out of reach from here. Go there first.");
		assertThat(hint).doesNotContain("Vestment Racks").doesNotContain("sacristy_crate").doesNotContain("vestment_racks");
	}

	@Test
	void somethingThePlayerHasNotSeenRevealsNothing() {
		// An object in the unseen alcove, or one that does not exist: no name, no place.
		assertThat(atTheThreshold().outOfReach("alcove_chain")).isEqualTo("That is out of reach from here.");
	}
}
