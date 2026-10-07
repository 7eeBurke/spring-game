package com.leeburke.springgame.enemy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.leeburke.springgame.action.resolution.TargetCombatProfile;
import com.leeburke.springgame.action.resolution.TargetProfileKey;
import com.leeburke.springgame.mechanics.BodyPart;
import com.leeburke.springgame.mechanics.BodySeverity;
import com.leeburke.springgame.mechanics.TraumaRules;

/**
 * Builds Stage 11's {@link TargetCombatProfile} for a player attacking an enemy. Stage 11 only
 * consumes the profile; how it was derived lives here.
 * <p>
 * The DC is the enemy's defense DC; the existing-injury modifier comes from the targeted part's
 * severity (0 when no part is named). Everything else uses the Stage 12 baselines. A part the
 * anatomy lacks, or a DESTROYED part (modifier undefined), has no profile, so Stage 11 reports the
 * attack unavailable without rolling; there is never a fallback to another part or the whole body.
 */
public final class EnemyTargetProfiles {

	private EnemyTargetProfiles() {
	}

	public static Optional<TargetCombatProfile> profile(EnemyCombatant enemy, Optional<BodyPart> bodyPart) {
		Objects.requireNonNull(enemy, "enemy");
		Objects.requireNonNull(bodyPart, "bodyPart");
		int existingInjury = 0;
		if (bodyPart.isPresent()) {
			Optional<BodySeverity> severity = enemy.instance().body().severity(bodyPart.get());
			if (severity.isEmpty()) {
				return Optional.empty();
			}
			OptionalInt modifier = TraumaRules.existingInjuryModifier(severity.get());
			if (modifier.isEmpty()) {
				return Optional.empty();
			}
			existingInjury = modifier.getAsInt();
		}
		return Optional.of(new TargetCombatProfile(
				EnemyRules.defenseDc(enemy.instance().stats(), enemy.definition().defenseStat()),
				EnemyRules.EFFECTIVENESS_BASELINE,
				EnemyRules.PROTECTION_BASELINE,
				EnemyRules.TRAUMA_PROTECTION_BASELINE,
				existingInjury,
				EnemyRules.ANATOMY_INTERACTION_BASELINE,
				EnemyRules.DEFENSIVE_MITIGATION_BASELINE));
	}

	/** The whole-target profile plus one per present, non-destroyed body part. */
	public static Map<TargetProfileKey, TargetCombatProfile> profiles(EnemyCombatant enemy) {
		Map<TargetProfileKey, TargetCombatProfile> profiles = new LinkedHashMap<>();
		profile(enemy, Optional.empty()).ifPresent(p -> profiles.put(TargetProfileKey.wholeTarget(enemy.entityId()), p));
		for (BodyPart part : enemy.anatomy().bodyParts()) {
			profile(enemy, Optional.of(part)).ifPresent(p -> profiles.put(TargetProfileKey.at(enemy.entityId(), part), p));
		}
		return Map.copyOf(profiles);
	}
}
