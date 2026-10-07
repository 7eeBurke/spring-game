package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.function.Function;

import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.validation.ActionValidationContext;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.world.HiddenContentKind;
import com.leeburke.springgame.world.SceneState;
import com.leeburke.springgame.world.SceneZone;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Backend-only guard that the player-safe context an intent was validated against agrees with the
 * authoritative resolution context. A mismatch is an orchestration error, never a gameplay result,
 * so it throws before any random number is drawn. Nothing here reaches the intent or the player.
 */
final class ResolutionCoherence {

	private ResolutionCoherence() {
	}

	static void check(ValidatedActionIntent validated, ActionResolutionContext context) {
		ActionValidationContext validation = validated.context();
		require(validation.references().equals(context.references()),
				"player references differ between validation and resolution");
		require(validation.incomingAttacks().equals(context.incomingAttacks().keySet()),
				"incoming attacks differ between validation and resolution");
		PlayerSceneView view = validation.view();
		require(view.currentZoneId().equals(context.location().zoneId()),
				"validated current zone differs from the player location");
		for (ActionStep step : validated.intent().steps()) {
			for (ActionTarget target : targets(step.payload())) {
				checkTarget(target, view, context.scene());
			}
		}
	}

	private static void checkTarget(ActionTarget target, PlayerSceneView view, SceneState scene) {
		switch (target) {
			case ActionTarget.EntityTarget t -> {
				PlayerSceneView.VisibleEntity seen = visible(view.entities(), PlayerSceneView.VisibleEntity::id, t.entityId());
				require(!scene.isHidden(HiddenContentKind.ENTITY, t.entityId())
						&& scene.entities().stream().anyMatch(e -> e.id().equals(seen.id())
								&& e.definitionCode().equals(seen.definitionCode()) && e.zoneId().equals(seen.zoneId())),
						"entity reference does not match the authoritative scene");
			}
			case ActionTarget.ObjectTarget t -> {
				PlayerSceneView.VisibleObject seen = visible(view.objects(), PlayerSceneView.VisibleObject::id, t.objectId());
				require(!scene.isHidden(HiddenContentKind.OBJECT, t.objectId())
						&& scene.objects().stream().anyMatch(o -> o.id().equals(seen.id())
								&& o.definitionCode().equals(seen.definitionCode()) && o.zoneId().equals(seen.zoneId())),
						"object reference does not match the authoritative scene");
			}
			case ActionTarget.HazardTarget t -> {
				PlayerSceneView.VisibleHazard seen = visible(view.hazards(), PlayerSceneView.VisibleHazard::id, t.hazardId());
				require(!scene.isHidden(HiddenContentKind.HAZARD, t.hazardId())
						&& scene.hazards().stream().anyMatch(h -> h.id().equals(seen.id())
								&& h.definitionCode().equals(seen.definitionCode()) && h.zoneId().equals(seen.zoneId())),
						"hazard reference does not match the authoritative scene");
			}
			case ActionTarget.ZoneTarget t -> {
				PlayerSceneView.VisibleZone seen = visible(view.zones(), PlayerSceneView.VisibleZone::id, t.zoneId());
				require(!scene.isHidden(HiddenContentKind.ZONE, t.zoneId())
						&& scene.zones().stream().anyMatch(z -> zoneMatches(z, seen)),
						"zone reference does not match the authoritative scene");
			}
			case ActionTarget.ExitTarget t -> {
				PlayerSceneView.KnownExit seen = visible(view.exits(), PlayerSceneView.KnownExit::id, t.exitId());
				require(!scene.isHidden(HiddenContentKind.EXIT, t.exitId())
						&& scene.exits().stream().anyMatch(x -> x.id().equals(seen.id()) && x.zoneId().equals(seen.zoneId())),
						"exit reference does not match the authoritative scene");
			}
			case ActionTarget.SelfTarget t -> {
				// The player, not scene content.
			}
			case ActionTarget.Unspecified t -> {
				// No reference.
			}
		}
	}

	private static boolean zoneMatches(SceneZone zone, PlayerSceneView.VisibleZone seen) {
		return zone.id().equals(seen.id()) && zone.displayName().equals(seen.displayName());
	}

	private static <T> T visible(List<T> items, Function<T, String> id, String wanted) {
		return items.stream().filter(item -> id.apply(item).equals(wanted)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Validated intent references content absent from its own view"));
	}

	static List<ActionTarget> targets(ActionPayload payload) {
		return switch (payload) {
			case ActionPayload.AttackPayload p -> List.of(p.target());
			case ActionPayload.DefendPayload p -> List.of(p.cover());
			case ActionPayload.MovePayload p -> List.of(p.target());
			case ActionPayload.InteractPayload p -> List.of(p.target());
			case ActionPayload.ObservePayload p -> List.of(p.target());
			case ActionPayload.UseAbilityPayload p -> List.of(p.target());
			case ActionPayload.UseItemPayload p -> List.of(p.target());
			case ActionPayload.CommunicatePayload p -> List.of(p.target());
		};
	}

	private static void require(boolean condition, String problem) {
		if (!condition) {
			throw new IllegalArgumentException("Resolution context is inconsistent with validation: " + problem);
		}
	}
}
