package com.leeburke.springgame.ai.interpreter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.leeburke.springgame.action.ActionApproach;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.CarriedKind;
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
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.BodyPart;

/**
 * The Action Interpreter's output document (schema version 1): the Stage 10 vocabulary with
 * request-scoped aliases instead of references. It has no mechanical fields at all (no stat,
 * suitability, DC, roll, success, damage or similar), so a model cannot submit any.
 * <p>
 * Fields that may be null in JSON are exactly the payload slots, {@code responseToAttack},
 * {@code carried}, a target's {@code alias} and {@code bodyPart}, and {@code stepNumber}; every
 * other field is required and non-null. Structural rules that need several fields are checked in
 * the constructors; references are resolved later by {@link ActionDocumentMapper}.
 */
public record ActionDocument(
		int schemaVersion,
		boolean supported,
		String responseToAttack,
		InterpretationConfidence confidence,
		List<Step> steps,
		List<Unresolved> unresolved) {

	public static final int CURRENT_SCHEMA_VERSION = 1;

	public ActionDocument {
		if (schemaVersion != CURRENT_SCHEMA_VERSION) {
			throw new IllegalArgumentException("schemaVersion must be " + CURRENT_SCHEMA_VERSION);
		}
		Objects.requireNonNull(confidence, "confidence");
		steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
		unresolved = List.copyOf(Objects.requireNonNull(unresolved, "unresolved"));
		// The one way to say "I cannot tell what the player means" is no steps plus the unclear phrases.
		if (supported && steps.isEmpty() && unresolved.isEmpty()) {
			throw new IllegalArgumentException("a supported action needs at least one step, or the unclear phrases in unresolved");
		}
		if (!supported && !steps.isEmpty()) {
			throw new IllegalArgumentException("an unsupported action has no steps");
		}
	}

	public record Step(
			StepRelation relation,
			ActionType action,
			Attack attack,
			Defend defend,
			Move move,
			Interact interact,
			Observe observe,
			UseAbility useAbility,
			UseItem useItem,
			Communicate communicate) {

		public Step {
			Objects.requireNonNull(relation, "relation");
			Objects.requireNonNull(action, "action");
			long payloads = Stream.of(attack, defend, move, interact, observe, useAbility, useItem, communicate)
					.filter(Objects::nonNull).count();
			if (payloads != 1) {
				throw new IllegalArgumentException("a step has exactly one payload, but had " + payloads);
			}
			Object expected = switch (action) {
				case ATTACK -> attack;
				case DEFEND -> defend;
				case MOVE -> move;
				case INTERACT -> interact;
				case OBSERVE -> observe;
				case USE_ABILITY -> useAbility;
				case USE_ITEM -> useItem;
				case COMMUNICATE -> communicate;
			};
			if (expected == null) {
				throw new IllegalArgumentException("a " + action + " step needs its matching payload");
			}
		}
	}

	public enum TargetKind {
		ENTITY,
		OBJECT,
		HAZARD,
		ZONE,
		EXIT,
		SELF,
		NONE
	}

	/**
	 * A target. Scene kinds need an alias; SELF and NONE have none. Only ENTITY and SELF may name a
	 * body part. Every kind except NONE identifies something, so its specificity is EXPLICIT or
	 * INFERRED; NONE is always UNSPECIFIED (as in Stage 10, where only "no target" is UNSPECIFIED).
	 */
	public record Target(TargetKind kind, String alias, BodyPart bodyPart, TargetSpecificity specificity) {
		public Target {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(specificity, "specificity");
			if (kind != TargetKind.NONE && specificity == TargetSpecificity.UNSPECIFIED) {
				throw new IllegalArgumentException("a " + kind + " target identifies something, so specificity must be "
						+ "EXPLICIT (the player named it) or INFERRED (you identified it from context); "
						+ "UNSPECIFIED is only for kind NONE");
			}
			if (kind == TargetKind.NONE && specificity != TargetSpecificity.UNSPECIFIED) {
				throw new IllegalArgumentException("a NONE target identifies nothing, so specificity must be UNSPECIFIED");
			}
			boolean scene = kind != TargetKind.SELF && kind != TargetKind.NONE;
			if (scene != (alias != null)) {
				throw new IllegalArgumentException(scene ? "a " + kind + " target needs an alias" : "a " + kind + " target has no alias");
			}
			if (bodyPart != null && kind != TargetKind.ENTITY && kind != TargetKind.SELF) {
				throw new IllegalArgumentException("only an ENTITY or SELF target can name a body part");
			}
		}
	}

	public record Attack(String weapon, WeaponMethod method, AttackTemplate template, Target target,
			ActionApproach approach, AttackPurpose purpose) {
		public Attack {
			Objects.requireNonNull(weapon, "weapon");
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(template, "template");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(approach, "approach");
			Objects.requireNonNull(purpose, "purpose");
		}
	}

	public record Defend(DefenseMethod method, EvadeType evadeType, ParryContact parryContact, Target cover) {
		public Defend {
			Objects.requireNonNull(method, "method");
			Objects.requireNonNull(evadeType, "evadeType");
			Objects.requireNonNull(parryContact, "parryContact");
			Objects.requireNonNull(cover, "cover");
		}
	}

	public record Move(MovementType movementType, Target target, RelativeGoal goal, ActionApproach approach) {
		public Move {
			Objects.requireNonNull(movementType, "movementType");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(goal, "goal");
			Objects.requireNonNull(approach, "approach");
		}
	}

	public record Interact(InteractionKind kind, Target target, Carried carried, ActionApproach approach) {
		public Interact {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(approach, "approach");
		}
	}

	public record Carried(CarriedKind kind, String alias) {
		public Carried {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(alias, "alias");
		}
	}

	public record Observe(ObservationKind kind, Target target) {
		public Observe {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(target, "target");
		}
	}

	public record UseAbility(String ability, Target target) {
		public UseAbility {
			Objects.requireNonNull(ability, "ability");
			Objects.requireNonNull(target, "target");
		}
	}

	public record UseItem(String item, Target target) {
		public UseItem {
			Objects.requireNonNull(item, "item");
			Objects.requireNonNull(target, "target");
		}
	}

	public record Communicate(CommunicationKind kind, String content, Target target) {
		public Communicate {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(content, "content");
			Objects.requireNonNull(target, "target");
		}
	}

	/** @param stepNumber the 1-based step the phrase belongs to, or null for the whole input */
	public record Unresolved(Integer stepNumber, String phrase) {
		public Unresolved {
			Objects.requireNonNull(phrase, "phrase");
		}
	}
}
