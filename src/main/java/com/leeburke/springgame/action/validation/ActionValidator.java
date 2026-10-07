package com.leeburke.springgame.action.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
import com.leeburke.springgame.action.InteractionKind;
import com.leeburke.springgame.action.MovementType;
import com.leeburke.springgame.action.ObservationKind;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.world.view.PlayerSceneView;

/**
 * Validates an {@link ActionIntent} against an {@link ActionValidationContext}. Pure and
 * deterministic: no randomness, no rolls, no mechanics, no mutation of the intent.
 * <p>
 * Order: unsupported schema version (stops), the incoming-attack reference, unresolved
 * references, then each step in order through four layers: structure ({@code SCHEMA_INVALID}),
 * scene references against the player view only ({@code UNKNOWN_SCENE_REFERENCE}), ownership
 * ({@code UNKNOWN_PLAYER_REFERENCE}) and physical plausibility. Plausibility is consulted only for
 * a step with no earlier errors and no unresolved reference (an unresolved reference without a
 * step blocks it for every step). All errors are collected for later repair.
 */
public final class ActionValidator {

	private final PhysicalPlausibilityPolicy plausibility;

	public ActionValidator() {
		this(PhysicalPlausibilityPolicy.NO_PROVEN_IMPOSSIBILITIES);
	}

	public ActionValidator(PhysicalPlausibilityPolicy plausibility) {
		this.plausibility = Objects.requireNonNull(plausibility, "plausibility");
	}

	/**
	 * The intent wrapped as proof of validation, or empty if {@link #validate} reports any error.
	 * Action resolution accepts only this type.
	 */
	public Optional<ValidatedActionIntent> validated(ActionIntent intent, ActionValidationContext context) {
		return validate(intent, context).valid()
				? Optional.of(new ValidatedActionIntent(intent, context))
				: Optional.empty();
	}

	public ActionValidationResult validate(ActionIntent intent, ActionValidationContext context) {
		Objects.requireNonNull(intent, "intent");
		Objects.requireNonNull(context, "context");
		List<ActionValidationError> errors = new ArrayList<>();

		if (intent.schemaVersion() != ActionIntent.CURRENT_SCHEMA_VERSION) {
			errors.add(error(ActionValidationCode.UNSUPPORTED_SCHEMA_VERSION, null,
					"Unsupported action schema version " + intent.schemaVersion()));
			return new ActionValidationResult(errors);
		}

		intent.responseToAttack().ifPresent(attack -> {
			if (!context.incomingAttacks().contains(attack)) {
				errors.add(error(ActionValidationCode.UNKNOWN_INCOMING_ATTACK, null, "Unknown incoming attack '" + attack + "'"));
			}
		});

		boolean unresolvedWithoutStep = false;
		Set<String> unresolvedSteps = new HashSet<>();
		for (UnresolvedReference unresolved : intent.unresolvedReferences()) {
			errors.add(error(ActionValidationCode.UNRESOLVED_REFERENCE, unresolved.stepId().orElse(null),
					"Could not resolve '" + unresolved.phrase() + "'"));
			unresolved.stepId().ifPresent(unresolvedSteps::add);
			unresolvedWithoutStep |= unresolved.stepId().isEmpty();
		}

		for (ActionStep step : intent.steps()) {
			List<ActionValidationError> stepErrors = new ArrayList<>();
			checkStructure(step, stepErrors);
			checkSceneReferences(step, context.view(), stepErrors);
			checkOwnership(step, context.references(), stepErrors);
			boolean blockedByUnresolved = unresolvedWithoutStep || unresolvedSteps.contains(step.id());
			if (stepErrors.isEmpty() && !blockedByUnresolved) {
				plausibility.impossibility(step, context).ifPresent(reason ->
						stepErrors.add(error(ActionValidationCode.ACTION_PHYSICALLY_IMPOSSIBLE, step.id(), reason)));
			}
			errors.addAll(stepErrors);
		}
		return new ActionValidationResult(errors);
	}

	// --- Layer 1: structure that depends on the combination of payload and target ---

	private static void checkStructure(ActionStep step, List<ActionValidationError> errors) {
		String problem = switch (step.payload()) {
			case AttackPayload attack -> requireTarget(attack.target(), "ATTACK",
					ActionTarget.EntityTarget.class, ActionTarget.ObjectTarget.class, ActionTarget.HazardTarget.class);
			case DefendPayload defend -> null;
			case MovePayload move -> checkMove(move);
			case InteractPayload interact -> checkInteract(interact);
			case ObservePayload observe -> checkObserve(observe);
			// Ability and item use may target anything visible, the player, or nothing.
			case UseAbilityPayload use -> null;
			case UseItemPayload use -> null;
			case CommunicatePayload communicate -> communicate.target() instanceof ActionTarget.EntityTarget
					|| communicate.target() instanceof ActionTarget.Unspecified
							? null
							: "COMMUNICATE can only address a creature or no one in particular";
		};
		if (problem != null) {
			errors.add(error(ActionValidationCode.SCHEMA_INVALID, step.id(), problem));
		}
	}

	private static String checkMove(MovePayload move) {
		ActionTarget target = move.target();
		if (target instanceof ActionTarget.SelfTarget) {
			return "MOVE cannot target the player";
		}
		if (move.movementType() == MovementType.CLOSE_DISTANCE) {
			return requireTarget(target, "CLOSE_DISTANCE", ActionTarget.EntityTarget.class, ActionTarget.ObjectTarget.class);
		}
		if (target instanceof ActionTarget.ExitTarget) {
			boolean leaving = move.movementType() == MovementType.ADVANCE || move.movementType() == MovementType.RETREAT
					|| move.movementType() == MovementType.DISENGAGE;
			return leaving ? null : "Only ADVANCE, RETREAT or DISENGAGE can go through an exit";
		}
		return null;
	}

	private static String checkInteract(InteractPayload interact) {
		ActionTarget target = interact.target();
		if (target instanceof ActionTarget.SelfTarget) {
			return "INTERACT cannot target the player";
		}
		if (interact.kind() == InteractionKind.PICK_UP) {
			return requireTarget(target, "PICK_UP", ActionTarget.ObjectTarget.class);
		}
		if (interact.kind() != InteractionKind.DROP && target instanceof ActionTarget.Unspecified) {
			return interact.kind() + " needs a target";
		}
		return null;
	}

	private static String checkObserve(ObservePayload observe) {
		if (observe.kind() == ObservationKind.INSPECT) {
			return observe.target() instanceof ActionTarget.Unspecified ? "INSPECT needs a target" : null;
		}
		return observe.target() instanceof ActionTarget.SelfTarget ? observe.kind() + " cannot target the player" : null;
	}

	@SafeVarargs
	private static String requireTarget(ActionTarget target, String action, Class<? extends ActionTarget>... allowed) {
		for (Class<? extends ActionTarget> type : allowed) {
			if (type.isInstance(target)) {
				return null;
			}
		}
		return action + " needs a target of kind " + kindNames(allowed);
	}

	@SafeVarargs
	private static String kindNames(Class<? extends ActionTarget>... types) {
		List<String> names = new ArrayList<>();
		for (Class<? extends ActionTarget> type : types) {
			names.add(type.getSimpleName().replace("Target", "").toUpperCase(Locale.ROOT));
		}
		return String.join(", ", names);
	}

	// --- Layer 2: scene references, against the player-safe view only ---

	private static void checkSceneReferences(ActionStep step, PlayerSceneView view, List<ActionValidationError> errors) {
		for (ActionTarget target : targets(step.payload())) {
			Optional<String> unknown = switch (target) {
				case ActionTarget.EntityTarget t -> missing(t.entityId(), view.entities(), PlayerSceneView.VisibleEntity::id, "entity");
				case ActionTarget.ObjectTarget t -> missing(t.objectId(), view.objects(), PlayerSceneView.VisibleObject::id, "object");
				case ActionTarget.HazardTarget t -> missing(t.hazardId(), view.hazards(), PlayerSceneView.VisibleHazard::id, "hazard");
				case ActionTarget.ZoneTarget t -> missing(t.zoneId(), view.zones(), PlayerSceneView.VisibleZone::id, "zone");
				case ActionTarget.ExitTarget t -> missing(t.exitId(), view.exits(), PlayerSceneView.KnownExit::id, "exit");
				case ActionTarget.SelfTarget t -> Optional.empty();
				case ActionTarget.Unspecified t -> Optional.empty();
			};
			unknown.ifPresent(message -> errors.add(error(ActionValidationCode.UNKNOWN_SCENE_REFERENCE, step.id(), message)));
		}
	}

	private static <T> Optional<String> missing(String id, List<T> visible, Function<T, String> idOf, String kind) {
		boolean known = visible.stream().map(idOf).anyMatch(id::equals);
		return known ? Optional.empty() : Optional.of("Unknown " + kind + " reference '" + id + "'");
	}

	private static List<ActionTarget> targets(ActionPayload payload) {
		return switch (payload) {
			case AttackPayload p -> List.of(p.target());
			case DefendPayload p -> List.of(p.cover());
			case MovePayload p -> List.of(p.target());
			case InteractPayload p -> List.of(p.target());
			case ObservePayload p -> List.of(p.target());
			case UseAbilityPayload p -> List.of(p.target());
			case UseItemPayload p -> List.of(p.target());
			case CommunicatePayload p -> List.of(p.target());
		};
	}

	// --- Layer 3: ownership, against the player's current references only ---

	private static void checkOwnership(ActionStep step, PlayerActionReferences refs, List<ActionValidationError> errors) {
		switch (step.payload()) {
			case AttackPayload p -> requireOwned(refs.weapons().containsKey(p.weaponRef()), "weapon", p.weaponRef(), step, errors);
			case UseAbilityPayload p -> requireOwned(refs.abilities().containsKey(p.abilityRef()), "ability", p.abilityRef(), step, errors);
			case UseItemPayload p -> requireOwned(refs.items().containsKey(p.itemRef()), "item", p.itemRef(), step, errors);
			case InteractPayload p -> p.carried().ifPresent(carried -> requireCarried(carried, refs, step, errors));
			default -> {
				// No owned references.
			}
		}
	}

	private static void requireCarried(CarriedReference carried, PlayerActionReferences refs, ActionStep step,
			List<ActionValidationError> errors) {
		boolean owned = carried.kind() == CarriedKind.WEAPON
				? refs.weapons().containsKey(carried.ref())
				: refs.items().containsKey(carried.ref());
		requireOwned(owned, carried.kind() == CarriedKind.WEAPON ? "weapon" : "item", carried.ref(), step, errors);
	}

	private static void requireOwned(boolean owned, String kind, String ref, ActionStep step, List<ActionValidationError> errors) {
		if (!owned) {
			errors.add(error(ActionValidationCode.UNKNOWN_PLAYER_REFERENCE, step.id(), "Unknown " + kind + " reference '" + ref + "'"));
		}
	}

	private static ActionValidationError error(ActionValidationCode code, String stepId, String message) {
		return new ActionValidationError(code, Optional.ofNullable(stepId), message);
	}
}
