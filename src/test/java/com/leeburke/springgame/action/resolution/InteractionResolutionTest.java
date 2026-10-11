package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionPayload.InteractPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.StepResult.InteractionFailure;
import com.leeburke.springgame.action.resolution.StepResult.InteractionResult;
import com.leeburke.springgame.world.ContainerState;
import com.leeburke.springgame.world.SceneState;

/** Opening a container and taking from it: automatic, no roll, and only beside it. */
class InteractionResolutionTest {

	private static final String CRATE = "pew_1"; // the fixture's one object, given container state below

	private final InteractionResolver resolver = new InteractionResolver();
	private final SceneState scene = ResolutionFixtures.scene();

	private static InteractPayload interact(InteractionKind kind) {
		return new InteractPayload(kind, new ActionTarget.ObjectTarget(CRATE, TargetSpecificity.EXPLICIT), Optional.empty(),
				ActionApproach.NORMAL);
	}

	private StepOutcome resolve(InteractionKind kind, ContainerState state, String zone, int beltRoom) {
		Map<String, ContainerState> containers = new HashMap<>();
		if (state != null) {
			containers.put(state.objectId(), state);
		}
		return resolver.resolve(new ActionStep("s1", 1, StepRelation.START, interact(kind)), interact(kind), scene, zone, containers,
				beltRoom);
	}

	private static InteractionResult result(StepOutcome outcome) {
		return (InteractionResult) outcome.result().orElseThrow();
	}

	@Test
	void openingAClosedContainerOpensItAndShowsWhatItHolds() {
		StepOutcome opened = resolve(InteractionKind.OPEN, new ContainerState(CRATE, false, List.of("BANDAGE")), "entrance", 2);

		assertThat(opened.success()).contains(StepSuccess.SUCCESS);
		assertThat(opened.check()).as("no roll").isEmpty();
		assertThat(opened.effects()).containsExactly(new OutcomeEffect.ContainerOpened(CRATE));
		assertThat(result(opened).contents()).containsExactly("BANDAGE");
		assertThat(result(opened).alreadyOpen()).isFalse();
	}

	@Test
	void openingAnOpenContainerChangesNothing() {
		StepOutcome opened = resolve(InteractionKind.OPEN, new ContainerState(CRATE, true, List.of()), "entrance", 2);

		assertThat(opened.success()).contains(StepSuccess.SUCCESS);
		assertThat(opened.effects()).isEmpty();
		assertThat(result(opened).alreadyOpen()).isTrue();
	}

	@Test
	void takingFromAnOpenContainerPutsTheItemOnTheBelt() {
		StepOutcome taken = resolve(InteractionKind.PICK_UP, new ContainerState(CRATE, true, List.of("TORCH")), "entrance", 1);

		assertThat(taken.success()).contains(StepSuccess.SUCCESS);
		assertThat(taken.effects()).containsExactly(new OutcomeEffect.ItemTaken(CRATE, "TORCH"));
	}

	@Test
	void everyReasonATakeFailsIsTold() {
		assertThat(result(resolve(InteractionKind.PICK_UP, new ContainerState(CRATE, false, List.of("TORCH")), "entrance", 1)).failure())
				.contains(InteractionFailure.CLOSED);
		assertThat(result(resolve(InteractionKind.PICK_UP, new ContainerState(CRATE, true, List.of()), "entrance", 1)).failure())
				.contains(InteractionFailure.EMPTY);
		assertThat(result(resolve(InteractionKind.PICK_UP, new ContainerState(CRATE, true, List.of("TORCH")), "entrance", 0)).failure())
				.contains(InteractionFailure.NO_ROOM);
	}

	@Test
	void outOfReachCannotBeginSoItIsNeverAFailure() {
		for (InteractionKind kind : List.of(InteractionKind.OPEN, InteractionKind.PICK_UP)) {
			StepOutcome farAway = resolve(kind, new ContainerState(CRATE, false, List.of("TORCH")), "aisle", 1);
			assertThat(farAway.status()).as(kind.name()).isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
			assertThat(farAway.unavailable()).contains(UnavailableReason.OUT_OF_REACH);
			assertThat(farAway.effects()).isEmpty();
		}
	}

	@Test
	void whatHasNoMechanicsYetIsSaidSoNotFailed() {
		assertThat(resolve(InteractionKind.OPEN, null, "entrance", 1).status()).as("not a container")
				.isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
		assertThat(resolve(InteractionKind.BREAK, new ContainerState(CRATE, false, List.of("TORCH")), "entrance", 1).status())
				.isEqualTo(StepStatus.MECHANICS_UNAVAILABLE);
	}

	@Test
	void openThenTakeWorksInOneIntentAndTheBeltRoomIsCounted() {
		ResolutionFixtures.Setup setup = new ResolutionFixtures.Setup();
		setup.scene = ResolutionFixtures.scene().withContainer(new ContainerState(CRATE, false, List.of("BANDAGE")));

		ResolvedOutcome outcome = setup.resolve(intent(interact(InteractionKind.OPEN), interact(InteractionKind.PICK_UP)), new FixedRolls());

		assertThat(outcome.steps()).allMatch(s -> s.success().filter(StepSuccess.SUCCESS::equals).isPresent());
		assertThat(outcome.effects()).containsExactly(new OutcomeEffect.ContainerOpened(CRATE), new OutcomeEffect.ItemTaken(CRATE, "BANDAGE"));
		assertThat(outcome.metadata().rollsConsumed()).isZero();
	}
}
