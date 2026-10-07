package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ACOLYTE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionPayload.CommunicatePayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;

class CommunicationResolutionTest {

	private final FixedRolls rolls = new FixedRolls();

	@Test
	void speakingToACreatureIsAnAutomaticSuccessWithoutConsequences() {
		ResolvedOutcome outcome = new Setup().resolve(
				intent(new CommunicatePayload(CommunicationKind.THREATEN, "Kneel or burn.", acolyte())), rolls);
		StepOutcome step = outcome.steps().getFirst();

		assertThat(step.success()).contains(StepSuccess.SUCCESS);
		assertThat(step.check()).isEmpty();
		assertThat(step.effects()).isEmpty();
		assertThat(step.result()).contains(new StepResult.CommunicationResult(CommunicationKind.THREATEN, Optional.of(ACOLYTE)));
		assertThat(outcome.overall()).isEqualTo(OverallResult.COMPLETE_SUCCESS);
		assertThat(rolls.drawn()).isZero();
	}

	@Test
	void speakingToNoOneHasNoAddressee() {
		StepOutcome step = new Setup().resolve(
				intent(new CommunicatePayload(CommunicationKind.SAY, "Hello?", ActionTarget.unspecified())), rolls)
				.steps().getFirst();

		assertThat(step.result()).contains(new StepResult.CommunicationResult(CommunicationKind.SAY, Optional.empty()));
	}

	@Test
	void spokenContentIsNotCarriedIntoTheOutcome() {
		ResolvedOutcome outcome = new Setup().resolve(
				intent(new CommunicatePayload(CommunicationKind.DECEIVE, "I am the bishop.", acolyte())), rolls);

		assertThat(outcome.toString()).doesNotContain("bishop");
	}
}
