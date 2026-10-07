package com.leeburke.springgame.action.resolution;

import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ACOLYTE;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.ATTACK;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.acolyte;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.attack;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.defending;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.evade;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.intent;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.moveTo;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.references;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.scene;
import static com.leeburke.springgame.action.resolution.ResolutionFixtures.slash;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.FixedRolls;
import com.leeburke.springgame.action.resolution.ResolutionFixtures.Setup;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneHazard;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;

class EngineBoundaryTest {

	private final Setup setup = new Setup();

	// --- Validation boundary ---

	@Test
	void invalidIntentNeverBecomesAValidatedIntent() {
		ActionIntent unknownTarget = intent(attack(WeaponMethod.SLASH,
				new ActionTarget.EntityTarget("ghost_9", Optional.empty(), TargetSpecificity.EXPLICIT)));

		assertThat(new ActionValidator().validated(unknownTarget, setup.validationContext())).isEmpty();
	}

	@Test
	void validIntentKeepsItsIntentAndContext() {
		ActionIntent intent = intent(slash());
		ValidatedActionIntent validated = setup.validate(intent);

		assertThat(validated.intent()).isEqualTo(intent);
		assertThat(validated.context()).isEqualTo(setup.validationContext());
	}

	@Test
	void sameInputsAndSeedGiveEqualOutcomes() {
		ActionIntent intent = intent(slash(), moveTo("aisle"), slash());
		ResolvedOutcome first = setup.resolve(intent, new SplittableRandom(99));
		ResolvedOutcome second = setup.resolve(intent, new SplittableRandom(99));

		assertThat(second).isEqualTo(first);
		assertThat(first.metadata()).isEqualTo(new ResolutionMetadata(2, ResolutionMetadata.CURRENT_RULES_VERSION));
		assertThat(first.schemaVersion()).isEqualTo(ResolvedOutcome.CURRENT_SCHEMA_VERSION);
	}

	@Test
	void outcomeEchoesTheAttackItResponds() {
		assertThat(setup.resolve(defending(evade()), new FixedRolls(11)).responseToAttack()).contains(ATTACK);
		assertThat(setup.resolve(intent(slash()), new FixedRolls(11)).responseToAttack()).isEmpty();
	}

	// --- Coherence between validation and resolution (all before any roll) ---

	@Test
	void alignedContextsResolve() {
		assertThat(setup.resolve(intent(slash()), new FixedRolls(11)).overall()).isEqualTo(OverallResult.COMPLETE_SUCCESS);
	}

	@Test
	void differentPlayerReferencesAreRejected() {
		PlayerActionReferences other = new PlayerActionReferences(
				Map.of(ActionFixtures.SWORD, ActionFixtures.CONTENT.findWeapon("WAR_HAMMER").orElseThrow()),
				references().abilities(), references().items());
		ActionResolutionContext context = new ActionResolutionContext(setup.player, setup.scene, setup.location, other,
				setup.profiles, setup.attacks);

		assertRejected(intent(slash()), context);
	}

	@Test
	void differentIncomingAttacksAreRejected() {
		ActionResolutionContext context = new ActionResolutionContext(setup.player, setup.scene, setup.location,
				references(), setup.profiles, Map.of());

		assertRejected(intent(slash()), context);
	}

	@Test
	void validatedZoneDifferentFromPlayerLocationIsRejected() {
		ActionResolutionContext context = new ActionResolutionContext(setup.player, setup.scene,
				ResolutionFixtures.location("aisle"), references(), setup.profiles, setup.attacks);

		assertRejected(intent(slash()), context);
	}

	@Test
	void entityMissingFromTheSceneIsRejected() {
		assertRejected(intent(slash()), sceneWith(s -> withEntities(s, List.of())));
	}

	@Test
	void hiddenEntityIsRejected() {
		assertRejected(intent(slash()), sceneWith(s -> withHidden(s, new HiddenContentRef(HiddenContentKind.ENTITY, ACOLYTE))));
	}

	@Test
	void entityWithADifferentDefinitionOrZoneIsRejected() {
		assertRejected(intent(slash()), sceneWith(s -> withEntities(s, List.of(new SceneEntity(ACOLYTE, "BONE_KNIGHT", "aisle")))));
		assertRejected(intent(slash()),
				sceneWith(s -> withEntities(s, List.of(new SceneEntity(ACOLYTE, "HOLLOW_ACOLYTE", "entrance")))));
	}

	@Test
	void objectMismatchIsRejected() {
		ActionIntent push = intent(new ActionPayload.InteractPayload(InteractionKind.PUSH,
				new ActionTarget.ObjectTarget("pew_1", TargetSpecificity.EXPLICIT), Optional.empty(), ActionApproach.NORMAL));

		assertRejected(push, sceneWith(s -> new SceneState(s.zones(), s.connections(), s.entities(),
				List.of(new SceneObject("pew_1", "ALTAR", "entrance")), s.hazards(), s.exits(), s.activeEvents(),
				s.environmentFlags(), s.hiddenContent(), s.discoveredFacts())));
	}

	@Test
	void hazardMismatchIsRejected() {
		ActionIntent inspect = intent(new ActionPayload.ObservePayload(com.leeburke.springgame.action.ObservationKind.INSPECT,
				new ActionTarget.HazardTarget("fire_1", TargetSpecificity.EXPLICIT)));

		assertRejected(inspect, sceneWith(s -> new SceneState(s.zones(), s.connections(), s.entities(), s.objects(),
				List.of(new SceneHazard("fire_1", "FIRE", "entrance")), s.exits(), s.activeEvents(),
				s.environmentFlags(), s.hiddenContent(), s.discoveredFacts())));
	}

	@Test
	void zoneMismatchIsRejected() {
		assertRejected(intent(moveTo("aisle")), sceneWith(s -> {
			List<SceneZone> zones = new ArrayList<>(s.zones());
			zones.set(1, new SceneZone("aisle", "Collapsed Aisle"));
			return new SceneState(zones, s.connections(), s.entities(), s.objects(), s.hazards(), s.exits(),
					s.activeEvents(), s.environmentFlags(), s.hiddenContent(), s.discoveredFacts());
		}));
		assertRejected(intent(moveTo("aisle")), sceneWith(s -> withHidden(s, new HiddenContentRef(HiddenContentKind.ZONE, "aisle"))));
	}

	@Test
	void exitMismatchIsRejected() {
		ActionIntent leave = intent(new ActionPayload.MovePayload(MovementType.ADVANCE,
				new ActionTarget.ExitTarget("north_door", TargetSpecificity.EXPLICIT), RelativeGoal.NONE, ActionApproach.NORMAL));

		assertRejected(leave, sceneWith(s -> new SceneState(s.zones(), s.connections(), s.entities(), s.objects(),
				s.hazards(), List.of(new SceneExit("north_door", "aisle", UUID.randomUUID())), s.activeEvents(),
				s.environmentFlags(), s.hiddenContent(), s.discoveredFacts())));
	}

	@Test
	void referencesTheIntentDoesNotUseAreNotCompared() {
		setup.scene = withEntities(scene(), List.of(new SceneEntity(ACOLYTE, "HOLLOW_ACOLYTE", "aisle")));
		setup.scene = new SceneState(setup.scene.zones(), setup.scene.connections(), setup.scene.entities(),
				List.of(new SceneObject("pew_1", "ALTAR", "entrance")), setup.scene.hazards(), setup.scene.exits(),
				setup.scene.activeEvents(), setup.scene.environmentFlags(),
				List.of(new HiddenContentRef(HiddenContentKind.ZONE, "crypt"), new HiddenContentRef(HiddenContentKind.CONNECTION, "c2"),
						new HiddenContentRef(HiddenContentKind.CONNECTION, "c3")),
				setup.scene.discoveredFacts());

		assertThat(setup.resolve(intent(slash()), new FixedRolls(11)).overall()).isEqualTo(OverallResult.COMPLETE_SUCCESS);
	}

	@Test
	void communicationAddresseeIsCheckedToo() {
		ActionIntent speak = intent(new ActionPayload.CommunicatePayload(com.leeburke.springgame.action.CommunicationKind.SAY,
				"Who goes there?", acolyte()));

		assertRejected(speak, sceneWith(s -> withEntities(s, List.of())));
	}

	private ActionResolutionContext sceneWith(UnaryOperator<SceneState> change) {
		return new ActionResolutionContext(setup.player, change.apply(scene()), setup.location, references(), setup.profiles,
				setup.attacks);
	}

	private void assertRejected(ActionIntent intent, ActionResolutionContext context) {
		ValidatedActionIntent validated = setup.validate(intent);
		FixedRolls rolls = new FixedRolls(11);

		assertThatIllegalArgumentException().isThrownBy(() -> new ActionEngine().resolve(validated, context, rolls));
		assertThat(rolls.drawn()).isZero();
	}

	private static SceneState withEntities(SceneState s, List<SceneEntity> entities) {
		List<HiddenContentRef> hidden = s.hiddenContent().stream()
				.filter(ref -> ref.kind() != HiddenContentKind.ENTITY
						|| entities.stream().anyMatch(e -> e.id().equals(ref.localId())))
				.toList();
		return new SceneState(s.zones(), s.connections(), entities, s.objects(), s.hazards(), s.exits(), s.activeEvents(),
				s.environmentFlags(), hidden, s.discoveredFacts());
	}

	private static SceneState withHidden(SceneState s, HiddenContentRef ref) {
		List<HiddenContentRef> hidden = new ArrayList<>(s.hiddenContent());
		hidden.add(ref);
		return new SceneState(s.zones(), s.connections(), s.entities(), s.objects(), s.hazards(), s.exits(), s.activeEvents(),
				s.environmentFlags(), hidden, s.discoveredFacts());
	}
}
