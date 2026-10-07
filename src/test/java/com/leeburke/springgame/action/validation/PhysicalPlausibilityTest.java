package com.leeburke.springgame.action.validation;

import static com.leeburke.springgame.action.ActionFixtures.attack;
import static com.leeburke.springgame.action.ActionFixtures.context;
import static com.leeburke.springgame.action.ActionFixtures.entity;
import static com.leeburke.springgame.action.ActionFixtures.intent;
import static com.leeburke.springgame.action.ActionFixtures.search;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.action.WeaponMethod;

class PhysicalPlausibilityTest {

	/** Records which steps it was asked about; declares the named step impossible. */
	private static final class RecordingPolicy implements PhysicalPlausibilityPolicy {
		final List<String> consulted = new ArrayList<>();
		private final String impossibleStep;

		RecordingPolicy(String impossibleStep) {
			this.impossibleStep = impossibleStep;
		}

		@Override
		public Optional<String> impossibility(ActionStep step, ActionValidationContext context) {
			consulted.add(step.id());
			return step.id().equals(impossibleStep) ? Optional.of("That cannot be done") : Optional.empty();
		}
	}

	@Test
	void provenImpossibilityBecomesActionPhysicallyImpossibleForThatStepOnly() {
		RecordingPolicy policy = new RecordingPolicy("s2");
		ActionValidationResult result = new ActionValidator(policy).validate(intent(search(), search(), search()), context());

		assertThat(result.errors()).containsExactly(
				new ActionValidationError(ActionValidationCode.ACTION_PHYSICALLY_IMPOSSIBLE, Optional.of("s2"), "That cannot be done"));
		assertThat(policy.consulted).containsExactly("s1", "s2", "s3");
	}

	@Test
	void policyIsNotConsultedForStepsWithEarlierErrors() {
		RecordingPolicy policy = new RecordingPolicy("none");
		ActionValidationResult result = new ActionValidator(policy).validate(
				intent(attack(entity("ghoul_9")), search(), attack(ActionTarget.unspecified())), context());

		assertThat(policy.consulted).containsExactly("s2");
		assertThat(result.errors()).extracting(ActionValidationError::code)
				.containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE, ActionValidationCode.SCHEMA_INVALID);
	}

	@Test
	void unresolvedReferenceBlocksPlausibilityForItsStep() {
		RecordingPolicy policy = new RecordingPolicy("s1");
		ActionValidationResult result = new ActionValidator(policy).validate(intent(Optional.empty(),
				List.of(new UnresolvedReference(Optional.of("s1"), "the thing")), search(), attack(entity("ghoul_9"))), context());

		assertThat(policy.consulted).isEmpty();
		assertThat(result.errors()).extracting(ActionValidationError::code)
				.containsExactly(ActionValidationCode.UNRESOLVED_REFERENCE, ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
	}

	@Test
	void unresolvedReferenceWithoutStepBlocksPlausibilityForEveryStep() {
		RecordingPolicy policy = new RecordingPolicy("s1");
		ActionValidationResult result = new ActionValidator(policy).validate(intent(Optional.empty(),
				List.of(new UnresolvedReference(Optional.empty(), "it")), search(), search(), attack(entity("ghoul_9"))), context());

		assertThat(policy.consulted).isEmpty();
		assertThat(result.errors()).extracting(ActionValidationError::code)
				.containsExactly(ActionValidationCode.UNRESOLVED_REFERENCE, ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
	}

	@Test
	void oddButPossibleActionPassesTheDefaultPolicy() {
		AttackPayload pommelTheFire = new AttackPayload(ActionFixtures.SWORD, WeaponMethod.POMMEL_STRIKE, AttackTemplate.LOW_SWEEP,
				new ActionTarget.HazardTarget("fire_1", TargetSpecificity.EXPLICIT), ActionApproach.ACROBATIC, AttackPurpose.EXTINGUISH);
		assertThat(new ActionValidator().validate(intent(pommelTheFire), context()).valid()).isTrue();
	}

	@Test
	void validationResultsCarryNoMechanics() {
		for (var component : ActionValidationError.class.getRecordComponents()) {
			assertThat(component.getType().getPackageName()).doesNotContain("mechanics");
		}
		assertThat(ActionValidationResult.class.getRecordComponents()).hasSize(1);
	}
}
