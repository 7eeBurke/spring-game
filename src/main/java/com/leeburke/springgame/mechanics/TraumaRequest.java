package com.leeburke.springgame.mechanics;

import java.util.Objects;

/**
 * Already-determined inputs for one trauma calculation.
 * <p>
 * The existing-injury, anatomy-interaction and attack-form modifiers are signed because the rules
 * that produce them are not owned by this stage. {@code NONE} contact is accepted and yields no impact.
 */
public record TraumaRequest(
		int weaponTrauma,
		ContactQuality contactQuality,
		int existingInjuryModifier,
		int anatomyInteractionModifier,
		int attackFormModifier,
		int traumaProtection,
		int defensiveMitigation) {

	public TraumaRequest {
		Objects.requireNonNull(contactQuality, "contactQuality");
		requireNonNegative(weaponTrauma, "weaponTrauma");
		requireNonNegative(traumaProtection, "traumaProtection");
		requireNonNegative(defensiveMitigation, "defensiveMitigation");
	}

	private static void requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " cannot be negative, but was " + value);
		}
	}
}
