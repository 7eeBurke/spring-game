package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.hold;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.moveTo;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;

class MovementResolutionTest {

	private final Setup setup = new Setup();
	private final FixedRolls rolls = new FixedRolls();

	private StepOutcome move(MovePayload payload) {
		return setup.resolve(intent(payload), rolls).steps().getFirst();
	}

	@Test
	void moveThroughAKnownConnectionMovesThePlayer() {
		StepOutcome step = move(moveTo("aisle"));

		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.check()).isEmpty();
		assertThat(step.result()).contains(new StepResult.MovementResult("entrance", "aisle", true));
		assertThat(step.effects()).containsExactly(new OutcomeEffect.PlayerMoved("entrance", "aisle"));
	}

	@Test
	void advanceToAConnectedZoneAlsoMoves() {
		StepOutcome step = move(new MovePayload(MovementType.ADVANCE,
				new ActionTarget.ZoneTarget("aisle", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL));

		assertThat(step.effects()).containsExactly(new OutcomeEffect.PlayerMoved("entrance", "aisle"));
	}

	@Test
	void moveToTheCurrentZoneSucceedsWithoutMoving() {
		StepOutcome step = move(moveTo("entrance"));

		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.result()).contains(new StepResult.MovementResult("entrance", "entrance", false));
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void zoneJoinedOnlyByAHiddenConnectionFailsWithoutRevealingIt() {
		ResolvedOutcome outcome = setup.resolve(intent(moveTo("vestry")), rolls);
		StepOutcome step = outcome.steps().getFirst();

		assertThat(step.status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(step.success()).contains(StepSuccess.FAILURE);
		assertThat(step.result()).contains(new StepResult.MovementResult("entrance", "vestry", false));
		assertThat(step.effects()).isEmpty();
		assertThat(outcome.overall()).isEqualTo(OverallResult.FAILURE);
		assertThat(outcome.toString()).doesNotContain("c3").doesNotContain("crypt");
	}

	@Test
	void holdPositionSucceedsWithoutMoving() {
		StepOutcome step = move(hold());

		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.result()).contains(new StepResult.MovementResult("entrance", "entrance", false));
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void unsupportedMovementIsUnavailable() {
		MovePayload[] unsupported = {
				new MovePayload(MovementType.CLOSE_DISTANCE, acolyte(), RelativeGoal.NONE, ActionApproach.NORMAL),
				new MovePayload(MovementType.RETREAT, ActionTarget.unspecified(), RelativeGoal.NONE, ActionApproach.NORMAL),
				new MovePayload(MovementType.REPOSITION, ActionTarget.unspecified(), RelativeGoal.COVER, ActionApproach.NORMAL),
				new MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget("aisle", TargetSpecificity.EXPLICIT),
						RelativeGoal.COVER, ActionApproach.NORMAL),
				new MovePayload(MovementType.CLIMB, ActionTarget.unspecified(), RelativeGoal.NONE, ActionApproach.NORMAL) };

		for (MovePayload payload : unsupported) {
			StepOutcome step = move(payload);
			assertThat(step.unavailable()).as(payload.toString()).contains(UnavailableReason.ACTION_NOT_IMPLEMENTED);
			assertThat(step.effects()).isEmpty();
		}
	}

	@Test
	void movementNeverDrawsARoll() {
		move(moveTo("aisle"));
		move(moveTo("vestry"));
		move(hold());

		assertThat(rolls.drawn()).isZero();
	}
}
