package com.leeburke.springgame.action;

import static com.leeburke.springgame.action.ActionFixtures.attack;
import static com.leeburke.springgame.action.ActionFixtures.entity;
import static com.leeburke.springgame.action.ActionFixtures.search;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ActionIntentTest {

	private static ActionStep step(String id, int sequence, StepRelation relation) {
		return new ActionStep(id, sequence, relation, search());
	}

	private static ActionIntent intent(List<ActionStep> steps) {
		return new ActionIntent(1, Optional.empty(), steps, InterpretationConfidence.HIGH, List.of());
	}

	@Test
	void requiresAtLeastOneStep() {
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of()));
	}

	@Test
	void sequencesAreOneBasedContiguousAndInListOrder() {
		assertThat(intent(List.of(step("a", 1, StepRelation.START), step("b", 2, StepRelation.THEN),
				step("c", 3, StepRelation.WHILE))).steps()).hasSize(3);
		assertThatIllegalArgumentException().isThrownBy(() -> step("a", 0, StepRelation.START));
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of(step("a", 1, StepRelation.START), step("b", 3, StepRelation.THEN))));
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of(step("b", 2, StepRelation.THEN), step("a", 1, StepRelation.START))));
	}

	@Test
	void onlyTheFirstStepUsesStart() {
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of(step("a", 1, StepRelation.THEN))));
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of(step("a", 1, StepRelation.START), step("b", 2, StepRelation.START))));
	}

	@ParameterizedTest
	@EnumSource(value = StepRelation.class, names = "START", mode = EnumSource.Mode.EXCLUDE)
	void laterStepsMayUseEveryOtherRelation(StepRelation relation) {
		assertThat(intent(List.of(step("a", 1, StepRelation.START), step("b", 2, relation))).steps().get(1).relation())
				.isEqualTo(relation);
	}

	@Test
	void stepIdsMustBeUniqueAndClean() {
		assertThatIllegalArgumentException().isThrownBy(() -> intent(List.of(step("a", 1, StepRelation.START), step("a", 2, StepRelation.THEN))));
		assertThatIllegalArgumentException().isThrownBy(() -> step(" a", 1, StepRelation.START));
		assertThatIllegalArgumentException().isThrownBy(() -> step("", 1, StepRelation.START));
	}

	@ParameterizedTest
	@EnumSource(InterpretationConfidence.class)
	void everyConfidenceIsAllowed(InterpretationConfidence confidence) {
		assertThat(new ActionIntent(1, Optional.empty(), List.of(step("a", 1, StepRelation.START)), confidence, List.of())
				.confidence()).isEqualTo(confidence);
	}

	@Test
	void unresolvedReferencesMustPointAtRealSteps() {
		List<ActionStep> steps = List.of(step("a", 1, StepRelation.START));
		assertThat(new ActionIntent(1, Optional.empty(), steps, InterpretationConfidence.LOW,
				List.of(new UnresolvedReference(Optional.of("a"), "the lever"), new UnresolvedReference(Optional.empty(), "it")))
				.unresolvedReferences()).hasSize(2);
		assertThatIllegalArgumentException().isThrownBy(() -> new ActionIntent(1, Optional.empty(), steps,
				InterpretationConfidence.LOW, List.of(new UnresolvedReference(Optional.of("z"), "the lever"))));
		assertThatIllegalArgumentException().isThrownBy(() -> new UnresolvedReference(Optional.empty(), "  "));
	}

	@Test
	void schemaVersionAndResponseReferenceRules() {
		List<ActionStep> steps = List.of(step("a", 1, StepRelation.START));
		assertThatIllegalArgumentException().isThrownBy(() -> new ActionIntent(0, Optional.empty(), steps, InterpretationConfidence.HIGH, List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> new ActionIntent(1, Optional.of(" "), steps, InterpretationConfidence.HIGH, List.of()));
		assertThat(new ActionIntent(2, Optional.empty(), steps, InterpretationConfidence.HIGH, List.of()).schemaVersion()).isEqualTo(2);
	}

	@Test
	void listsAreCopiedAndUnmodifiableAndNullsRejected() {
		List<ActionStep> steps = new ArrayList<>(List.of(step("a", 1, StepRelation.START)));
		ActionIntent intent = intent(steps);
		steps.add(step("b", 2, StepRelation.THEN));
		assertThat(intent.steps()).hasSize(1);
		assertThatThrownBy(() -> intent.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatNullPointerException().isThrownBy(() -> intent(Arrays.asList(step("a", 1, StepRelation.START), null)));
		assertThatNullPointerException().isThrownBy(() -> new ActionIntent(1, null, List.of(step("a", 1, StepRelation.START)),
				InterpretationConfidence.HIGH, List.of()));
	}

	@Test
	void actionTypeIsDerivedFromThePayload() {
		assertThat(new ActionStep("a", 1, StepRelation.START, attack(entity("acolyte_1"))).actionType()).isEqualTo(ActionType.ATTACK);
		assertThat(new ActionStep("a", 1, StepRelation.START, search()).actionType()).isEqualTo(ActionType.OBSERVE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "ATTACK", "DEFEND", "MOVE", "INTERACT", "OBSERVE", "USE_ABILITY", "USE_ITEM", "COMMUNICATE" })
	void everyActionTypeHasExactlyOnePayloadRecord(String type) {
		long matches = Arrays.stream(ActionPayload.class.getPermittedSubclasses())
				.filter(c -> c.getSimpleName().equals(toPayloadName(type)))
				.count();
		assertThat(matches).isEqualTo(1);
	}

	private static String toPayloadName(String type) {
		StringBuilder name = new StringBuilder();
		for (String part : type.split("_")) {
			name.append(part.charAt(0)).append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
		}
		return name + "Payload";
	}
}
