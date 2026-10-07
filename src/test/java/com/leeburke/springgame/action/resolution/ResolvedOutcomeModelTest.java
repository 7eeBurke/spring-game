package com.leeburke.springgame.action.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.mechanics.BodyPart;

class ResolvedOutcomeModelTest {

	private static final StepResult SAID = new StepResult.CommunicationResult(CommunicationKind.SAY, Optional.empty());
	private static final OutcomeEffect MOVED = new OutcomeEffect.PlayerMoved("entrance", "aisle");

	private static StepOutcome success(String id) {
		return StepOutcome.resolved(id, ActionType.COMMUNICATE, StepSuccess.SUCCESS, Optional.empty(), SAID, List.of());
	}

	private static StepOutcome resolved(String id, StepSuccess success) {
		return StepOutcome.resolved(id, ActionType.COMMUNICATE, success, Optional.empty(), SAID, List.of());
	}

	private static StepOutcome unavailable(String id) {
		return StepOutcome.unavailable(id, ActionType.OBSERVE, UnavailableReason.ACTION_NOT_IMPLEMENTED);
	}

	private static StepOutcome cancelled(String id, CancellationReason reason) {
		return StepOutcome.cancelled(id, ActionType.ATTACK, reason);
	}

	// --- StepOutcome invariants ---

	@Test
	void resolvedStepNeedsASuccessLevelAndNoReason() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.RESOLVED,
				Optional.empty(), Optional.empty(), Optional.of(SAID), List.of(), Optional.empty(), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.RESOLVED,
				Optional.of(StepSuccess.SUCCESS), Optional.empty(), Optional.of(SAID), List.of(),
				Optional.of(CancellationReason.PLAYER_DOWN), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.RESOLVED,
				Optional.of(StepSuccess.SUCCESS), Optional.empty(), Optional.of(SAID), List.of(), Optional.empty(),
				Optional.of(UnavailableReason.ACTION_NOT_IMPLEMENTED)));
	}

	@Test
	void cancelledStepHasOnlyItsReason() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.CANCELLED,
				Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Optional.empty(), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.CANCELLED,
				Optional.of(StepSuccess.FAILURE), Optional.empty(), Optional.empty(), List.of(),
				Optional.of(CancellationReason.PLAYER_DOWN), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE, StepStatus.CANCELLED,
				Optional.empty(), Optional.empty(), Optional.empty(), List.of(MOVED),
				Optional.of(CancellationReason.PLAYER_DOWN), Optional.empty()));
		assertThat(cancelled("s1", CancellationReason.PLAYER_DOWN).cancellation()).contains(CancellationReason.PLAYER_DOWN);
	}

	@Test
	void unavailableStepHasOnlyItsReason() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE,
				StepStatus.MECHANICS_UNAVAILABLE, Optional.empty(), Optional.empty(), Optional.of(SAID), List.of(),
				Optional.empty(), Optional.of(UnavailableReason.ACTION_NOT_IMPLEMENTED)));
		assertThatIllegalArgumentException().isThrownBy(() -> new StepOutcome("s1", ActionType.MOVE,
				StepStatus.MECHANICS_UNAVAILABLE, Optional.empty(), Optional.empty(), Optional.empty(), List.of(),
				Optional.of(CancellationReason.PLAYER_DOWN), Optional.of(UnavailableReason.ACTION_NOT_IMPLEMENTED)));
		StepOutcome step = unavailable("s1");
		assertThat(step.success()).isEmpty();
		assertThat(step.check()).isEmpty();
		assertThat(step.effects()).isEmpty();
	}

	@Test
	void stepEffectsAreImmutableCopies() {
		List<OutcomeEffect> effects = new ArrayList<>(List.of(MOVED));
		StepOutcome step = StepOutcome.resolved("s1", ActionType.MOVE, StepSuccess.SUCCESS, Optional.empty(),
				new StepResult.MovementResult("entrance", "aisle", true), effects);
		effects.clear();
		assertThat(step.effects()).containsExactly(MOVED);
		assertThatThrownBy(() -> step.effects().add(MOVED)).isInstanceOf(UnsupportedOperationException.class);
	}

	// --- ResolvedOutcome ---

	@Test
	void outcomeNeedsAtLeastOneStepAndKeepsThemImmutable() {
		ResolutionMetadata metadata = new ResolutionMetadata(0, 1);
		assertThatIllegalArgumentException().isThrownBy(() -> new ResolvedOutcome(1, Optional.empty(),
				OverallResult.MECHANICS_UNAVAILABLE, List.of(), metadata));
		ResolvedOutcome outcome = new ResolvedOutcome(1, Optional.empty(), OverallResult.COMPLETE_SUCCESS,
				List.of(success("s1")), metadata);
		assertThatThrownBy(() -> outcome.steps().add(success("s2"))).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void effectsAreFlattenedInStepOrder() {
		OutcomeEffect hit = new OutcomeEffect.TargetDamaged("acolyte_1", 4, Optional.of(BodyPart.HEAD), Optional.empty());
		OutcomeEffect hurt = new OutcomeEffect.PlayerDamaged(2, Optional.empty(), Optional.empty());
		StepOutcome first = StepOutcome.resolved("s1", ActionType.MOVE, StepSuccess.SUCCESS, Optional.empty(),
				new StepResult.MovementResult("entrance", "aisle", true), List.of(MOVED));
		StepOutcome second = StepOutcome.resolved("s2", ActionType.COMMUNICATE, StepSuccess.SUCCESS, Optional.empty(), SAID,
				List.of(hit, hurt));
		ResolvedOutcome outcome = new ResolvedOutcome(1, Optional.empty(), OverallResult.COMPLETE_SUCCESS,
				List.of(first, second), new ResolutionMetadata(0, 1));
		assertThat(outcome.effects()).containsExactly(MOVED, hit, hurt);
	}

	@Test
	void metadataRejectsNegativeRollsAndUnknownRulesVersion() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ResolutionMetadata(-1, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> new ResolutionMetadata(0, 0));
	}

	// --- Results and effects ---

	@Test
	void effectsRejectNegativeDamageAndNonMoves() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new OutcomeEffect.TargetDamaged("acolyte_1", -1, Optional.empty(), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new OutcomeEffect.PlayerDamaged(-1, Optional.empty(), Optional.empty()));
		assertThatIllegalArgumentException().isThrownBy(() -> new OutcomeEffect.PlayerMoved("aisle", "aisle"));
		assertThatIllegalArgumentException().isThrownBy(
				() -> new OutcomeEffect.TargetDamaged(" ", 1, Optional.empty(), Optional.empty()));
	}

	@Test
	void movementResultCannotMoveWithinTheSameZone() {
		assertThatIllegalArgumentException().isThrownBy(() -> new StepResult.MovementResult("aisle", "aisle", true));
		assertThat(new StepResult.MovementResult("aisle", "aisle", false).moved()).isFalse();
	}

	@Test
	void targetProfileRejectsNegativeProtection() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> new TargetCombatProfile(12, com.leeburke.springgame.mechanics.Effectiveness.NORMAL, -1, 0, 0, 0, 0));
	}

	// --- Aggregation (each row of the documented table) ---

	@Test
	void allStepsSucceedingIsCompleteSuccess() {
		assertThat(ResolvedOutcome.aggregate(List.of(success("s1"), success("s2")))).isEqualTo(OverallResult.COMPLETE_SUCCESS);
	}

	@Test
	void successThenUnavailableIsPartialSuccess() {
		assertThat(ResolvedOutcome.aggregate(List.of(success("s1"), unavailable("s2"))))
				.isEqualTo(OverallResult.PARTIAL_SUCCESS);
	}

	@Test
	void failureThenCancelledIsFailure() {
		assertThat(ResolvedOutcome.aggregate(List.of(resolved("s1", StepSuccess.FAILURE),
				cancelled("s2", CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL)))).isEqualTo(OverallResult.FAILURE);
	}

	@Test
	void partialThenCancelledIsPartialSuccess() {
		assertThat(ResolvedOutcome.aggregate(List.of(resolved("s1", StepSuccess.PARTIAL),
				cancelled("s2", CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL)))).isEqualTo(OverallResult.PARTIAL_SUCCESS);
	}

	@Test
	void successAndFailureIsPartialSuccess() {
		assertThat(ResolvedOutcome.aggregate(List.of(success("s1"), resolved("s2", StepSuccess.FAILURE))))
				.isEqualTo(OverallResult.PARTIAL_SUCCESS);
	}

	@Test
	void onlyUnavailableIsMechanicsUnavailable() {
		assertThat(ResolvedOutcome.aggregate(List.of(unavailable("s1")))).isEqualTo(OverallResult.MECHANICS_UNAVAILABLE);
	}

	@Test
	void playerDownIsInterruptedRegardlessOfEarlierSuccess() {
		assertThat(ResolvedOutcome.aggregate(List.of(success("s1"), cancelled("s2", CancellationReason.PLAYER_DOWN))))
				.isEqualTo(OverallResult.INTERRUPTED);
	}
}
