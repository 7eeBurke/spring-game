package com.leeburke.springgame.action.resolution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import com.leeburke.springgame.action.ActionPayload.AttackPayload;
import com.leeburke.springgame.action.ActionStep;
import com.leeburke.springgame.action.ActionTarget;
import com.leeburke.springgame.action.ActionType;
import com.leeburke.springgame.action.AttackPurpose;
import com.leeburke.springgame.content.WeaponDefinition;
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

/**
 * Resolves a player attack: a DAMAGE attack on a visible entity with an exact backend profile.
 * Stat from the method, base DC from the profile, baseline suitability; Stage 3 resolves the check,
 * {@link ContactRules} maps the degree to contact, and Stage 4 computes damage and trauma from the
 * weapon definition and profile. Other attack kinds are reported as unavailable before any roll.
 */
final class AttackResolver {

	private final CheckResolver checks;
	private final DamageCalculator damage = new DamageCalculator();
	private final TraumaCalculator trauma = new TraumaCalculator();

	AttackResolver(CheckResolver checks) {
		this.checks = checks;
	}

	StepOutcome resolve(ActionStep step, AttackPayload attack, ActionResolutionContext context, RandomGenerator rng) {
		if (!(attack.target() instanceof ActionTarget.EntityTarget target) || attack.purpose() != AttackPurpose.DAMAGE) {
			return StepOutcome.unavailable(step.id(), ActionType.ATTACK, UnavailableReason.ACTION_NOT_IMPLEMENTED);
		}
		TargetCombatProfile profile = context.targetProfiles().get(new TargetProfileKey(target.entityId(), target.bodyPart()));
		if (profile == null) {
			return StepOutcome.unavailable(step.id(), ActionType.ATTACK, UnavailableReason.MISSING_TARGET_PROFILE);
		}
		WeaponDefinition weapon = Objects.requireNonNull(context.references().weapons().get(attack.weaponRef()),
				"validated weapon reference");

		StatType stat = ResolutionRules.attackStat(attack.method());
		CheckResult check = checks.resolve(new CheckRequest(stat, context.player().stats().get(stat), profile.defenseDc(),
				Optional.of(ResolutionRules.BASELINE_SUITABILITY), List.of()), rng);
		ContactQuality contact = ContactRules.attackContact(check.degree());

		DamageResult damageResult = damage.calculate(
				new DamageRequest(weapon.baseDamage(), contact, profile.effectiveness(), profile.protection()));
		TraumaResult traumaResult = trauma.calculate(new TraumaRequest(weapon.trauma(), contact,
				profile.existingInjuryModifier(), profile.anatomyInteractionModifier(), ResolutionRules.ATTACK_FORM_BASELINE,
				profile.traumaProtection(), profile.defensiveMitigation()));

		List<OutcomeEffect> effects = contact == ContactQuality.NONE
				? List.of()
				: List.of(new OutcomeEffect.TargetDamaged(target.entityId(), damageResult.finalDamage(), target.bodyPart(),
						traumaResult.impactSeverity()));
		StepResult.AttackResult result = new StepResult.AttackResult(attack.weaponRef(), weapon.code(), target.entityId(),
				target.bodyPart(), contact, damageResult, traumaResult);
		return StepOutcome.resolved(step.id(), ActionType.ATTACK, ResolutionRules.successFor(check.degree()),
				Optional.of(check), result, effects);
	}
}
