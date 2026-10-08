package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ACOLYTE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.related;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.slash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.world.view.PlayerSceneView;

/** The Stage 14 extension of Stage 11: leaving through an exit, and targets felled within one intent. */
class ExitAndDefeatResolutionTest {

	private final Setup setup = new Setup();

	private static MovePayload throughExit() {
		return new MovePayload(MovementType.ADVANCE, new ActionTarget.ExitTarget("north_door", TargetSpecificity.EXPLICIT),
				RelativeGoal.NONE, ActionApproach.NORMAL);
	}

	@Test
	void advancingThroughAnExitInTheCurrentZoneLeavesTheScene() {
		FixedRolls rolls = new FixedRolls();
		StepOutcome step = setup.resolve(intent(throughExit()), rolls).steps().getFirst();

		assertThat(step.status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.check()).isEmpty();
		assertThat(step.result()).contains(new StepResult.ExitResult("north_door"));
		assertThat(step.effects()).containsExactly(new OutcomeEffect.LeftScene("north_door"));
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void anExitInAnotherZoneIsNotReached() {
		PlayerSceneView view = ResolutionFixtures.view();
		setup.view = new PlayerSceneView("aisle", view.zones(), view.connections(), view.entities(), view.objects(),
				view.hazards(), view.exits(), view.discoveredFacts());
		setup.location = ResolutionFixtures.location("aisle");

		StepOutcome step = setup.resolve(intent(throughExit()), new FixedRolls()).steps().getFirst();

		assertThat(step.success()).contains(StepSuccess.FAILURE);
		assertThat(step.result()).contains(new StepResult.ExitResult("north_door"));
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void stepsAfterLeavingAreCancelled() {
		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(), List.of(StepRelation.THEN), throughExit(), slash()),
				new FixedRolls());

		assertThat(outcome.steps().get(1).status()).isEqualTo(StepStatus.CANCELLED);
		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.LEFT_SCENE);
		assertThat(outcome.effects()).containsExactly(new OutcomeEffect.LeftScene("north_door"));
	}

	@Test
	void anAttackOnATargetFelledEarlierInTheIntentIsCancelledWithoutARoll() {
		setup.hitPoints.put(ACOLYTE, 1);
		FixedRolls rolls = new FixedRolls(20);

		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(), List.of(StepRelation.THEN), slash(), slash()), rolls);

		assertThat(outcome.steps().getFirst().effects()).anyMatch(OutcomeEffect.TargetDamaged.class::isInstance);
		assertThat(outcome.steps().get(1).status()).isEqualTo(StepStatus.CANCELLED);
		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.TARGET_DEFEATED);
		assertThat(rolls.drawn()).isEqualTo(1);
	}

	@Test
	void anAttackOnATargetAlreadyAtZeroIsCancelled() {
		setup.hitPoints.put(ACOLYTE, 0);
		FixedRolls rolls = new FixedRolls();

		StepOutcome step = setup.resolve(intent(slash()), rolls).steps().getFirst();

		assertThat(step.cancellation()).contains(CancellationReason.TARGET_DEFEATED);
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void untrackedTargetsAreNeverTreatedAsFelled() {
		FixedRolls rolls = new FixedRolls(20, 20);

		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(), List.of(StepRelation.THEN), slash(), slash()), rolls);

		assertThat(outcome.steps()).allMatch(s -> s.status() == StepStatus.RESOLVED);
	}

	@Test
	void targetHitPointsCannotBeNegative() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ActionResolutionContext(
				ResolutionFixtures.player(20, PlayerBody.healthy()), ResolutionFixtures.scene(),
				ResolutionFixtures.location("entrance"), ResolutionFixtures.references(), Map.of(), Map.of(), Map.of(ACOLYTE, -1)));
	}
}
