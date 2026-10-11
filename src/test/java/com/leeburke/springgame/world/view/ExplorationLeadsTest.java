package com.leeburke.springgame.world.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/** What is left to explore, from what the player knows only. */
class ExplorationLeadsTest {

	/** The nave as known from its west end: entrance - aisle - {pews, apse}; the road back here, a way on in the apse. */
	private static PlayerSceneView nave(String here) {
		return new PlayerSceneView(here,
				List.of(new VisibleZone("nave_entrance", "Nave Entrance"), new VisibleZone("central_aisle", "Central Aisle"),
						new VisibleZone("collapsed_pews", "Collapsed Pews"), new VisibleZone("apse", "Apse")),
				List.of(new VisibleConnection("nave_entrance", "central_aisle"), new VisibleConnection("central_aisle", "collapsed_pews"),
						new VisibleConnection("central_aisle", "apse")),
				List.of(), List.of(), List.of(), List.of(new KnownExit("lantern_road", "nave_entrance"), new KnownExit("exit_1", "apse")),
				List.of());
	}

	@Test
	void anUnexploredWayFarOffIsNamedWithTheRouteToIt() {
		ExplorationLeads leads = ExplorationLeads.of(nave("nave_entrance"), Optional.of(List.of("nave_entrance")), Set.of("exit_1"));

		assertThat(leads.unexplored()).containsExactly(new ExplorationLeads.Way("exit_1", "apse", 2, Optional.of("central_aisle")));
		assertThat(leads.known()).extracting(ExplorationLeads.Way::exitId).containsExactly("lantern_road");
		assertThat(leads.unvisited()).extracting(ExplorationLeads.Place::zoneId)
				.containsExactly("central_aisle", "apse", "collapsed_pews");
		assertThat(leads.visited()).isEmpty();
		assertThat(leads.nothingKnownLeft()).isFalse();
	}

	@Test
	void visitedPlacesAreTold_AndWithNothingLeftItSaysOnlyThatNothingIsKnown() {
		ExplorationLeads leads = ExplorationLeads.of(nave("central_aisle"),
				Optional.of(List.of("nave_entrance", "central_aisle", "collapsed_pews", "apse")), Set.of());

		assertThat(leads.unexplored()).isEmpty();
		assertThat(leads.unvisited()).isEmpty();
		assertThat(leads.visited()).extracting(ExplorationLeads.Place::zoneId)
				.containsExactlyInAnyOrder("nave_entrance", "collapsed_pews", "apse");
		assertThat(leads.known()).hasSize(2);
		assertThat(leads.nothingKnownLeft()).isTrue();
	}

	@Test
	void whereVisitsWereNeverRecordedNothingIsClaimedAboutThem() {
		ExplorationLeads leads = ExplorationLeads.of(nave("apse"), Optional.empty(), Set.of("exit_1"));

		assertThat(leads.visitsRecorded()).isFalse();
		assertThat(leads.unvisited()).isEmpty();
		assertThat(leads.visited()).isEmpty();
		assertThat(leads.unexplored()).containsExactly(new ExplorationLeads.Way("exit_1", "apse", 0, Optional.empty()));
	}

	@Test
	void onlyWhatIsInTheKnownViewCanBeALead() {
		// The view holds only seen zones: the apse and its way on are not known yet from the west end.
		PlayerSceneView fromTheDoors = new PlayerSceneView("nave_entrance",
				List.of(new VisibleZone("nave_entrance", "Nave Entrance"), new VisibleZone("central_aisle", "Central Aisle")),
				List.of(new VisibleConnection("nave_entrance", "central_aisle")), List.of(), List.of(), List.of(),
				List.of(new KnownExit("lantern_road", "nave_entrance")), List.of());

		ExplorationLeads leads = ExplorationLeads.of(fromTheDoors, Optional.of(List.of("nave_entrance")), Set.of("exit_1", "secret"));

		assertThat(leads.unexplored()).isEmpty();
		assertThat(leads.unvisited()).extracting(ExplorationLeads.Place::zoneId).containsExactly("central_aisle");
		assertThat(leads.nothingKnownLeft()).as("an unvisited place beside is still a lead").isFalse();
	}
}
