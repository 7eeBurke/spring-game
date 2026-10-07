package com.leeburke.springgame.action.resolution;

import java.util.Objects;

import com.leeburke.springgame.mechanics.Effectiveness;

/**
 * Backend-supplied mechanical facts about attacking one target (optionally at one body part).
 * Resolution consumes these values; it does not decide why the target has them. The enemy model
 * will produce profiles later.
 */
public record TargetCombatProfile(
		int defenseDc,
		Effectiveness effectiveness,
		int protection,
		int traumaProtection,
		int existingInjuryModifier,
		int anatomyInteractionModifier,
		int defensiveMitigation) {

	public TargetCombatProfile {
		Objects.requireNonNull(effectiveness, "effectiveness");
		if (protection < 0 || traumaProtection < 0 || defensiveMitigation < 0) {
			throw new IllegalArgumentException("Protection, trauma protection and defensive mitigation cannot be negative");
		}
	}
}
