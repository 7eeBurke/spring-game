package com.leeburke.springgame.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionPayload.ObservePayload;
import com.leeburke.springgame.action.ActionTarget.EntityTarget;
import com.leeburke.springgame.action.validation.ActionValidationContext;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.GameContentLoader;
import com.leeburke.springgame.world.view.PlayerSceneView;
import com.leeburke.springgame.world.view.PlayerSceneView.KnownExit;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleConnection;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleEntity;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleHazard;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleObject;
import com.leeburke.springgame.world.view.PlayerSceneView.VisibleZone;

/** Deterministic action-contract fixtures. */
public final class ActionFixtures {

	public static final GameContentCatalog CONTENT = GameContentLoader.loadBundled();

	public static final String SWORD = "weapon_a";
	public static final String SALVE = "item_a";
	public static final String CROWBAR = "item_b";
	public static final String SPARE_SALVE = "item_c";
	public static final String STONEBLOOD = "ability_a";
	public static final String INCOMING = "attack_1";

	private ActionFixtures() {
	}

	/** Zones entrance and aisle; entity acolyte_1, object pew_1, hazard fire_1, exit north_door. */
	public static PlayerSceneView view() {
		return new PlayerSceneView("entrance",
				List.of(new VisibleZone("entrance", "Nave Entrance"), new VisibleZone("aisle", "Side Aisle")),
				List.of(new VisibleConnection("entrance", "aisle")),
				List.of(new VisibleEntity("acolyte_1", "HOLLOW_ACOLYTE", "aisle")),
				List.of(new VisibleObject("pew_1", "WOODEN_PEW", "entrance")),
				List.of(new VisibleHazard("fire_1", "FIRE", "aisle")),
				List.of(new KnownExit("north_door", "entrance")),
				List.of("ALTAR_IS_HOLLOW"));
	}

	public static PlayerActionReferences references() {
		return new PlayerActionReferences(
				Map.of(SWORD, CONTENT.findWeapon("LONGSWORD").orElseThrow()),
				Map.of(STONEBLOOD, CONTENT.findAbility("STONEBLOOD").orElseThrow()),
				Map.of(SALVE, CONTENT.findItem("RESTORATIVE_SALVE").orElseThrow(),
						CROWBAR, CONTENT.findItem("CROWBAR").orElseThrow(),
						SPARE_SALVE, CONTENT.findItem("RESTORATIVE_SALVE").orElseThrow()));
	}

	public static ActionValidationContext context() {
		return new ActionValidationContext(view(), references(), Set.of(INCOMING));
	}

	public static ActionValidationContext context(PlayerSceneView view) {
		return new ActionValidationContext(view, references(), Set.of(INCOMING));
	}

	public static EntityTarget entity(String id) {
		return new EntityTarget(id, Optional.empty(), TargetSpecificity.EXPLICIT);
	}

	public static AttackPayload attack(ActionTarget target) {
		return new AttackPayload(SWORD, WeaponMethod.SLASH, AttackTemplate.HORIZONTAL_SWING, target,
				ActionApproach.NORMAL, AttackPurpose.DAMAGE);
	}

	public static ObservePayload search() {
		return new ObservePayload(ObservationKind.SEARCH, ActionTarget.unspecified());
	}

	/** Builds an intent with steps s1, s2, ... in order (START then THEN). */
	public static ActionIntent intent(ActionPayload... payloads) {
		return intent(Optional.empty(), List.of(), payloads);
	}

	public static ActionIntent intent(Optional<String> responseToAttack, List<UnresolvedReference> unresolved,
			ActionPayload... payloads) {
		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < payloads.length; i++) {
			steps.add(new ActionStep("s" + (i + 1), i + 1, i == 0 ? StepRelation.START : StepRelation.THEN, payloads[i]));
		}
		return new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, responseToAttack, steps,
				InterpretationConfidence.HIGH, unresolved);
	}
}
