package com.leeburke.springgame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.EvadeType;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.ParryContact;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.game.DestinationCheck.Place;
import com.leeburke.springgame.game.RouteGrounding.Ambiguous;
import com.leeburke.springgame.game.RouteGrounding.Grounded;
import com.leeburke.springgame.game.RouteGrounding.Result;
import com.leeburke.springgame.game.RouteGrounding.Threshold;
import com.leeburke.springgame.game.RouteGrounding.Unchanged;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * Route grounding keeps the player in charge of crossing into another scene. Readings are given as
 * the model might produce them, including wrong ones (an exit it marks EXPLICIT for "walk forward").
 */
class RouteGroundingTest {

	// --- Builders ---

	static MovePayload move(MovementType type, ActionTarget target) {
		return new MovePayload(type, target, RelativeGoal.NONE, ActionApproach.NORMAL);
	}

	static MovePayload toZone(String zone) {
		return move(MovementType.ADVANCE, new ActionTarget.ZoneTarget(zone, TargetSpecificity.EXPLICIT));
	}

	static MovePayload reposition(String zone) {
		return move(MovementType.REPOSITION, new ActionTarget.ZoneTarget(zone, TargetSpecificity.EXPLICIT));
	}

	static MovePayload throughExit(String exit) {
		return move(MovementType.ADVANCE, new ActionTarget.ExitTarget(exit, TargetSpecificity.EXPLICIT));
	}

	static MovePayload onward() {
		return move(MovementType.ADVANCE, ActionTarget.unspecified());
	}

	static ActionPayload.DefendPayload parry() {
		return new ActionPayload.DefendPayload(DefenseMethod.PARRY, EvadeType.UNSPECIFIED, ParryContact.UNSPECIFIED,
				ActionTarget.unspecified());
	}

	static ActionIntent and(ActionPayload... payloads) {
		return ActionFixtures.intent(payloads);
	}

	/** Where the journey goes once grounded (rewritten, or already right), as "kind:id" per move. */
	static List<String> route(Result result, ActionIntent read) {
		assertThat(result).isInstanceOfAny(Grounded.class, Unchanged.class);
		return moves(result instanceof Grounded g ? g.intent() : read);
	}

	static List<String> moves(ActionIntent intent) {
		return intent.steps().stream()
				.filter(s -> s.payload() instanceof MovePayload)
				.map(s -> switch (((MovePayload) s.payload()).target()) {
					case ActionTarget.ZoneTarget z -> "zone:" + z.zoneId();
					case ActionTarget.ExitTarget x -> "exit:" + x.exitId();
					default -> "none";
				}).toList();
	}

	static List<String> ids(List<Place> places) {
		return places.stream().map(Place::id).toList();
	}

	// --- The hub, as a new run sees it ---

	@Nested
	class TheHub {

		static final String HEARTH = "lantern_hearth";
		static final String ROAD = "chapel_road";
		static final String EXIT = "road_to_chapel";
		static final Map<String, String> LABELS = Map.of(EXIT, "the road to the Hollow Chapel");

		static PlayerSceneView at(String zone) {
			return new PlayerSceneView(zone, List.of(new VisibleZone(ROAD, "Chapel Road"), new VisibleZone(HEARTH, "Lantern Hearth")),
					List.of(new VisibleConnection(HEARTH, ROAD)), List.of(), List.of(), List.of(), List.of(new KnownExit(EXIT, ROAD)),
					List.of());
		}

		static Result ground(String input, String from, ActionIntent intent) {
			return RouteGrounding.ground(input, intent, at(from), LABELS, Set.of());
		}

		static List<String> go(String input, String from, ActionIntent intent) {
			return route(ground(input, from, intent), intent);
		}

		static final String PLAYTEST = "I walk forward, keeping an eye on my surroundings to see if I spot anything";

		// The playtest input.

		@ParameterizedTest
		@ValueSource(strings = { "onward", "exit" })
		void walkingForwardWhileLookingStopsAtTheRoadAndStillLooks(String reading) {
			ActionIntent read = and(reading.equals("exit") ? throughExit(EXIT) : onward(), ActionFixtures.search());

			Result result = ground(PLAYTEST, HEARTH, read);

			assertThat(route(result, read)).containsExactly("zone:" + ROAD); // one local step, never the road out
			assertThat(((Grounded) result).intent().steps()).extracting(s -> s.payload().getClass().getSimpleName())
					.containsExactly("MovePayload", "ObservePayload");
		}

		@Test
		void walkingForwardFromTheRoadNeverCrosses() {
			// Even read (wrongly) as the exit, with the model sure of it.
			assertThat(ground("I walk forward", ROAD, and(throughExit(EXIT)))).isInstanceOfSatisfying(Threshold.class,
					t -> assertThat(ids(t.ways())).containsExactly(EXIT));
			// With a look around as well, the player stays and looks.
			assertThat(go(PLAYTEST, ROAD, and(onward(), ActionFixtures.search()))).containsExactly("zone:" + ROAD);
		}

		@Test
		void aCautiousStepIsOneLocalMove() {
			assertThat(go("I cautiously advance", HEARTH, and(onward()))).containsExactly("zone:" + ROAD);
			assertThat(ground("I take a few steps ahead", HEARTH, and(toZone(ROAD)))).isInstanceOf(Unchanged.class);
		}

		// Approach is not entry.

		@Test
		void headingTowardTheChapelApproachesWithoutEntering() {
			// The model marks the exit as the target: the words still only approach.
			assertThat(go("I head toward the chapel", HEARTH, and(throughExit(EXIT)))).containsExactly("zone:" + ROAD);
			assertThat(go("I head toward the chapel, looking for its entrance", HEARTH,
					and(throughExit(EXIT), ActionFixtures.search()))).containsExactly("zone:" + ROAD);
			// Already at the road: nothing nearer to go to.
			assertThat(ground("I head toward the chapel", ROAD, and(throughExit(EXIT)))).isInstanceOf(Threshold.class);
		}

		@Test
		void mentioningTheWayIsNotTakingIt() {
			assertThat(go("Follow the road", HEARTH, and(throughExit(EXIT)))).containsExactly("zone:" + ROAD);
			assertThat(ground("Follow the road", ROAD, and(throughExit(EXIT)))).isInstanceOf(Threshold.class);
		}

		// Explicit entry.

		@Test
		void enteringTheChapelCrossesWithTheBoundedRoute() {
			assertThat(go("I enter the Hollow Chapel", ROAD, and(throughExit(EXIT)))).containsExactly("exit:" + EXIT);
			Result fromHearth = ground("I follow the road all the way into the chapel", HEARTH, and(throughExit(EXIT)));
			assertThat(route(fromHearth, null)).containsExactly("zone:" + ROAD, "exit:" + EXIT);
			assertThat(((Grounded) fromHearth).intent().steps()).extracting(ActionStep::relation)
					.containsExactly(StepRelation.START, StepRelation.IF_PREVIOUS_SUCCEEDS);
		}

		@Test
		void aClearlyAuthorizedEntryCorrectsAWrongReading() {
			// The real probe's reading: back to the hearth.
			assertThat(go("I enter the chapel", ROAD, and(toZone(HEARTH)))).containsExactly("exit:" + EXIT);
			// Read as the road zone: "the chapel" names no zone in full, so the entry stands.
			assertThat(go("I enter the chapel", HEARTH, and(toZone(ROAD)))).containsExactly("zone:" + ROAD, "exit:" + EXIT);
			// But entering the Chapel Road is just going to the road.
			assertThat(ground("I step onto the chapel road", HEARTH, and(reposition(ROAD)))).isInstanceOf(Unchanged.class);
		}

		@ParameterizedTest
		@ValueSource(strings = { "I go inside", "I step through the doorway" })
		void aContextualEntryWorksWithOneWayIn(String input) {
			assertThat(go(input, ROAD, and(throughExit(EXIT)))).containsExactly("exit:" + EXIT);
			assertThat(go(input, ROAD, and(onward()))).containsExactly("exit:" + EXIT);
		}

		@Test
		void anUnclearPhraseWithAnAuthorizedEntryIsResolved() {
			ActionIntent unclear = ActionFixtures.intent(Optional.empty(),
					List.of(new UnresolvedReference(Optional.of("s1"), "the old door")), onward());

			Result result = ground("I go into the chapel through the old door", ROAD, unclear);

			assertThat(route(result, unclear)).containsExactly("exit:" + EXIT);
			assertThat(((Grounded) result).intent().unresolvedReferences()).isEmpty();
		}

		@Test
		void withNoStepsAtAllOnlyANamedOrAuthorizedPlaceIsGrounded() {
			assertThat(route(RouteGrounding.groundNamedPlace("I enter the chapel", at(ROAD), LABELS, Set.of()), null))
					.containsExactly("exit:" + EXIT);
			assertThat(RouteGrounding.groundNamedPlace("I continue onward until I find something", at(ROAD), LABELS, Set.of()))
					.isInstanceOf(Unchanged.class);
		}

		// Going back.

		@Test
		void goingBackToANamedPlaceIsHonoured() {
			assertThat(ground("I go back to the hearth", ROAD, and(reposition(HEARTH)))).isInstanceOf(Unchanged.class);
			assertThat(go("I turn back toward the Last Lantern", ROAD, and(throughExit(EXIT)))).containsExactly("zone:" + HEARTH);
		}

		// Safety.

		@Test
		void aLeadingDefenseStaysFirstAndTheMoveStaysLocal() {
			ActionIntent intent = ActionFixtures.intent(Optional.of("attack_1"), List.of(), parry(), onward(), ActionFixtures.search());

			Result result = ground("I parry, then walk forward and look around", HEARTH, intent);

			List<ActionStep> steps = ((Grounded) result).intent().steps();
			assertThat(steps).extracting(s -> s.payload().getClass().getSimpleName())
					.containsExactly("DefendPayload", "MovePayload", "ObservePayload");
			assertThat(moves(((Grounded) result).intent())).containsExactly("zone:" + ROAD);
			assertThat(((Grounded) result).intent().responseToAttack()).contains("attack_1");
		}

		@Test
		void otherIntentsAreNotTouched() {
			assertThat(ground("I look around", ROAD, and(ActionFixtures.search()))).isInstanceOf(Unchanged.class);
			assertThat(ground("I go to the hearth and back to the road", ROAD, and(reposition(HEARTH), reposition(ROAD))))
					.isInstanceOf(Unchanged.class);
		}

		@Test
		void anIdleMoveIsLeftForTheTurnRules() {
			assertThat(ground("I wait on the chapel road", ROAD, and(reposition(ROAD)))).isInstanceOf(Unchanged.class);
		}
	}

	// --- A region scene: a way back, two ways on, an unconnected zone ---

	@Nested
	class ARegionScene {

		static final Map<String, String> LABELS = Map.of(
				"back", "the way to The Last Lantern",
				"on_nave", "an unexplored way",
				"on_apse", "an unexplored way",
				"ossuary", "the way to the Ossuary");

		/** Vestry Door (with the way back) - Bone Nave - Bone Apse; ways on from the nave and the apse. */
		static PlayerSceneView at(String zone) {
			return new PlayerSceneView(zone,
					List.of(new VisibleZone("entrance", "Vestry Door"), new VisibleZone("nave", "Bone Nave"), new VisibleZone("apse", "Bone Apse")),
					List.of(new VisibleConnection("entrance", "nave"), new VisibleConnection("nave", "apse")),
					List.of(), List.of(), List.of(),
					List.of(new KnownExit("back", "entrance"), new KnownExit("on_nave", "nave"), new KnownExit("on_apse", "apse"),
							new KnownExit("ossuary", "apse")),
					List.of());
		}

		static final Set<String> BACK = Set.of("back", "ossuary");

		static Result ground(String input, String from, ActionIntent intent) {
			return RouteGrounding.ground(input, intent, at(from), LABELS, BACK);
		}

		static List<String> go(String input, String from, ActionIntent intent) {
			return route(ground(input, from, intent), intent);
		}

		@Test
		void headingBackToTheLastLanternRetreats() {
			assertThat(go("I head back to the Last Lantern", "entrance", and(throughExit("back")))).containsExactly("exit:back");
			assertThat(go("I return to the last lantern", "entrance", and(throughExit("on_nave")))).containsExactly("exit:back");
			// RETREAT through the way back is grounded like any journey.
			assertThat(go("I retreat back to the Last Lantern", "entrance",
					and(move(MovementType.RETREAT, new ActionTarget.ExitTarget("back", TargetSpecificity.EXPLICIT)))))
					.containsExactly("exit:back");
		}

		@Test
		void mentioningTheWayBackIsNotTakingIt() {
			// Moving toward it without going back is an approach; it leaves from here, so the player is asked.
			assertThat(ground("I walk toward the Last Lantern", "entrance", and(throughExit("back")))).isInstanceOf(Threshold.class);
		}

		@Test
		void severalWaysOnGiveChoicesNotAGuess() {
			assertThat(ground("I walk forward", "nave", and(onward()))).isInstanceOfSatisfying(Ambiguous.class,
					a -> assertThat(ids(a.choices())).containsExactly("on_nave", "on_apse"));
		}

		@Test
		void ambiguousEntrancesAreClarified() {
			// Two ways leave from the apse: "the doorway" could be either, whatever the model picked.
			assertThat(ground("I step through the doorway", "apse", and(throughExit("on_apse"))))
					.isInstanceOfSatisfying(Ambiguous.class, a -> assertThat(ids(a.choices())).containsExactlyInAnyOrder("on_apse", "ossuary"));
		}

		@Test
		void noPathIsInventedAndNamedPlacesAreApproached() {
			// The Ossuary (discovered) is two zones away: approaching it is a move toward its zone, which the engine fails.
			assertThat(go("I head for the ossuary", "entrance", and(onward()))).containsExactly("zone:apse");
		}

		@Test
		void placesTheWordsShareAreAmbiguous() {
			assertThat(ground("I go to the bone room", "entrance", and(onward())))
					.isInstanceOfSatisfying(Ambiguous.class, a -> assertThat(a.choices()).extracting(Place::name)
							.containsExactlyInAnyOrder("Bone Nave", "Bone Apse"));
		}

		@Test
		void onlyVisiblePlacesAreEverChosen() {
			Result result = ground("I go down into the secret crypt", "entrance", and(onward()));

			// "Into" with nothing named: only ways within reach are candidates, never anything unseen.
			assertThat(result).isInstanceOfSatisfying(Grounded.class, g -> assertThat(moves(g.intent())).allMatch(
					m -> m.equals("exit:back") || m.equals("zone:nave") || m.equals("exit:on_nave")));
		}
	}

	// --- Going to an object: incremental, bounded, never through a way out, stopping at danger ---

	@Nested
	class TowardAnObject {

		/** threshold - racks - alcove; the crate in the alcove, two steps away; a way out at the threshold. */
		static PlayerSceneView sacristy(List<PlayerSceneView.VisibleEntity> creatures) {
			return new PlayerSceneView("threshold",
					List.of(new VisibleZone("threshold", "Vestry Threshold"), new VisibleZone("racks", "Vestment Racks"),
							new VisibleZone("alcove", "Narrow Alcove")),
					List.of(new VisibleConnection("threshold", "racks"), new VisibleConnection("racks", "alcove")),
					creatures, List.of(new PlayerSceneView.VisibleObject("crate", "CRATE", "alcove")), List.of(),
					List.of(new KnownExit("back", "threshold")), List.of());
		}

		static MovePayload toCrate(MovementType type) {
			return move(type, new ActionTarget.ObjectTarget("crate", TargetSpecificity.EXPLICIT));
		}

		static ActionPayload openCrate() {
			return new ActionPayload.InteractPayload(com.leeburke.springgame.action.InteractionKind.OPEN,
					new ActionTarget.ObjectTarget("crate", TargetSpecificity.EXPLICIT), Optional.empty(), ActionApproach.NORMAL);
		}

		static Result ground(String input, PlayerSceneView view, ActionIntent intent) {
			return RouteGrounding.ground(input, intent, view, Map.of("back", "the way to The Last Lantern"), Set.of("back"), Set.of());
		}

		@Test
		void movingTowardItIsOneStepAndTheOpenThenWaits() {
			ActionIntent read = and(toCrate(MovementType.CLOSE_DISTANCE), openCrate());

			Result result = ground("I move toward the crate and see if I can open it", sacristy(List.of()), read);

			assertThat(route(result, read)).containsExactly("zone:racks");
			assertThat(((Grounded) result).intent().steps()).extracting(s -> s.payload().getClass().getSimpleName())
					.containsExactly("MovePayload", "InteractPayload"); // the open stays; the engine finds it out of reach
		}

		@Test
		void goingAllTheWayToItIsAtMostTwoSteps() {
			ActionIntent read = and(toCrate(MovementType.ADVANCE), openCrate());

			assertThat(route(ground("I go all the way to the crate and open it", sacristy(List.of()), read), read))
					.containsExactly("zone:racks", "zone:alcove");
		}

		@Test
		void theWalkStopsWhereSomethingLivingStands() {
			ActionIntent read = and(toCrate(MovementType.ADVANCE));
			PlayerSceneView guarded = sacristy(List.of(new PlayerSceneView.VisibleEntity("penitent", "ASHBOUND_PENITENT", "racks")));

			assertThat(route(ground("I go to the crate", guarded, read), read)).containsExactly("zone:racks");
			// A fallen one does not stop it.
			Result past = RouteGrounding.ground("I go to the crate", read, guarded, Map.of(), Set.of(), Set.of("penitent"));
			assertThat(route(past, read)).containsExactly("zone:racks", "zone:alcove");
		}

		@Test
		void examiningItFromHereMovesNothing() {
			ActionIntent read = and(new ActionPayload.ObservePayload(com.leeburke.springgame.action.ObservationKind.INSPECT,
					new ActionTarget.ObjectTarget("crate", TargetSpecificity.EXPLICIT)));

			assertThat(ground("I examine the crate from here", sacristy(List.of()), read)).isInstanceOf(Unchanged.class);
		}

		@Test
		void besideItThereIsNothingToWalk() {
			PlayerSceneView beside = new PlayerSceneView("alcove", sacristy(List.of()).zones(), sacristy(List.of()).connections(),
					List.of(), sacristy(List.of()).objects(), List.of(), sacristy(List.of()).exits(), List.of());

			assertThat(ground("I go to the crate", beside, and(toCrate(MovementType.ADVANCE)))).isInstanceOf(Unchanged.class);
		}
	}
}
