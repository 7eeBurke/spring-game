package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.ActionPayload.DefendPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.mechanics.CheckRequest;
import com.leeburke.springgame.mechanics.CheckResolver;
import com.leeburke.springgame.mechanics.CheckResult;
import com.leeburke.springgame.mechanics.ContactQuality;
import com.leeburke.springgame.mechanics.ContactRules;
import com.leeburke.springgame.mechanics.DamageCalculator;
import com.leeburke.springgame.mechanics.DamageRequest;
import com.leeburke.springgame.mechanics.DamageResult;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.TraumaCalculator;
import com.leeburke.springgame.mechanics.TraumaRequest;
import com.leeburke.springgame.mechanics.TraumaResult;
import com.leeburke.springgame.mechanics.TraumaRules;

/**
 * Resolves the player's defense against the intent's incoming attack, using only the backend-supplied
 * {@link IncomingAttack}; no attack is chosen here. Stat from the defense method, DC from the attack,
 * baseline suitability. The defender's degree maps to incoming contact, and Stage 4 computes what
 * gets through. An attack counts as resolved only once a defense against it is RESOLVED.
 */
final class DefenseResolver {

	private final CheckResolver checks;
	private final DamageCalculator damage = new DamageCalculator();
	private final TraumaCalculator trauma = new TraumaCalculator();

	DefenseResolver(CheckResolver checks) {
		this.checks = checks;
	}

	StepOutcome resolve(ActionStep step, DefendPayload defense, Optional<String> responseToAttack,
			ActionResolutionContext context, Set<String> resolvedAttacks, RandomGenerator rng) {
		if (responseToAttack.isEmpty()) {
			return StepOutcome.unavailable(step.id(), ActionType.DEFEND, UnavailableReason.NO_INCOMING_ATTACK);
		}
		String attackRef = responseToAttack.get();
		if (resolvedAttacks.contains(attackRef)) {
			return StepOutcome.unavailable(step.id(), ActionType.DEFEND, UnavailableReason.INCOMING_ATTACK_ALREADY_RESOLVED);
		}
		IncomingAttack attack = Objects.requireNonNull(context.incomingAttacks().get(attackRef), "validated incoming attack");

		int existingInjury = 0;
		if (attack.targetBodyPart().isPresent()) {
			OptionalInt modifier = TraumaRules.existingInjuryModifier(context.player().body().severity(attack.targetBodyPart().get()));
			if (modifier.isEmpty()) {
				return StepOutcome.unavailable(step.id(), ActionType.DEFEND, UnavailableReason.UNDEFINED_INJURY_MODIFIER);
			}
			existingInjury = modifier.getAsInt();
		}

		StatType stat = ResolutionRules.defenseStat(defense.method());
		CheckResult check = checks.resolve(new CheckRequest(stat, context.player().stats().get(stat), attack.difficulty(),
				Optional.of(ResolutionRules.BASELINE_SUITABILITY), List.of()), rng);
		ContactQuality contact = ContactRules.incomingContact(check.degree());

		DamageResult damageResult = damage.calculate(
				new DamageRequest(attack.baseDamage(), contact, attack.effectiveness(), ResolutionRules.PLAYER_PROTECTION));
		TraumaResult traumaResult = trauma.calculate(new TraumaRequest(attack.weaponTrauma(), contact, existingInjury,
				attack.anatomyInteractionModifier(), attack.attackFormModifier(), ResolutionRules.PLAYER_TRAUMA_PROTECTION,
				ResolutionRules.DEFENSIVE_MITIGATION_BASELINE));

		List<OutcomeEffect> effects = contact == ContactQuality.NONE
				? List.of()
				: List.of(new OutcomeEffect.PlayerDamaged(damageResult.finalDamage(), attack.targetBodyPart(),
						traumaResult.impactSeverity()));
		resolvedAttacks.add(attackRef);
		return StepOutcome.resolved(step.id(), ActionType.DEFEND, ResolutionRules.successFor(check.degree()),
				Optional.of(check), new StepResult.DefenseResult(attackRef, defense.method(), contact, damageResult, traumaResult),
				effects);
	}
}
