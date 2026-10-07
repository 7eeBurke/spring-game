package com.leeburke.springgame.action.validation;

import static com.leeburke.springgame.action.ActionFixtures.CROWBAR;
import static com.leeburke.springgame.action.ActionFixtures.INCOMING;
import static com.leeburke.springgame.action.ActionFixtures.SALVE;
import static com.leeburke.springgame.action.ActionFixtures.SPARE_SALVE;
import static com.leeburke.springgame.action.ActionFixtures.STONEBLOOD;
import static com.leeburke.springgame.action.ActionFixtures.attack;
import static com.leeburke.springgame.action.ActionFixtures.context;
import static com.leeburke.springgame.action.ActionFixtures.entity;
import static com.leeburke.springgame.action.ActionFixtures.intent;
import static com.leeburke.springgame.action.ActionFixtures.search;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionPayload.CommunicatePayload;
import com.leeburke.springgame.action.ActionPayload.DefendPayload;
import com.leeburke.springgame.action.ActionPayload.InteractPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionPayload.ObservePayload;
import com.leeburke.springgame.action.ActionPayload.UseAbilityPayload;
import com.leeburke.springgame.action.ActionPayload.UseItemPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionTarget.ExitTarget;
import com.leeburke.springgame.action.ActionTarget.HazardTarget;
import com.leeburke.springgame.action.ActionTarget.ObjectTarget;
import com.leeburke.springgame.action.ActionTarget.SelfTarget;
import com.leeburke.springgame.action.ActionTarget.ZoneTarget;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
import com.leeburke.springgame.action.CommunicationKind;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.EvadeType;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.ParryContact;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.BodyPart;

class ActionValidatorTest {

	private static final TargetSpecificity EXPLICIT = TargetSpecificity.EXPLICIT;
	private final ActionValidator validator = new ActionValidator();

	private ActionValidationResult validate(ActionPayload... payloads) {
		return validator.validate(intent(payloads), context());
	}

	private static List<ActionValidationCode> codes(ActionValidationResult result) {
		return result.errors().stream().map(ActionValidationError::code).toList();
	}

	private static MovePayload move(MovementType type, ActionTarget target) {
		return new MovePayload(type, target, RelativeGoal.NONE, ActionApproach.NORMAL);
	}

	private static InteractPayload interact(InteractionKind kind, ActionTarget target) {
		return new InteractPayload(kind, target, Optional.empty(), ActionApproach.NORMAL);
	}

	// --- Valid targeting of every visible kind ---

	@Test
	void everyVisibleTargetKindValidates() {
		assertThat(validate(attack(entity("acolyte_1"))).valid()).isTrue();
		assertThat(validate(interact(InteractionKind.PUSH, new ObjectTarget("pew_1", EXPLICIT))).valid()).isTrue();
		assertThat(validate(interact(InteractionKind.EXTINGUISH, new HazardTarget("fire_1", EXPLICIT))).valid()).isTrue();
		assertThat(validate(move(MovementType.REPOSITION, new ZoneTarget("aisle", EXPLICIT))).valid()).isTrue();
		assertThat(validate(move(MovementType.ADVANCE, new ExitTarget("north_door", EXPLICIT))).valid()).isTrue();
		assertThat(validate(search()).valid()).isTrue();
	}

	@Test
	void unknownSceneReferencesOfEveryKind() {
		assertThat(codes(validate(attack(entity("ghoul_9"))))).containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
		assertThat(codes(validate(interact(InteractionKind.PUSH, new ObjectTarget("altar_9", EXPLICIT)))))
				.containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
		assertThat(codes(validate(interact(InteractionKind.EXTINGUISH, new HazardTarget("fire_9", EXPLICIT)))))
				.containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
		assertThat(codes(validate(move(MovementType.REPOSITION, new ZoneTarget("crypt", EXPLICIT)))))
				.containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
		assertThat(codes(validate(move(MovementType.ADVANCE, new ExitTarget("secret_stair", EXPLICIT)))))
				.containsExactly(ActionValidationCode.UNKNOWN_SCENE_REFERENCE);
	}

	@Test
	void bodyPartOnVisibleEntityIsAcceptedWithoutAnatomyChecks() {
		assertThat(validate(attack(new ActionTarget.EntityTarget("acolyte_1", Optional.of(BodyPart.HEART), EXPLICIT))).valid()).isTrue();
	}

	// --- Ownership ---

	@Test
	void ownedReferencesAreAccepted() {
		assertThat(validate(new UseItemPayload(SALVE, new SelfTarget(Optional.of(BodyPart.LEFT_ARM), EXPLICIT))).valid()).isTrue();
		assertThat(validate(new UseItemPayload(SPARE_SALVE, ActionTarget.unspecified())).valid()).isTrue();
		assertThat(validate(new UseAbilityPayload(STONEBLOOD, ActionTarget.unspecified())).valid()).isTrue();
		assertThat(validate(new InteractPayload(InteractionKind.JAM, new ObjectTarget("pew_1", EXPLICIT),
				Optional.of(new CarriedReference(CarriedKind.ITEM, CROWBAR)), ActionApproach.FORCEFUL)).valid()).isTrue();
	}

	@Test
	void unknownOwnedReferencesAreRejected() {
		assertThat(codes(validate(new UseItemPayload("item_z", ActionTarget.unspecified()))))
				.containsExactly(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE);
		assertThat(codes(validate(new UseAbilityPayload("ability_z", ActionTarget.unspecified()))))
				.containsExactly(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE);
		assertThat(codes(validate(new InteractPayload(InteractionKind.JAM, new ObjectTarget("pew_1", EXPLICIT),
				Optional.of(new CarriedReference(CarriedKind.WEAPON, CROWBAR)), ActionApproach.NORMAL))))
				.as("crowbar is an item reference, not a weapon reference")
				.containsExactly(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE);
	}

	@Test
	void catalogueDefinitionAloneIsNotOwnership() {
		AttackPayload withCatalogueCode = new AttackPayload("LONGSWORD", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH,
				entity("acolyte_1"), ActionApproach.NORMAL, AttackPurpose.DAMAGE);
		assertThat(ActionFixtures.CONTENT.findWeapon("LONGSWORD")).isPresent();
		assertThat(codes(validate(withCatalogueCode))).containsExactly(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE);

		ActionValidationContext noWeapons = new ActionValidationContext(ActionFixtures.view(),
				new PlayerActionReferences(Map.of(), Map.of(), Map.of()), Set.of());
		assertThat(codes(validator.validate(intent(attack(entity("acolyte_1"))), noWeapons)))
				.containsExactly(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE);
	}

	// --- Layer 1 structure ---

	@Test
	void inconsistentTargetsAreSchemaInvalid() {
		assertThat(codes(validate(attack(new ZoneTarget("aisle", EXPLICIT))))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(attack(ActionTarget.unspecified())))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(interact(InteractionKind.PICK_UP, new HazardTarget("fire_1", EXPLICIT)))))
				.containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(interact(InteractionKind.OPEN, ActionTarget.unspecified())))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(new ObservePayload(ObservationKind.INSPECT, ActionTarget.unspecified()))))
				.containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(new CommunicatePayload(CommunicationKind.SAY, "Hello", new ObjectTarget("pew_1", EXPLICIT)))))
				.containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(move(MovementType.CLOSE_DISTANCE, ActionTarget.unspecified()))))
				.containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(move(MovementType.CLIMB, new ExitTarget("north_door", EXPLICIT)))))
				.containsExactly(ActionValidationCode.SCHEMA_INVALID);
	}

	@Test
	void selfTargetIsOnlyForItemsAbilitiesAndInspection() {
		SelfTarget self = new SelfTarget(Optional.empty(), EXPLICIT);
		assertThat(validate(new UseItemPayload(SALVE, self)).valid()).isTrue();
		assertThat(validate(new UseAbilityPayload(STONEBLOOD, self)).valid()).isTrue();
		assertThat(validate(new ObservePayload(ObservationKind.INSPECT, self)).valid()).isTrue();
		assertThat(codes(validate(attack(self)))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(move(MovementType.REPOSITION, self)))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(interact(InteractionKind.PUSH, self)))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(new CommunicatePayload(CommunicationKind.SAY, "Hm.", self)))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
		assertThat(codes(validate(new ObservePayload(ObservationKind.LISTEN, self)))).containsExactly(ActionValidationCode.SCHEMA_INVALID);
	}

	@Test
	void documentedMovementCasesValidate() {
		assertThat(validate(move(MovementType.CLOSE_DISTANCE, entity("acolyte_1"))).valid()).isTrue();
		assertThat(validate(move(MovementType.RETREAT, ActionTarget.unspecified())).valid()).isTrue();
		assertThat(validate(move(MovementType.DISENGAGE, new ExitTarget("north_door", EXPLICIT))).valid()).isTrue();
		assertThat(validate(new MovePayload(MovementType.REPOSITION, new ObjectTarget("pew_1", EXPLICIT), RelativeGoal.COVER,
				ActionApproach.QUICK)).valid()).isTrue();
		assertThat(validate(new MovePayload(MovementType.HOLD_POSITION, ActionTarget.unspecified(), RelativeGoal.NONE,
				ActionApproach.CAUTIOUS)).valid()).isTrue();
	}

	// --- Unresolved references ---

	@Test
	void unresolvedReferencesInvalidateDeterministically() {
		ActionPayload ok = search();
		assertThat(validator.validate(intent(Optional.empty(), List.of(), ok), context()).valid()).isTrue();

		ActionValidationResult one = validator.validate(intent(Optional.empty(),
				List.of(new UnresolvedReference(Optional.of("s1"), "the lever")), ok), context());
		assertThat(codes(one)).containsExactly(ActionValidationCode.UNRESOLVED_REFERENCE);
		assertThat(one.errors().getFirst().message()).contains("the lever");
		assertThat(one.errors().getFirst().stepId()).contains("s1");

		ActionValidationResult three = validator.validate(intent(Optional.empty(), List.of(
				new UnresolvedReference(Optional.empty(), "it"),
				new UnresolvedReference(Optional.of("s1"), "the lever"),
				new UnresolvedReference(Optional.empty(), "that thing")), ok), context());
		assertThat(three.errors()).extracting(ActionValidationError::message)
				.containsExactly("Could not resolve 'it'", "Could not resolve 'the lever'", "Could not resolve 'that thing'");
	}

	@Test
	void unresolvedReferenceResultDoesNotDependOnTheView() {
		ActionIntent intent = intent(Optional.empty(), List.of(new UnresolvedReference(Optional.empty(), "the relic")), search());
		PlayerSceneViewEmpty empty = new PlayerSceneViewEmpty();
		assertThat(validator.validate(intent, context())).isEqualTo(validator.validate(intent, ActionFixtures.context(empty.view())));
	}

	/** A minimal view with one zone and nothing else. */
	private record PlayerSceneViewEmpty() {
		com.leeburke.springgame.world.view.PlayerSceneView view() {
			return new com.leeburke.springgame.world.view.PlayerSceneView("entrance",
					List.of(new com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone("entrance", "Entrance")),
					List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		}
	}

	// --- Schema version and incoming attacks ---

	@Test
	void unsupportedSchemaVersionIsTheOnlyError() {
		ActionIntent future = new ActionIntent(2, Optional.empty(),
				List.of(new ActionStep("s1", 1, StepRelation.START, attack(entity("nobody")))), InterpretationConfidence.HIGH, List.of());
		assertThat(codes(validator.validate(future, context()))).containsExactly(ActionValidationCode.UNSUPPORTED_SCHEMA_VERSION);
	}

	@Test
	void incomingAttackReferences() {
		DefendPayload duck = new DefendPayload(DefenseMethod.EVADE, EvadeType.DUCK, ParryContact.UNSPECIFIED, ActionTarget.unspecified());
		assertThat(validator.validate(intent(Optional.of(INCOMING), List.of(), duck), context()).valid()).isTrue();
		assertThat(codes(validator.validate(intent(Optional.of("attack_9"), List.of(), duck), context())))
				.containsExactly(ActionValidationCode.UNKNOWN_INCOMING_ATTACK);
		assertThat(validator.validate(intent(search()),
				new ActionValidationContext(ActionFixtures.view(), ActionFixtures.references(), Set.of())).valid()).isTrue();
	}

	// --- Ordering and determinism ---

	@Test
	void errorsFollowStepOrderAndAreDeterministic() {
		ActionIntent intent = intent(attack(entity("ghoul_9")), search(), new UseItemPayload("item_z", ActionTarget.unspecified()));
		ActionValidationResult first = validator.validate(intent, context());

		assertThat(first.errors()).extracting(e -> e.stepId().orElseThrow()).containsExactly("s1", "s3");
		assertThat(first).isEqualTo(validator.validate(intent, context()));
		assertThat(ActionValidationResult.success().valid()).isTrue();
	}
}
