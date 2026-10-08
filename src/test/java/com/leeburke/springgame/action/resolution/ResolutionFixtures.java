package com.leeburke.springgame.action.resolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionFixtures;
import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionPayload.DefendPayload;
import com.leeburke.springgame.action.ActionPayload.MovePayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.EvadeType;
import com.leeburke.springgame.action.InterpretationConfidence;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.ParryContact;
import com.leeburke.springgame.action.RelativeGoal;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.TargetSpecificity;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.validation.ActionValidationContext;
import com.leeburke.springgame.action.validation.ActionValidator;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.character.Fated;
import com.leeburke.springgame.character.PlayerBody;
import com.leeburke.springgame.character.PlayerCharacterState;
import com.leeburke.springgame.character.ToolBelt;
import com.leeburke.springgame.content.AbilityDefinition;
import com.leeburke.springgame.content.PassiveDefinition;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatValue;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.HiddenContentRef;
import com.leeburke.springgame.world.PlayerLocation;
import com.leeburke.springgame.world.SceneEntity;
import com.leeburke.springgame.world.SceneExit;
import com.leeburke.springgame.world.SceneHazard;
import com.leeburke.springgame.world.SceneObject;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.ZoneConnection;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleEntity;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleHazard;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/**
 * Deterministic resolution fixtures.
 * <p>
 * Scene: zones entrance, aisle, vestry (visible) and crypt (hidden). Connections entrance-aisle
 * (visible), aisle-crypt (hidden) and entrance-vestry (hidden: the vestry is seen but its passage is
 * not known). Entity acolyte_1 in the aisle, hidden lurker_1 in the crypt, object pew_1, hazard
 * fire_1, exit north_door. The player stands at the entrance with MIGHT 8 (+2), AGILITY 7 (+1),
 * PERCEPTION 6 (0), ARCANA 5 (-1), RESOLVE 6 (0) and 20 of 20 HP, carrying a longsword (6 damage,
 * 3 trauma).
 */
final class ResolutionFixtures {

	static final String SWORD = ActionFixtures.SWORD;
	static final String ATTACK = ActionFixtures.INCOMING;
	static final String ACOLYTE = "acolyte_1";
	static final int SWORD_DAMAGE = 6;
	static final int SWORD_TRAUMA = 3;

	static final StatBlock STATS = new StatBlock(new StatValue(8), new StatValue(7), new StatValue(6), new StatValue(5),
			new StatValue(6));

	static final TargetCombatProfile ACOLYTE_PROFILE = new TargetCombatProfile(12, Effectiveness.NORMAL, 1, 0, 0, 0, 0);

	static final IncomingAttack INCOMING = new IncomingAttack(ATTACK, ACOLYTE, AttackTemplate.OVERHEAD_STRIKE, 12, 5, 3,
			Effectiveness.NORMAL, 0, 0, Optional.empty());

	private ResolutionFixtures() {
	}

	static PlayerSceneView view() {
		return new PlayerSceneView("entrance",
				List.of(new VisibleZone("entrance", "Nave Entrance"), new VisibleZone("aisle", "Side Aisle"),
						new VisibleZone("vestry", "Vestry")),
				List.of(new VisibleConnection("entrance", "aisle")),
				List.of(new VisibleEntity(ACOLYTE, "HOLLOW_ACOLYTE", "aisle")),
				List.of(new VisibleObject("pew_1", "WOODEN_PEW", "entrance")),
				List.of(new VisibleHazard("fire_1", "FIRE", "aisle")),
				List.of(new KnownExit("north_door", "entrance")),
				List.of());
	}

	static SceneState scene() {
		return new SceneState(
				List.of(new SceneZone("entrance", "Nave Entrance"), new SceneZone("aisle", "Side Aisle"),
						new SceneZone("vestry", "Vestry"), new SceneZone("crypt", "Crypt")),
				List.of(new ZoneConnection("c1", "entrance", "aisle"), new ZoneConnection("c2", "aisle", "crypt"),
						new ZoneConnection("c3", "entrance", "vestry")),
				List.of(new SceneEntity(ACOLYTE, "HOLLOW_ACOLYTE", "aisle"), new SceneEntity("lurker_1", "HOLLOW_ACOLYTE", "crypt")),
				List.of(new SceneObject("pew_1", "WOODEN_PEW", "entrance")),
				List.of(new SceneHazard("fire_1", "FIRE", "aisle")),
				List.of(new SceneExit("north_door", "entrance", UUID.fromString("00000000-0000-0000-0000-000000000002"))),
				List.of(), List.of(),
				List.of(new HiddenContentRef(HiddenContentKind.ZONE, "crypt"),
						new HiddenContentRef(HiddenContentKind.CONNECTION, "c2"),
						new HiddenContentRef(HiddenContentKind.CONNECTION, "c3"),
						new HiddenContentRef(HiddenContentKind.ENTITY, "lurker_1")),
				List.of());
	}

	static PlayerLocation location(String zoneId) {
		return new PlayerLocation(UUID.fromString("00000000-0000-0000-0000-000000000001"), zoneId);
	}

	static PlayerActionReferences references() {
		return ActionFixtures.references();
	}

	static PlayerCharacterState player(int currentHp, PlayerBody body) {
		return new PlayerCharacterState("Wren", STATS, new Fated(1), 20, currentHp, body,
				new PassiveDefinition("CALM", "Calm"), new AbilityDefinition("BLINK", "Blink"), new ToolBelt(List.of()));
	}

	static PlayerBody bodyWith(BodyPart part, BodySeverity severity) {
		Map<BodyPart, BodySeverity> severities = new EnumMap<>(PlayerBody.healthy().severities());
		severities.put(part, severity);
		return new PlayerBody(severities);
	}

	/** A mutable resolution setup; defaults are the fixture scene, player and acolyte profile. */
	static final class Setup {
		PlayerSceneView view = view();
		SceneState scene = scene();
		PlayerLocation location = location("entrance");
		PlayerCharacterState player = player(20, PlayerBody.healthy());
		Map<TargetProfileKey, TargetCombatProfile> profiles = new HashMap<>(
				Map.of(TargetProfileKey.wholeTarget(ACOLYTE), ACOLYTE_PROFILE));
		Map<String, IncomingAttack> attacks = new HashMap<>(Map.of(ATTACK, INCOMING));
		Map<String, Integer> hitPoints = new HashMap<>();

		ActionValidationContext validationContext() {
			return new ActionValidationContext(view, references(), attacks.keySet());
		}

		ActionResolutionContext resolutionContext() {
			return new ActionResolutionContext(player, scene, location, references(), profiles, attacks, hitPoints);
		}

		ValidatedActionIntent validate(ActionIntent intent) {
			Optional<ValidatedActionIntent> validated = new ActionValidator().validated(intent, validationContext());
			assertThat(validated).as("fixture intent must pass Stage 10 validation").isPresent();
			return validated.orElseThrow();
		}

		ResolvedOutcome resolve(ActionIntent intent, RandomGenerator rng) {
			return new ActionEngine().resolve(validate(intent), resolutionContext(), rng);
		}
	}

	// --- Intents ---

	static ActionIntent intent(ActionPayload... payloads) {
		return ActionFixtures.intent(payloads);
	}

	static ActionIntent defending(ActionPayload... payloads) {
		return ActionFixtures.intent(Optional.of(ATTACK), List.of(), payloads);
	}

	/** Steps s1..sn; the first is START, later ones use the given relations in order. */
	static ActionIntent related(Optional<String> response, List<StepRelation> laterRelations, ActionPayload... payloads) {
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < payloads.length; i++) {
			StepRelation relation = i == 0 ? StepRelation.START : laterRelations.get(i - 1);
			steps.add(new ActionStep("s" + (i + 1), i + 1, relation, payloads[i]));
		}
		return new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, response, steps, InterpretationConfidence.HIGH, List.of());
	}

	// --- Payloads ---

	static ActionTarget.EntityTarget acolyte() {
		return new ActionTarget.EntityTarget(ACOLYTE, Optional.empty(), TargetSpecificity.EXPLICIT);
	}

	static ActionTarget.EntityTarget acolyteAt(BodyPart part) {
		return new ActionTarget.EntityTarget(ACOLYTE, Optional.of(part), TargetSpecificity.EXPLICIT);
	}

	static AttackPayload attack(WeaponMethod method, ActionTarget target) {
		return new AttackPayload(SWORD, method, AttackTemplate.HORIZONTAL_SWING, target, ActionApproach.NORMAL,
				AttackPurpose.DAMAGE);
	}

	static AttackPayload slash() {
		return attack(WeaponMethod.SLASH, acolyte());
	}

	static DefendPayload defend(DefenseMethod method) {
		return new DefendPayload(method, EvadeType.UNSPECIFIED, ParryContact.UNSPECIFIED, ActionTarget.unspecified());
	}

	static DefendPayload evade() {
		return defend(DefenseMethod.EVADE);
	}

	static MovePayload moveTo(String zoneId) {
		return new MovePayload(MovementType.REPOSITION, new ActionTarget.ZoneTarget(zoneId, TargetSpecificity.EXPLICIT),
				RelativeGoal.NONE, ActionApproach.NORMAL);
	}

	static MovePayload hold() {
		return new MovePayload(MovementType.HOLD_POSITION, ActionTarget.unspecified(), RelativeGoal.NONE, ActionApproach.NORMAL);
	}

	/** Returns preset d20 faces from {@code nextInt(1, 21)} and records how many were drawn. */
	static final class FixedRolls implements RandomGenerator {

		private final Deque<Integer> faces = new ArrayDeque<>();
		private int drawn;

		FixedRolls(int... faces) {
			for (int face : faces) {
				this.faces.add(face);
			}
		}

		@Override
		public int nextInt(int origin, int bound) {
			if (origin != 1 || bound != 21) {
				throw new AssertionError("Only d20 rolls are expected, got [" + origin + ", " + bound + ")");
			}
			if (faces.isEmpty()) {
				throw new AssertionError("More rolls drawn than the test supplied");
			}
			drawn++;
			return faces.poll();
		}

		@Override
		public long nextLong() {
			throw new AssertionError("Resolution must draw only d20 rolls");
		}

		int drawn() {
			return drawn;
		}
	}
}
