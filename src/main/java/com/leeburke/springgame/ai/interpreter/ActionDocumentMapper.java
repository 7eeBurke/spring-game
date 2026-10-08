package com.leeburke.springgame.ai.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.ActionIntent;
import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
import com.leeburke.springgame.action.UnresolvedReference;
import com.leeburke.springgame.ai.interpreter.ActionDocument.Target;
import com.leeburke.springgame.ai.interpreter.AliasTable.AliasException;

/**
 * Turns a parsed {@link ActionDocument} into the Stage 10 {@link ActionIntent}: resolves every
 * alias through the request's {@link AliasTable}, numbers steps {@code s1..sn}, and lets the Stage 10
 * constructors check their own invariants. Problems are reported with the document's own aliases
 * and step numbers only, never backend references.
 */
public final class ActionDocumentMapper {

	private final AliasTable aliases;

	public ActionDocumentMapper(AliasTable aliases) {
		this.aliases = Objects.requireNonNull(aliases, "aliases");
	}

	/** @throws DocumentMappingException listing each problem found */
	public ActionIntent toIntent(ActionDocument document) {
		List<String> problems = new ArrayList<>();
		Optional<String> response = Optional.empty();
		if (document.responseToAttack() != null) {
			response = attempt(problems, "responseToAttack", () -> aliases.resolve(AliasKind.ATTACK, document.responseToAttack()));
		}

		List<ActionStep> steps = new ArrayList<>();
		for (int i = 0; i < document.steps().size(); i++) {
			int number = i + 1;
			ActionDocument.Step step = document.steps().get(i);
			Optional<ActionPayload> payload = attempt(problems, "step " + number + " " + payloadName(step.action()),
					() -> payload(step));
			payload.ifPresent(p -> {
				try {
					steps.add(new ActionStep("s" + number, number, step.relation(), p));
				} catch (IllegalArgumentException e) {
					problems.add("step " + number + ": " + e.getMessage());
				}
			});
		}

		List<UnresolvedReference> unresolved = new ArrayList<>();
		for (ActionDocument.Unresolved u : document.unresolved()) {
			Integer stepNumber = u.stepNumber();
			if (stepNumber != null && (stepNumber < 1 || stepNumber > document.steps().size())) {
				problems.add("unresolved phrase refers to step " + stepNumber + ", which does not exist");
				continue;
			}
			attempt(problems, "unresolved phrase", () -> new UnresolvedReference(
					Optional.ofNullable(stepNumber).map(n -> "s" + n), u.phrase())).ifPresent(unresolved::add);
		}

		if (problems.isEmpty()) {
			try {
				return new ActionIntent(ActionIntent.CURRENT_SCHEMA_VERSION, response, steps, document.confidence(), unresolved);
			} catch (IllegalArgumentException e) {
				problems.add(e.getMessage());
			}
		}
		throw new DocumentMappingException(problems);
	}

	private static String payloadName(com.leeburke.springgame.action.ActionType action) {
		return switch (action) {
			case ATTACK -> "attack";
			case DEFEND -> "defend";
			case MOVE -> "move";
			case INTERACT -> "interact";
			case OBSERVE -> "observe";
			case USE_ABILITY -> "useAbility";
			case USE_ITEM -> "useItem";
			case COMMUNICATE -> "communicate";
		};
	}

	private ActionPayload payload(ActionDocument.Step step) {
		return switch (step.action()) {
			case ATTACK -> new ActionPayload.AttackPayload(aliases.resolve(AliasKind.WEAPON, step.attack().weapon()),
					step.attack().method(), step.attack().template(), target(step.attack().target(), "target"), step.attack().approach(),
					step.attack().purpose());
			case DEFEND -> new ActionPayload.DefendPayload(step.defend().method(), step.defend().evadeType(),
					step.defend().parryContact(), target(step.defend().cover(), "cover"));
			case MOVE -> new ActionPayload.MovePayload(step.move().movementType(), target(step.move().target(), "target"),
					step.move().goal(), step.move().approach());
			case INTERACT -> new ActionPayload.InteractPayload(step.interact().kind(), target(step.interact().target(), "target"),
					Optional.ofNullable(step.interact().carried()).map(this::carried), step.interact().approach());
			case OBSERVE -> new ActionPayload.ObservePayload(step.observe().kind(), target(step.observe().target(), "target"));
			case USE_ABILITY -> new ActionPayload.UseAbilityPayload(aliases.resolve(AliasKind.ABILITY, step.useAbility().ability()),
					target(step.useAbility().target(), "target"));
			case USE_ITEM -> new ActionPayload.UseItemPayload(aliases.resolve(AliasKind.ITEM, step.useItem().item()),
					target(step.useItem().target(), "target"));
			case COMMUNICATE -> new ActionPayload.CommunicatePayload(step.communicate().kind(), step.communicate().content(),
					target(step.communicate().target(), "target"));
		};
	}

	private CarriedReference carried(ActionDocument.Carried carried) {
		AliasKind kind = carried.kind() == CarriedKind.WEAPON ? AliasKind.WEAPON : AliasKind.ITEM;
		return new CarriedReference(carried.kind(), aliases.resolve(kind, carried.alias()));
	}

	/** Builds the Stage 10 target; a rule it breaks is reported with the document field it came from. */
	private ActionTarget target(Target target, String field) {
		try {
			return target(target);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(field + ": " + e.getMessage(), e);
		}
	}

	ActionTarget target(Target target) {
		Optional<com.leeburke.springgame.mechanics.BodyPart> part = Optional.ofNullable(target.bodyPart());
		return switch (target.kind()) {
			case ENTITY -> new ActionTarget.EntityTarget(aliases.resolve(AliasKind.ENTITY, target.alias()), part, target.specificity());
			case OBJECT -> new ActionTarget.ObjectTarget(aliases.resolve(AliasKind.OBJECT, target.alias()), target.specificity());
			case HAZARD -> new ActionTarget.HazardTarget(aliases.resolve(AliasKind.HAZARD, target.alias()), target.specificity());
			case ZONE -> new ActionTarget.ZoneTarget(aliases.resolve(AliasKind.ZONE, target.alias()), target.specificity());
			case EXIT -> new ActionTarget.ExitTarget(aliases.resolve(AliasKind.EXIT, target.alias()), target.specificity());
			case SELF -> new ActionTarget.SelfTarget(part, target.specificity());
			case NONE -> new ActionTarget.Unspecified();
		};
	}

	private static <T> Optional<T> attempt(List<String> problems, String where, java.util.function.Supplier<T> action) {
		try {
			return Optional.of(action.get());
		} catch (AliasException | IllegalArgumentException | NullPointerException e) {
			problems.add(where + ": " + e.getMessage());
			return Optional.empty();
		}
	}

	/** The document could not be turned into an intent. Problems mention aliases and step numbers only. */
	public static final class DocumentMappingException extends RuntimeException {

		private final List<String> problems;

		DocumentMappingException(List<String> problems) {
			super(String.join("; ", problems));
			this.problems = List.copyOf(problems);
		}

		public List<String> problems() {
			return problems;
		}
	}
}
