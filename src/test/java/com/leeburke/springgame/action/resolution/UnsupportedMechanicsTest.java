package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;

class UnsupportedMechanicsTest {

	static Stream<ActionPayload> unsupported() {
		return Stream.of(
				new ActionPayload.InteractPayload(InteractionKind.PUSH,
						new ActionTarget.ObjectTarget("pew_1", TargetSpecificity.EXPLICIT), Optional.empty(), ActionApproach.NORMAL),
				new ActionPayload.UseAbilityPayload(ActionFixtures.STONEBLOOD, new ActionTarget.SelfTarget(Optional.empty(),
						TargetSpecificity.EXPLICIT)),
				new ActionPayload.UseItemPayload(ActionFixtures.SALVE, ActionTarget.unspecified()));
	}

	/** Observation reads only what is visible: automatic, no roll, no effect, nothing revealed. */
	@ParameterizedTest
	@MethodSource("observations")
	void observationResolvesAutomaticallyWithoutRevealingAnything(ActionPayload payload) {
		FixedRolls rolls = new FixedRolls();
		StepOutcome step = new Setup().resolve(intent(payload), rolls).steps().getFirst();

		assertThat(step.status()).isEqualTo(StepStatus.RESOLVED);
		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.check()).isEmpty();
		assertThat(step.result()).containsInstanceOf(StepResult.ObservationResult.class);
		assertThat(step.effects()).isEmpty();
		assertThat(rolls.drawn()).isZero();
	}

	static Stream<ActionPayload> observations() {
		return Stream.of(new ActionPayload.ObservePayload(ObservationKind.INSPECT, acolyte()), ActionFixtures.search());
	}

	@ParameterizedTest
	@MethodSource("unsupported")
	void validActionWithoutMechanicsIsUnavailableNotFailed(ActionPayload payload) {
		FixedRolls rolls = new FixedRolls();
		ResolvedOutcome outcome = new Setup().resolve(intent(payload), rolls);
		StepOutcome step = outcome.steps().getFirst();

		assertThat(step.actionType()).isEqualTo(payload.type());
		assertThat(step.status()).isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
		assertThat(step.unavailable()).contains(UnavailableReason.ACTION_NOT_IMPLEMENTED);
		assertThat(step.success()).isEmpty();
		assertThat(step.effects()).isEmpty();
		assertThat(outcome.overall()).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
		assertThat(rolls.drawn()).isZero();
		assertThat(new Setup().resolve(intent(payload), new FixedRolls())).isEqualTo(outcome);
	}
}
