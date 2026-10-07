package com.leeburke.springgame.action;

import static com.leeburke.springgame.action.ActionFixtures.entity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionPayload.CommunicatePayload;
import com.leeburke.springgame.action.ActionPayload.DefendPayload;
import com.leeburke.springgame.action.ActionPayload.InteractPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionPayload.UseAbilityPayload;
import com.leeburke.springgame.action.ActionPayload.UseItemPayload;
import com.leeburke.springgame.action.ActionTarget.EntityTarget;
import com.leeburke.springgame.action.ActionTarget.ExitTarget;
import com.leeburke.springgame.action.ActionTarget.HazardTarget;
import com.leeburke.springgame.action.ActionTarget.ObjectTarget;
import com.leeburke.springgame.action.ActionTarget.SelfTarget;
import com.leeburke.springgame.action.ActionTarget.ZoneTarget;
import com.leeburke.springgame.mechanics.BodyPart;

class ActionTargetAndPayloadTest {

	private static final TargetSpecificity EXPLICIT = TargetSpecificity.EXPLICIT;

	// --- Targets ---

	@Test
	void everyTargetKindCanBeBuilt() {
		assertThat(new EntityTarget("acolyte_1", Optional.of(BodyPart.LEFT_ARM), TargetSpecificity.INFERRED).bodyPart())
				.contains(BodyPart.LEFT_ARM);
		assertThat(new ObjectTarget("pew_1", EXPLICIT).specificity()).isEqualTo(EXPLICIT);
		assertThat(new HazardTarget("fire_1", EXPLICIT).hazardId()).isEqualTo("fire_1");
		assertThat(new ZoneTarget("aisle", EXPLICIT).zoneId()).isEqualTo("aisle");
		assertThat(new ExitTarget("north_door", EXPLICIT).exitId()).isEqualTo("north_door");
		assertThat(new SelfTarget(Optional.of(BodyPart.LEFT_ARM), EXPLICIT).bodyPart()).contains(BodyPart.LEFT_ARM);
		assertThat(ActionTarget.unspecified().specificity()).isEqualTo(TargetSpecificity.UNSPECIFIED);
	}

	@Test
	void referencingTargetsCannotBeUnspecified() {
		TargetSpecificity unspecified = TargetSpecificity.UNSPECIFIED;
		assertThatIllegalArgumentException().isThrownBy(() -> new EntityTarget("a", Optional.empty(), unspecified));
		assertThatIllegalArgumentException().isThrownBy(() -> new ObjectTarget("a", unspecified));
		assertThatIllegalArgumentException().isThrownBy(() -> new HazardTarget("a", unspecified));
		assertThatIllegalArgumentException().isThrownBy(() -> new ZoneTarget("a", unspecified));
		assertThatIllegalArgumentException().isThrownBy(() -> new ExitTarget("a", unspecified));
		assertThatIllegalArgumentException().isThrownBy(() -> new SelfTarget(Optional.empty(), unspecified));
	}

	@Test
	void unspecifiedTargetCarriesNoReference() {
		assertThat(ActionTarget.Unspecified.class.getRecordComponents()).isEmpty();
	}

	@Test
	void bodyPartIsOnlyRepresentableOnCreatureOrSelfTargets() {
		for (Class<?> target : ActionTarget.class.getPermittedSubclasses()) {
			boolean hasBodyPart = java.util.Arrays.stream(target.getRecordComponents()).anyMatch(c -> c.getName().equals("bodyPart"));
			assertThat(hasBodyPart).as(target.getSimpleName())
					.isEqualTo(target == EntityTarget.class || target == SelfTarget.class);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " pew_1", "pew_1 " })
	void targetIdsMustBeClean(String id) {
		assertThatIllegalArgumentException().isThrownBy(() -> new ObjectTarget(id, EXPLICIT));
	}

	@Test
	void nullTargetPartsAreRejected() {
		assertThatNullPointerException().isThrownBy(() -> new ObjectTarget(null, EXPLICIT));
		assertThatNullPointerException().isThrownBy(() -> new EntityTarget("a", null, EXPLICIT));
		assertThatNullPointerException().isThrownBy(() -> new ZoneTarget("a", null));
	}

	// --- Payloads ---

	@Test
	void defendRejectsMeaninglessCombinations() {
		ActionTarget none = ActionTarget.unspecified();
		ObjectTarget pew = new ObjectTarget("pew_1", EXPLICIT);
		assertThat(new DefendPayload(DefenseMethod.EVADE, EvadeType.DUCK, ParryContact.UNSPECIFIED, none).type()).isEqualTo(ActionType.DEFEND);
		assertThat(new DefendPayload(DefenseMethod.PARRY, EvadeType.UNSPECIFIED, ParryContact.BLADE, none).parryContact()).isEqualTo(ParryContact.BLADE);
		assertThat(new DefendPayload(DefenseMethod.TAKE_COVER, EvadeType.UNSPECIFIED, ParryContact.UNSPECIFIED, pew).cover()).isEqualTo(pew);
		assertThatIllegalArgumentException().isThrownBy(() -> new DefendPayload(DefenseMethod.BLOCK, EvadeType.DUCK, ParryContact.UNSPECIFIED, none));
		assertThatIllegalArgumentException().isThrownBy(() -> new DefendPayload(DefenseMethod.EVADE, EvadeType.UNSPECIFIED, ParryContact.BLADE, none));
		assertThatIllegalArgumentException().isThrownBy(() -> new DefendPayload(DefenseMethod.BRACE, EvadeType.UNSPECIFIED, ParryContact.UNSPECIFIED, pew));
		assertThatIllegalArgumentException().isThrownBy(() -> new DefendPayload(DefenseMethod.TAKE_COVER, EvadeType.UNSPECIFIED,
				ParryContact.UNSPECIFIED, new HazardTarget("fire_1", EXPLICIT)));
	}

	@Test
	void moveRejectsHoldPositionWithTargetAndCoverWithoutReposition() {
		ZoneTarget aisle = new ZoneTarget("aisle", EXPLICIT);
		assertThat(new MovePayload(MovementType.REPOSITION, ActionTarget.unspecified(), RelativeGoal.COVER, ActionApproach.QUICK).goal())
				.isEqualTo(RelativeGoal.COVER);
		assertThatIllegalArgumentException().isThrownBy(() -> new MovePayload(MovementType.HOLD_POSITION, aisle, RelativeGoal.NONE, ActionApproach.NORMAL));
		assertThatIllegalArgumentException().isThrownBy(() -> new MovePayload(MovementType.ADVANCE, aisle, RelativeGoal.COVER, ActionApproach.NORMAL));
	}

	@Test
	void interactRequiresCarriedForDropAndPlace() {
		ObjectTarget pew = new ObjectTarget("pew_1", EXPLICIT);
		assertThatIllegalArgumentException().isThrownBy(() -> new InteractPayload(InteractionKind.DROP, ActionTarget.unspecified(), Optional.empty(), ActionApproach.NORMAL));
		assertThatIllegalArgumentException().isThrownBy(() -> new InteractPayload(InteractionKind.PLACE, pew, Optional.empty(), ActionApproach.NORMAL));
		assertThat(new InteractPayload(InteractionKind.JAM, pew, Optional.of(new CarriedReference(CarriedKind.ITEM, "item_b")), ActionApproach.FORCEFUL)
				.carried()).isPresent();
	}

	@Test
	void communicationNeedsContent() {
		assertThatIllegalArgumentException().isThrownBy(() -> new CommunicatePayload(CommunicationKind.SAY, "  ", ActionTarget.unspecified()));
		assertThatNullPointerException().isThrownBy(() -> new CommunicatePayload(CommunicationKind.ASK, null, ActionTarget.unspecified()));
		assertThat(new CommunicatePayload(CommunicationKind.THREATEN, "Back off.", entity("acolyte_1")).type()).isEqualTo(ActionType.COMMUNICATE);
	}

	@Test
	void ownedReferencesMustBeClean() {
		assertThatIllegalArgumentException().isThrownBy(() -> new AttackPayload(" ", WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH,
				entity("a"), ActionApproach.NORMAL, AttackPurpose.DAMAGE));
		assertThatNullPointerException().isThrownBy(() -> new AttackPayload(null, WeaponMethod.SLASH, AttackTemplate.QUICK_SLASH,
				entity("a"), ActionApproach.NORMAL, AttackPurpose.DAMAGE));
		assertThatIllegalArgumentException().isThrownBy(() -> new UseAbilityPayload("", ActionTarget.unspecified()));
		assertThatIllegalArgumentException().isThrownBy(() -> new UseItemPayload("item_a ", ActionTarget.unspecified()));
		assertThatIllegalArgumentException().isThrownBy(() -> new CarriedReference(CarriedKind.WEAPON, ""));
		assertThatNullPointerException().isThrownBy(() -> new AttackPayload("w", null, AttackTemplate.QUICK_SLASH,
				entity("a"), ActionApproach.NORMAL, AttackPurpose.DAMAGE));
	}
}
