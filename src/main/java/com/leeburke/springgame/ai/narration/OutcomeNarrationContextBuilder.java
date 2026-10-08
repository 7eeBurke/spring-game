package com.leeburke.springgame.ai.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.leeburke.springgame.action.ActionPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.CarriedKind;
import com.leeburke.springgame.action.CarriedReference;
import com.leeburke.springgame.action.resolution.IncomingAttack;
import com.leeburke.springgame.action.resolution.ResolvedOutcome;
import com.leeburke.springgame.action.resolution.StepOutcome;
import com.leeburke.springgame.action.resolution.StepResult;
import com.leeburke.springgame.action.resolution.StepStatus;
import com.leeburke.springgame.action.validation.PlayerActionReferences;
import com.leeburke.springgame.action.validation.ValidatedActionIntent;
import com.leeburke.springgame.ai.narration.AttemptedAction.TargetKind;
import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.content.world.WorldContentCatalog;

/**
 * Derives the Outcome Narrator's facts from a {@link ResolvedOutcome} (confirmed backend truth, after
 * Stage 11) and, for each step, a Java summary of what was attempted from the validated intent. The
 * narrator never receives the intent itself. Names come from the player-visible scene and the
 * player's references; the attacker of a defended attack from the backend incoming attack, named
 * only if visible. Spoken words are passed on only for a RESOLVED communication step.
 */
public final class OutcomeNarrationContextBuilder {

	static final String UNSEEN = "something unseen";

	private final GameContentCatalog content;
	private final WorldContentCatalog world;

	public OutcomeNarrationContextBuilder(GameContentCatalog content, WorldContentCatalog world) {
		this.content = Objects.requireNonNull(content, "content");
		this.world = Objects.requireNonNull(world, "world");
	}

	/**
	 * @param validated     the intent that produced the outcome; its view is the scene as the player saw it
	 * @param incoming      the incoming attacks the outcome may have responded to, by reference
	 * @param playerWording what the player typed, if any; passed on only as a bounded, untrusted excerpt
	 * @throws IllegalArgumentException if the outcome's steps are not the intent's steps
	 */
	public OutcomeNarrationContext build(ResolvedOutcome outcome, ValidatedActionIntent validated,
			Map<String, IncomingAttack> incoming, NarrationMode mode, Optional<TerminalFact> terminal,
			Optional<String> playerWording) {
		List<ActionStep> steps = validated.intent().steps();
		if (steps.size() != outcome.steps().size()) {
			throw new IllegalArgumentException("The outcome has " + outcome.steps().size() + " steps, the intent " + steps.size());
		}
		NarrationNames names = NarrationNames.of(validated.context().view(), world);
		PlayerActionReferences references = validated.context().references();
		List<NarrationFact> facts = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			StepOutcome step = outcome.steps().get(i);
			if (!step.stepId().equals(steps.get(i).id())) {
				throw new IllegalArgumentException("Outcome step " + step.stepId() + " is not intent step " + steps.get(i).id());
			}
			AttemptedAction attempt = attempt(steps.get(i).payload(), names, references);
			if (step.status() != StepStatus.RESOLVED) {
				attempt = attempt.withoutWords();
			}
			facts.add(fact(i + 1, step, attempt, names, incoming));
		}
		String zone = names.zone(validated.context().view().currentZoneId()).orElseThrow();
		return new OutcomeNarrationContext(mode, zone, outcome.overall(), facts, terminal,
				playerWording.flatMap(OutcomeNarrationContext::excerpt));
	}

	private NarrationFact fact(int step, StepOutcome outcome, AttemptedAction attempt, NarrationNames names,
			Map<String, IncomingAttack> incoming) {
		return switch (outcome.status()) {
			case CANCELLED -> new NarrationFact.StepCancelled(step, attempt, outcome.cancellation().orElseThrow());
			case MECHANICS_UNAVAILABLE -> new NarrationFact.StepHadNoEffect(step, attempt, outcome.unavailable().orElseThrow());
			case RESOLVED -> switch (outcome.result().orElseThrow()) {
				case StepResult.AttackResult a -> NarrationFact.PlayerAttacked.of(step, attempt, weaponName(a.weaponCode()),
						names.entity(a.targetEntityId()).orElse(UNSEEN), a.targetedBodyPart(), a.contact(),
						a.damage().finalDamage(), a.trauma().impactSeverity());
				case StepResult.DefenseResult d -> NarrationFact.PlayerDefended.of(step, attempt,
						Optional.ofNullable(incoming.get(d.attackRef()))
								.flatMap(attack -> names.entity(attack.attackerEntityId())).orElse(UNSEEN),
						d.method(), d.incomingContact(), d.damage().finalDamage(),
						Optional.ofNullable(incoming.get(d.attackRef())).flatMap(IncomingAttack::targetBodyPart),
						d.trauma().impactSeverity());
				case StepResult.MovementResult m -> m.moved()
						? new NarrationFact.PlayerMoved(step, attempt, zoneName(names, m.fromZone()), zoneName(names, m.toZone()))
						: new NarrationFact.PlayerStayed(step, attempt, zoneName(names, m.fromZone()),
								m.toZone().equals(m.fromZone()) ? Optional.empty() : Optional.of(zoneName(names, m.toZone())));
				case StepResult.CommunicationResult c -> new NarrationFact.PlayerSpoke(step, attempt, c.kind(),
						c.addresseeEntityId().map(id -> names.entity(id).orElse(UNSEEN)));
			};
		};
	}

	/** What the payload tried to do, in visible names. Spoken words are included here and removed for unresolved steps. */
	static AttemptedAction attempt(ActionPayload payload, NarrationNames names, PlayerActionReferences references) {
		Builder a = new Builder(payload.type());
		switch (payload) {
			case ActionPayload.AttackPayload p -> {
				a.manner = Optional.of(p.method().name());
				a.template = Optional.of(p.template());
				a.approach = Optional.of(p.approach());
				a.purpose = Optional.of(p.purpose());
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.weapons().get(p.weaponRef())).map(WeaponDefinition::displayName);
			}
			case ActionPayload.DefendPayload p -> {
				a.manner = Optional.of(p.method().name());
				a.target(p.cover(), names);
			}
			case ActionPayload.MovePayload p -> {
				a.manner = Optional.of(p.movementType().name());
				a.approach = Optional.of(p.approach());
				a.target(p.target(), names);
			}
			case ActionPayload.InteractPayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.approach = Optional.of(p.approach());
				a.target(p.target(), names);
				a.using = p.carried().flatMap(c -> carriedName(c, references));
			}
			case ActionPayload.ObservePayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.target(p.target(), names);
			}
			case ActionPayload.UseAbilityPayload p -> {
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.abilities().get(p.abilityRef())).map(d -> d.displayName());
			}
			case ActionPayload.UseItemPayload p -> {
				a.target(p.target(), names);
				a.using = Optional.ofNullable(references.items().get(p.itemRef())).map(d -> d.displayName());
			}
			case ActionPayload.CommunicatePayload p -> {
				a.manner = Optional.of(p.kind().name());
				a.target(p.target(), names);
				a.spokenWords = Optional.of(p.content());
			}
		}
		return a.build();
	}

	private static Optional<String> carriedName(CarriedReference carried, PlayerActionReferences references) {
		return carried.kind() == CarriedKind.WEAPON
				? Optional.ofNullable(references.weapons().get(carried.ref())).map(WeaponDefinition::displayName)
				: Optional.ofNullable(references.items().get(carried.ref())).map(d -> d.displayName());
	}

	private String weaponName(String code) {
		return content.findWeapon(code).map(WeaponDefinition::displayName).orElse("weapon");
	}

	private static String zoneName(NarrationNames names, String zoneId) {
		return names.zone(zoneId).orElse("somewhere unseen");
	}

	private static final class Builder {
		private final ActionType action;
		private Optional<String> manner = Optional.empty();
		private Optional<com.leeburke.springgame.action.AttackTemplate> template = Optional.empty();
		private Optional<com.leeburke.springgame.action.ActionApproach> approach = Optional.empty();
		private Optional<com.leeburke.springgame.action.AttackPurpose> purpose = Optional.empty();
		private Optional<TargetKind> targetKind = Optional.empty();
		private Optional<String> target = Optional.empty();
		private Optional<com.leeburke.springgame.mechanics.BodyPart> bodyPart = Optional.empty();
		private Optional<String> using = Optional.empty();
		private Optional<String> spokenWords = Optional.empty();

		Builder(ActionType action) {
			this.action = action;
		}

		void target(ActionTarget value, NarrationNames names) {
			switch (value) {
				case ActionTarget.EntityTarget t -> {
					set(TargetKind.CREATURE, names.entity(t.entityId()).orElse(UNSEEN));
					bodyPart = t.bodyPart();
				}
				case ActionTarget.ObjectTarget t -> set(TargetKind.OBJECT, names.object(t.objectId()).orElse(UNSEEN));
				case ActionTarget.HazardTarget t -> set(TargetKind.HAZARD, names.hazard(t.hazardId()).orElse(UNSEEN));
				case ActionTarget.ZoneTarget t -> set(TargetKind.ZONE, names.zone(t.zoneId()).orElse("somewhere unseen"));
				case ActionTarget.ExitTarget t -> set(TargetKind.EXIT, "an exit");
				case ActionTarget.SelfTarget t -> {
					set(TargetKind.SELF, "yourself");
					bodyPart = t.bodyPart();
				}
				case ActionTarget.Unspecified t -> {
					// no target
				}
			}
		}

		private void set(TargetKind kind, String name) {
			targetKind = Optional.of(kind);
			target = Optional.of(name);
		}

		AttemptedAction build() {
			return new AttemptedAction(action, manner, template, approach, purpose, targetKind, target, bodyPart, using,
					spokenWords);
		}
	}
}
