package com.leeburke.springgame.game.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.ai.narration.AttemptedAction;
import com.leeburke.springgame.ai.narration.NarrationFact;
import com.leeburke.springgame.ai.narration.Perception;

/**
 * Turns stored before the narration upgrade still read with the production reader for stored turn
 * documents, and the new facts round-trip. A chronicle never fails on an older turn.
 */
class StoredFactCompatibilityTest {

	private final TurnJson json = new TurnJson();

	private static final String ATTEMPT = "{\"using\":null,\"action\":\"INTERACT\",\"manner\":\"OPEN\",\"target\":\"Crate\",\"purpose\":null,"
			+ "\"approach\":\"NORMAL\",\"bodyPart\":null,\"template\":null,\"targetKind\":\"OBJECT\",\"spokenWords\":null}";

	@Test
	void factsStoredBeforeTheUpgradeStillRead() {
		NarrationFact opened = json.read("{\"fact\":\"ContainerOpened\",\"step\":1,\"attempt\":" + ATTEMPT
				+ ",\"name\":\"Crate\",\"contents\":[\"Restorative Salve\"],\"alreadyOpen\":false}", NarrationFact.class);
		NarrationFact taken = json.read("{\"fact\":\"ItemTaken\",\"step\":1,\"attempt\":" + ATTEMPT
				+ ",\"item\":\"Restorative Salve\",\"from\":\"Crate\"}", NarrationFact.class);

		assertThat(opened).isInstanceOfSatisfying(NarrationFact.ContainerOpened.class,
				o -> assertThat(o.contents()).containsExactly("Restorative Salve"));
		assertThat(taken).isInstanceOf(NarrationFact.ItemTaken.class);

		// A close look at a place, and a ways answer whose perception still listed its ways, as stored before.
		NarrationFact place = json.read("{\"fact\":\"Inspected\",\"step\":2,\"attempt\":" + ATTEMPT + ",\"name\":\"the cracked belfry\","
				+ "\"description\":\"The belfry floor.\",\"state\":null,\"where\":\"here\",\"inSight\":true}", NarrationFact.class);
		NarrationFact ways = json.read("{\"fact\":\"SoughtWays\",\"step\":1,\"attempt\":" + ATTEMPT + ",\"perception\":{\"here\":"
				+ "{\"label\":\"Rope Gallery\",\"phrase\":\"the rope gallery\",\"description\":\"\"},\"beside\":[],\"things\":[],"
				+ "\"hazards\":[],\"creatures\":[],\"ways\":[{\"leadsTo\":\"an unexplored way\",\"passage\":\"a hatch in the belfry wall\","
				+ "\"where\":\"the cracked belfry\",\"here\":false}]},\"unexplored\":[],\"unvisited\":[],\"visited\":[],\"known\":[],"
				+ "\"visitsKnown\":true,\"nothingKnownLeft\":true}", NarrationFact.class);
		assertThat(place).isInstanceOf(NarrationFact.Inspected.class);
		assertThat(ways).isInstanceOf(NarrationFact.SoughtWays.class);
	}

	@Test
	void theNewFactsRoundTrip() {
		AttemptedAction open = new AttemptedAction(ActionType.INTERACT, Optional.of("OPEN"), Optional.empty(), Optional.empty(),
				Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		NarrationFact opened = new NarrationFact.OpenedContainer(1, open, "Crate",
				List.of(new NarrationFact.Found("Restorative Salve", "A small stoppered clay vial of thick, bitter-smelling ointment.")), false);
		NarrationFact unchanged = new NarrationFact.Perceived(2, open, new Perception(
				Optional.of(new Perception.PlaceRef("Central Aisle", "the central aisle", "")), List.of(), List.of(), List.of(), List.of(),
				List.of()), true);

		assertThat(json.read(json.write(opened), NarrationFact.class)).isEqualTo(opened);
		assertThat(json.read(json.write(unchanged), NarrationFact.class)).isEqualTo(unchanged);
	}
}
