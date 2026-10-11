package com.leeburke.springgame.action.resolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.StepRelation;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.mechanics.CheckResolver;

/**
 * Turns a validated intent into a {@link ResolvedOutcome}. Pure and deterministic: no persistence,
 * no Spring, no AI, no state mutation (it returns effects), and all randomness comes from the
 * supplied generator, consumed only by Stage 3 checks, once per rolled step, in step order.
 * <p>
 * Steps run in order. A player already at, or brought to, 0 HP cancels every later step
 * (PLAYER_DOWN). WHILE steps are unavailable. IF_PREVIOUS_SUCCEEDS runs only after a step resolved
 * as SUCCESS; START and THEN always run. Attack, defense, movement and communication resolve; other
 * action types are reported unavailable rather than inventing mechanics.
 */
public final class ActionEngine {

	private final AttackResolver attacks;
	private final DefenseResolver defenses;
	private final MovementResolver movement = new MovementResolver();
	private final InteractionResolver interactions = new InteractionResolver();

	public ActionEngine() {
		CheckResolver checks = new CheckResolver();
		this.attacks = new AttackResolver(checks);
		this.defenses = new DefenseResolver(checks);
	}

	/**
	 * @throws IllegalArgumentException if the resolution context is inconsistent with the context the
	 *                                  intent was validated against (an orchestration error)
	 */
	public ResolvedOutcome resolve(ValidatedActionIntent validated, ActionResolutionContext context, RandomGenerator rng) {
		Objects.requireNonNull(validated, "validated");
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(rng, "rng");
		ResolutionCoherence.check(validated, context);

		ActionIntent intent = validated.intent();
		List<StepOutcome> outcomes = new ArrayList<>();
		Set<String> resolvedAttacks = new HashSet<>();
		String currentZone = context.location().zoneId();
		int remainingHp = context.player().currentHp();
		boolean playerDown = remainingHp == 0;
		boolean leftScene = false;
		Map<String, Integer> targetHp = new HashMap<>(context.targetHitPoints());
		// Containers and tool-belt room as the steps play out, so "open it, then take what's inside" works.
		Map<String, com.leeburke.springgame.world.ContainerState> containers = new HashMap<>();
		context.scene().containers().forEach(c -> containers.put(c.objectId(), c));
		int[] beltRoom = { context.player().toolBelt().emptySlots() };

		for (ActionStep step : intent.steps()) {
			StepOutcome outcome;
			if (playerDown) {
				outcome = StepOutcome.cancelled(step.id(), step.actionType(), CancellationReason.PLAYER_DOWN);
			} else if (leftScene && !lookingAround(step)) {
				// Everything here refers to the scene left behind. Only a look around (no target) carries
				// on, in the new place: it reveals nothing and has no effect.
				outcome = StepOutcome.cancelled(step.id(), step.actionType(), CancellationReason.LEFT_SCENE);
			} else if (targetsFallen(step, targetHp)) {
				outcome = StepOutcome.cancelled(step.id(), step.actionType(), CancellationReason.TARGET_DEFEATED);
			} else if (step.relation() == StepRelation.WHILE) {
				outcome = StepOutcome.unavailable(step.id(), step.actionType(), UnavailableReason.SIMULTANEOUS_ACTION);
			} else if (step.relation() == StepRelation.IF_PREVIOUS_SUCCEEDS && !outcomes.getLast().succeeded()) {
				outcome = StepOutcome.cancelled(step.id(), step.actionType(), CancellationReason.PREVIOUS_STEP_NOT_SUCCESSFUL);
			} else {
				outcome = step.payload() instanceof ActionPayload.InteractPayload interact
						? interactions.resolve(step, interact, context.scene(), currentZone, containers, beltRoom[0])
						: resolveStep(step, intent.responseToAttack(), context, currentZone, resolvedAttacks, rng);
			}
			outcomes.add(outcome);

			for (OutcomeEffect effect : outcome.effects()) {
				switch (effect) {
					case OutcomeEffect.PlayerMoved moved -> currentZone = moved.toZone();
					case OutcomeEffect.PlayerDamaged damaged -> {
						remainingHp -= Math.min(remainingHp, damaged.hpDamage());
						playerDown = remainingHp == 0;
					}
					case OutcomeEffect.TargetDamaged damaged -> targetHp.computeIfPresent(damaged.entityId(),
							(id, hp) -> hp - Math.min(hp, damaged.hpDamage()));
					case OutcomeEffect.LeftScene left -> leftScene = true;
					case OutcomeEffect.ContainerOpened opened -> containers.computeIfPresent(opened.objectId(), (id, c) -> c.opened());
					case OutcomeEffect.ItemTaken taken -> {
						containers.computeIfPresent(taken.objectId(), (id, c) -> c.without(taken.itemCode()));
						beltRoom[0]--;
					}
				}
			}
		}

		int rolls = (int) outcomes.stream().filter(o -> o.check().isPresent()).count();
		return new ResolvedOutcome(ResolvedOutcome.CURRENT_SCHEMA_VERSION, intent.responseToAttack(),
				ResolvedOutcome.aggregate(outcomes), outcomes,
				new ResolutionMetadata(rolls, ResolutionMetadata.CURRENT_RULES_VERSION));
	}

	private StepOutcome resolveStep(ActionStep step, Optional<String> responseToAttack, ActionResolutionContext context,
			String currentZone, Set<String> resolvedAttacks, RandomGenerator rng) {
		return switch (step.payload()) {
			case ActionPayload.AttackPayload attack -> attacks.resolve(step, attack, context, rng);
			case ActionPayload.DefendPayload defense ->
					defenses.resolve(step, defense, responseToAttack, context, resolvedAttacks, rng);
			case ActionPayload.MovePayload move -> movement.resolve(step, move, context.scene(), currentZone);
			case ActionPayload.CommunicatePayload communicate -> StepOutcome.resolved(step.id(), ActionType.COMMUNICATE,
					StepSuccess.SUCCESS, Optional.empty(),
					new StepResult.CommunicationResult(communicate.kind(), addressee(communicate.target())), List.of());
			case ActionPayload.InteractPayload p -> notImplemented(step);
			case ActionPayload.ObservePayload observe -> StepOutcome.resolved(step.id(), ActionType.OBSERVE, StepSuccess.SUCCESS,
					Optional.empty(), new StepResult.ObservationResult(observe.kind()), List.of());
			case ActionPayload.UseAbilityPayload p -> notImplemented(step);
			case ActionPayload.UseItemPayload p -> notImplemented(step);
		};
	}

	/** An untargeted observation: taking in wherever the player now is. */
	private static boolean lookingAround(ActionStep step) {
		return step.payload() instanceof ActionPayload.ObservePayload observe && observe.target() instanceof ActionTarget.Unspecified;
	}

	/**
	 * True when the step attacks an entity that an earlier step in this intent brought to 0 HP. Only
	 * entities with known HP are tracked; damage is applied to state outside resolution.
	 */
	private static boolean targetsFallen(ActionStep step, Map<String, Integer> targetHp) {
		return step.payload() instanceof ActionPayload.AttackPayload attack
				&& attack.target() instanceof ActionTarget.EntityTarget entity
				&& targetHp.containsKey(entity.entityId())
				&& targetHp.get(entity.entityId()) == 0;
	}

	private static Optional<String> addressee(ActionTarget target) {
		return target instanceof ActionTarget.EntityTarget entity ? Optional.of(entity.entityId()) : Optional.empty();
	}

	private static StepOutcome notImplemented(ActionStep step) {
		return StepOutcome.unavailable(step.id(), step.actionType(), UnavailableReason.ACTION_NOT_IMPLEMENTED);
	}
}
