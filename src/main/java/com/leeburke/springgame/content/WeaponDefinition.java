package com.leeburke.springgame.content;

import java.util.Objects;

import com.leeburke.springgame.mechanics.DamageType;

/**
 * Static definition of a weapon: what can exist, not a weapon a character carries.
 * The primary damage type is content identity only; attack-method semantics are deferred.
 */
public record WeaponDefinition(String code, String displayName, int baseDamage, int trauma, DamageType primaryDamageType) {

	public WeaponDefinition {
		DefinitionFields.requireCode(code);
		DefinitionFields.requireDisplayName(displayName);
		if (baseDamage < 0) {
			throw new IllegalArgumentException("Weapon " + code + " base damage cannot be negative, but was " + baseDamage);
		}
		if (trauma < 0) {
			throw new IllegalArgumentException("Weapon " + code + " trauma cannot be negative, but was " + trauma);
		}
		Objects.requireNonNull(primaryDamageType, "primaryDamageType");
	}
}
