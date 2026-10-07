package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ATTACK;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.evade;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.hold;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.moveTo;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.player;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.related;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.slash;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;

/** Slash rolls against DC 12 with +1: 16 critical, 11 success, 7 partial, 6 failure. */
class MultiStepResolutionTest {

	private final Setup setup = new Setup();

	private ResolvedOutcome twoSlashes(StepRelation relation, int... faces) {
		return setup.resolve(related(Optional.empty(), List.of(relation), slash(), slash()), new FixedRolls(faces));
	}

	@ParameterizedTest
	@ValueSource(ints = { 11, 6 })
	void thenRunsAfterSuccessOrFailure(int firstFace) {
		ResolvedOutcome outcome = twoSlashes(StepRelation.THEN, firstFace, 11);

		assertThat(outcome.steps()).allMatch(step -> step.status() == StepStatus.RESOLVED);
		assertThat(outcome.metadata().rollsConsumed()).isEqualTo(2);
	}

	@ParameterizedTest
	@ValueSource(ints = { 16, 11 })
	void conditionalRunsAfterCriticalOrSuccess(int firstFace) {
		ResolvedOutcome outcome = twoSlashes(StepRelation.IF_PREVIOUS_SUCCEEDS, firstFace, 11);

		assertThat(outcome.steps().get(1).status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(outcome.overall()).isEqualTo(OverallResult.COMPLETE_SUCCESS);
	}

	@Test
	void conditionalIsCancelledAfterPartialSuccess() {
		FixedRolls rolls = new FixedRolls(7);
		ResolvedOutcome outcome = setup.resolve(
				related(Optional.empty(), List.of(StepRelation.IF_PREVIOUS_SUCCEEDS), slash(), slash()), rolls);

		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
		assertThat(outcome.overall()).isEqualTo(OverallResult.PARTIAL_SUCCESS);
		assertThat(rolls.drawn()).isEqualTo(1);
	}

	@Test
	void conditionalIsCancelledAfterFailure() {
		ResolvedOutcome outcome = twoSlashes(StepRelation.IF_PREVIOUS_SUCCEEDS, 6);

		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
		assertThat(outcome.overall()).isEqualTo(OverallResult.FAILURE);
		assertThat(outcome.metadata().rollsConsumed()).isEqualTo(1);
	}

	@Test
	void conditionalRunsAfterAnAutomaticSuccess() {
		ResolvedOutcome outcome = setup.resolve(
				related(Optional.empty(), List.of(StepRelation.IF_PREVIOUS_SUCCEEDS), hold(), slash()), new FixedRolls(11));

		assertThat(outcome.steps().get(1).status()).isEqualTo(StepStatus.RESOLVED);
	}

	@Test
	void conditionalIsCancelledAfterAnUnavailableStep() {
		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(), List.of(StepRelation.IF_PREVIOUS_SUCCEEDS),
				ActionFixtures.search(), slash()), new FixedRolls());

		assertThat(outcome.steps().get(1).cancellation()).contains(CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
		assertThat(outcome.overall()).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
	}

	@Test
	void conditionalIsCancelledAfterACancelledStep() {
		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(),
				List.of(StepRelation.IF_PREVIOUS_SUCCEEDS, StepRelation.IF_PREVIOUS_SUCCEEDS), slash(), slash(), slash()),
				new FixedRolls(6));

		assertThat(outcome.steps().get(2).cancellation()).contains(CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
	}

	@Test
	void whileStepIsUnavailableAndDrawsNothing() {
		FixedRolls rolls = new FixedRolls(11);
		ResolvedOutcome outcome = setup.resolve(related(Optional.empty(), List.of(StepRelation.WHILE), slash(), slash()), rolls);

		assertThat(outcome.steps().get(1).unavailable()).contains(UnavailableReason.SIMULTANEOUS_ACTION);
		assertThat(outcome.overall()).isEqualTo(OverallResult.PARTIAL_SUCCESS);
		assertThat(rolls.drawn()).isEqualTo(1);
	}

	@Test
	void damageReachingCurrentHpCancelsLaterStepsAsPlayerDown() {
		setup.player = player(3, PlayerBody.healthy());
		FixedRolls rolls = new FixedRolls(6);
		ResolvedOutcome outcome = setup.resolve(
				related(Optional.of(ATTACK), List.of(StepRelation.THEN, StepRelation.THEN), evade(), slash(), moveTo("aisle")),
				rolls);

		assertThat(((OutcomeEffect.PlayerDamaged) outcome.steps().getFirst().effects().getFirst()).hpDamage())
				.isGreaterThanOrEqualTo(3);
		assertThat(outcome.steps().subList(1, 3)).allMatch(step -> step.cancellation().equals(
				Optional.of(CancellationReason.PLAYER_DOWN)));
		assertThat(outcome.overall()).isEqualTo(OverallResult.INTERRUPTED);
		assertThat(rolls.drawn()).isEqualTo(1);
	}

	@Test
	void damageBelowCurrentHpDoesNotInterrupt() {
		ResolvedOutcome outcome = setup.resolve(
				related(Optional.of(ATTACK), List.of(StepRelation.THEN), evade(), slash()), new FixedRolls(6, 11));

		assertThat(outcome.steps().get(1).status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(outcome.overall()).isEqualTo(OverallResult.PARTIAL_SUCCESS);
	}

	@Test
	void playerAlreadyDownCancelsEveryStepWithoutRolling() {
		setup.player = player(0, PlayerBody.healthy());
		FixedRolls rolls = new FixedRolls();
		ActionPayload[] steps = { slash(), hold(), ActionFixtures.search() };
		ResolvedOutcome outcome = setup.resolve(intent(steps), rolls);

		assertThat(outcome.steps()).hasSize(3).allMatch(step -> step.status() == StepStatus.CANCELLED
				&& step.cancellation().equals(Optional.of(CancellationReason.PLAYER_DOWN)));
		assertThat(outcome.overall()).isEqualTo(OverallResult.INTERRUPTED);
		assertThat(outcome.metadata().rollsConsumed()).isZero();
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void rollsAreConsumedInStepOrder() {
		ResolvedOutcome outcome = twoSlashes(StepRelation.THEN, 16, 6);

		assertThat(outcome.steps().get(0).check().orElseThrow().degree()).isEqualTo(DegreeOfSuccess.CRITICAL_SUCCESS);
		assertThat(outcome.steps().get(1).check().orElseThrow().degree()).isEqualTo(DegreeOfSuccess.FAILURE);
		assertThat(outcome.overall()).isEqualTo(OverallResult.PARTIAL_SUCCESS);
	}

	@Test
	void automaticStepsDrawNoRolls() {
		FixedRolls rolls = new FixedRolls(11);
		ResolvedOutcome outcome = setup.resolve(intent(hold(), slash(), moveTo("aisle")), rolls);

		assertThat(outcome.metadata().rollsConsumed()).isEqualTo(1);
		assertThat(rolls.drawn()).isEqualTo(1);
		assertThat(outcome.overall()).isEqualTo(OverallResult.COMPLETE_SUCCESS);
	}

	@Test
	void movementEarlierInTheIntentChangesTheZoneForLaterMovement() {
		ActionIntent intent = intent(moveTo("aisle"), moveTo("entrance"));
		ResolvedOutcome outcome = setup.resolve(intent, new FixedRolls());

		assertThat(outcome.effects()).containsExactly(new OutcomeEffect.PlayerMoved("entrance", "aisle"),
				new OutcomeEffect.PlayerMoved("aisle", "entrance"));
	}
}
