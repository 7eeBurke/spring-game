package com.leeburke.springgame.action;

import java.util.Objects;
import java.util.Optional;

/**
 * The action-specific part of a step. The payload type determines the {@link ActionType}, so a
 * step can never pair one action type with another type's payload.
 * <p>
 * Constructors reject missing fields and combinations that can never make sense. Requirements that
 * depend on the target (for example "INSPECT needs a target") are reported by the validator as
 * {@code SCHEMA_INVALID} instead, so incomplete interpretations produce a result, not an exception.
 * Payloads describe intent only: no stat, DC, suitability, roll or outcome.
 */
public sealed interface ActionPayload {

	ActionType type();

	/** Attack with an owned weapon. Unarmed attacks are not part of the current vocabulary. */
	record AttackPayload(
			String weaponRef,
			WeaponMethod method,
			AttackTemplate template,
			ActionTarget target,
			ActionApproach approach,
			AttackPurpose purpose) implements ActionPayload {
		public AttackPayload {
			Refs.require(weaponRef, "Weapon reference");
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(template, "template");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(approach, "approach");
			Objects.requireNonNull(purpose, "purpose");
		}

		@Override
		public ActionType type() {
			return ActionType.ATTACK;
		}
	}

	/**
	 * Immediate defence. The incoming attack is referenced only by
	 * {@link ActionIntent#responseToAttack()}.
	 */
	record DefendPayload(
			DefenseMethod method,
			EvadeType evadeType,
			ParryContact parryContact,
			ActionTarget cover) implements ActionPayload {
		public DefendPayload {
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(evadeType, "evadeType");
			Objects.requireNonNull(parryContact, "parryContact");
			Objects.requireNonNull(cover, "cover");
			if (method != DefenseMethod.EVADE && evadeType != EvadeType.UNSPECIFIED) {
				throw new IllegalArgumentException("An evade type is only meaningful for EVADE");
			}
			if (method != DefenseMethod.PARRY && parryContact != ParryContact.UNSPECIFIED) {
				throw new IllegalArgumentException("A parry contact is only meaningful for PARRY");
			}
			if (method != DefenseMethod.TAKE_COVER && !(cover instanceof ActionTarget.Unspecified)) {
				throw new IllegalArgumentException("A cover target is only meaningful for TAKE_COVER");
			}
			if (!(cover instanceof ActionTarget.Unspecified || cover instanceof ActionTarget.ObjectTarget)) {
				throw new IllegalArgumentException("Cover must be an object or unspecified");
			}
		}

		@Override
		public ActionType type() {
			return ActionType.DEFEND;
		}
	}

	record MovePayload(
			MovementType movementType,
			ActionTarget target,
			RelativeGoal goal,
			ActionApproach approach) implements ActionPayload {
		public MovePayload {
			Objects.requireNonNull(movementType, "movementType");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(goal, "goal");
			Objects.requireNonNull(approach, "approach");
			if (movementType == MovementType.HOLD_POSITION
					&& (!(target instanceof ActionTarget.Unspecified) || goal != RelativeGoal.NONE)) {
				throw new IllegalArgumentException("HOLD_POSITION has no target or goal");
			}
			if (goal == RelativeGoal.COVER && movementType != MovementType.REPOSITION) {
				throw new IllegalArgumentException("Moving into cover is REPOSITION with goal COVER");
			}
		}

		@Override
		public ActionType type() {
			return ActionType.MOVE;
		}
	}

	/** Interaction; {@code carried} names an owned weapon or item used, required for DROP and PLACE. */
	record InteractPayload(
			InteractionKind kind,
			ActionTarget target,
			Optional<CarriedReference> carried,
			ActionApproach approach) implements ActionPayload {
		public InteractPayload {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(carried, "carried");
			Objects.requireNonNull(approach, "approach");
			if ((kind == InteractionKind.DROP || kind == InteractionKind.PLACE) && carried.isEmpty()) {
				throw new IllegalArgumentException(kind + " needs the carried weapon or item");
			}
		}

		@Override
		public ActionType type() {
			return ActionType.INTERACT;
		}
	}

	record ObservePayload(ObservationKind kind, ActionTarget target) implements ActionPayload {
		public ObservePayload {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(target, "target");
		}

		@Override
		public ActionType type() {
			return ActionType.OBSERVE;
		}
	}

	record UseAbilityPayload(String abilityRef, ActionTarget target) implements ActionPayload {
		public UseAbilityPayload {
			Refs.require(abilityRef, "Ability reference");
			Objects.requireNonNull(target, "target");
		}

		@Override
		public ActionType type() {
			return ActionType.USE_ABILITY;
		}
	}

	record UseItemPayload(String itemRef, ActionTarget target) implements ActionPayload {
		public UseItemPayload {
			Refs.require(itemRef, "Item reference");
			Objects.requireNonNull(target, "target");
		}

		@Override
		public ActionType type() {
			return ActionType.USE_ITEM;
		}
	}

	/** Speech. Every communication kind is spoken, so content is required. No stat is implied. */
	record CommunicatePayload(CommunicationKind kind, String content, ActionTarget target) implements ActionPayload {
		public CommunicatePayload {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(content, "content");
			if (content.isBlank()) {
				throw new IllegalArgumentException("Communication content must not be blank");
			}
			Objects.requireNonNull(target, "target");
		}

		@Override
		public ActionType type() {
			return ActionType.COMMUNICATE;
		}
	}
}
