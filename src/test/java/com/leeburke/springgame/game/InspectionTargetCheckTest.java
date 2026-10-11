package com.leeburke.springgame.game;

import static com.leeburke.springgame.enemy.EnemyFixtures.CONTENT;
import static com.leeburke.springgame.enemy.EnemyFixtures.WORLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.ai.interpreter.InterpretationContextBuilder;
import com.leeburke.springgame.ai.interpreter.InterpretationSetup;
import com.leeburke.springgame.character.PlayerCharacterGenerator;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.PlayerStatGenerator;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleContainer;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * The playtest's Bell Passage: standing in the cracked belfry, with the emptied crate back in the rope
 * gallery. A close look the interpreter aims at the crate is kept only when the player named it; a
 * feature of a place in sight is that place; anything else is unclear, never a substitute.
 */
class InspectionTargetCheckTest {

	private static final PlayerCharacterState PLAYER = PlayerCharacterState.from(
			new PlayerCharacterGenerator(CONTENT, new PlayerStatGenerator()).generate(TurnRandom.character(1)));

	/** The Bell Passage as known from the cracked belfry (two ways leave from it, as in the playtest). */
	private static InterpretationSetup atTheBelfry() {
		PlayerSceneView view = new PlayerSceneView("cracked_belfry",
				List.of(new VisibleZone("bell_landing", "Bell Landing"), new VisibleZone("rope_gallery", "Rope Gallery"),
						new VisibleZone("cracked_belfry", "Cracked Belfry")),
				List.of(new VisibleConnection("bell_landing", "rope_gallery"), new VisibleConnection("rope_gallery", "cracked_belfry")),
				List.of(), List.of(new VisibleObject("first_find", "CRATE", "rope_gallery")), List.of(),
				List.of(new KnownExit("exit_1", "cracked_belfry"), new KnownExit("exit_2", "cracked_belfry"),
						new KnownExit("lantern_road", "bell_landing")),
				List.of(), List.of(new VisibleContainer("first_find", true, List.of())));
		return new InterpretationContextBuilder(WORLD, code -> code).build(view, PLAYER, List.of(), Set.of(),
				Map.of("exit_1", "an unexplored way", "exit_2", "an unexplored way", "lantern_road", "the way to The Last Lantern"),
				"BELL_PASSAGE");
	}

	private static ActionIntent inspect(ActionTarget target) {
		return new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, Optional.empty(),
				List.of(new ActionStep("s1", 1, StepRelation.START, new ActionPayload.ObservePayload(ObservationKind.INSPECT, target))),
				InterpretationConfidence.HIGH, List.of());
	}

	private static final ActionIntent THE_CRATE = inspect(new ActionTarget.ObjectTarget("first_find", TargetSpecificity.EXPLICIT));

	private static ActionTarget lookedAt(InspectionTargetCheck.Result result) {
		return ((ActionPayload.ObservePayload) ((InspectionTargetCheck.Retargeted) result).intent().steps().getFirst().payload()).target();
	}

	@Test
	void theHoleIsAFeatureOfTheBelfryNotTheCrateInTheNextPlace() {
		InspectionTargetCheck.Result result = InspectionTargetCheck.check("I see if the hole is safe to drop down", THE_CRATE, atTheBelfry());

		assertThat(result).isInstanceOf(InspectionTargetCheck.Retargeted.class);
		assertThat(lookedAt(result)).isEqualTo(new ActionTarget.ZoneTarget("cracked_belfry", TargetSpecificity.INFERRED));
	}

	@Test
	void aStrayPronounDoesNotKeepTheWrongThingWhenAFeatureIsNamed() {
		assertThat(lookedAt(InspectionTargetCheck.check("Is it safe to drop down the hole?", THE_CRATE, atTheBelfry())))
				.isEqualTo(new ActionTarget.ZoneTarget("cracked_belfry", TargetSpecificity.INFERRED));
		assertThat(lookedAt(InspectionTargetCheck.check("I peer into the crack in the floor", THE_CRATE, atTheBelfry())))
				.isEqualTo(new ActionTarget.ZoneTarget("cracked_belfry", TargetSpecificity.INFERRED));
	}

	@Test
	void aNamedThingOrAPlainPronounIsKept() {
		assertThat(InspectionTargetCheck.check("I look into the empty crate again", THE_CRATE, atTheBelfry()))
				.isInstanceOf(InspectionTargetCheck.Unchanged.class);
		assertThat(InspectionTargetCheck.check("I take a closer look at it", THE_CRATE, atTheBelfry()))
				.isInstanceOf(InspectionTargetCheck.Unchanged.class);
	}

	@Test
	void somethingNoKnownPlaceOrThingMatchesIsUnclearNeverSubstituted() {
		assertThat(InspectionTargetCheck.check("I study the strange glyphs", THE_CRATE, atTheBelfry()))
				.isInstanceOf(InspectionTargetCheck.Unclear.class);
	}

	@Test
	void looksAtPlacesAndWaysAndOtherActionsAreNotTouched() {
		ActionIntent atThePlace = inspect(new ActionTarget.ZoneTarget("cracked_belfry", TargetSpecificity.EXPLICIT));
		assertThat(InspectionTargetCheck.check("I see if the hole is safe", atThePlace, atTheBelfry()))
				.isInstanceOf(InspectionTargetCheck.Unchanged.class);
		ActionIntent search = new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, Optional.empty(),
				List.of(new ActionStep("s1", 1, StepRelation.START, new ActionPayload.ObservePayload(ObservationKind.SEARCH,
						ActionTarget.unspecified()))), InterpretationConfidence.HIGH, List.of());
		assertThat(InspectionTargetCheck.check("I look around", search, atTheBelfry())).isInstanceOf(InspectionTargetCheck.Unchanged.class);
	}

	@Test
	void theInterpreterNowSeesTheBelfrysDescriptionAndHowItsWaysLook() {
		var context = atTheBelfry().context();
		assertThat(context.zones()).anySatisfy(z -> assertThat(z.description()).contains("crack wide enough to show the drop below"));
		assertThat(context.exits()).extracting(e -> e.passage())
				.contains("a hatch in the belfry wall", "a ladder down through the split floor");
	}
}
