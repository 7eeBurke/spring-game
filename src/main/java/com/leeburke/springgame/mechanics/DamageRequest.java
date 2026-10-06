package com.leeburke.springgame.mechanics;

import java.util.Objects;

/**
 * Already-determined inputs for one HP-damage calculation. Protection is supplied by the caller;
 * the MVP has no armour system. No maximums are imposed.
 */
public record DamageRequest(int baseDamage, ContactQuality contactQuality, Effectiveness effectiveness, int protection) {

	public DamageRequest {
		Objects.requireNonNull(contactQuality, "contactQuality");
		Objects.requireNonNull(effectiveness, "effectiveness");
		if (baseDamage < 0) {
			throw new IllegalArgumentException("Base damage cannot be negative, but was " + baseDamage);
		}
		if (protection < 0) {
			throw new IllegalArgumentException("Protection cannot be negative, but was " + protection);
		}
	}
}
