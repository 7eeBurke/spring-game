package com.leeburke.springgame.enemy;

import java.util.Objects;

import com.leeburke.springgame.content.GameContentCatalog;
import com.leeburke.springgame.content.WeaponDefinition;
import com.leeburke.springgame.content.enemy.AnatomyDefinition;
import com.leeburke.springgame.content.enemy.EnemyCatalog;
import com.leeburke.springgame.content.enemy.EnemyDefinition;

/**
 * An enemy instance together with the static definitions it is combined with, checked to belong
 * together: the definition is the instance's, the anatomy is the definition's, the body has exactly
 * the anatomy's parts, and the weapon is the instance's weapon. Every builder and the behaviour
 * engine take this, so a mismatch is a programming error caught before any random draw or Stage 11
 * resolution.
 */
public record EnemyCombatant(EnemyInstance instance, EnemyDefinition definition, AnatomyDefinition anatomy,
		WeaponDefinition weapon) {

	public EnemyCombatant {
		Objects.requireNonNull(instance, "instance");
		Objects.requireNonNull(definition, "definition");
		Objects.requireNonNull(anatomy, "anatomy");
		Objects.requireNonNull(weapon, "weapon");
		if (!instance.definitionCode().equals(definition.code())) {
			throw new IllegalArgumentException("Enemy " + instance.entityId() + " is a " + instance.definitionCode()
					+ ", not a " + definition.code());
		}
		if (!anatomy.code().equals(definition.anatomy())) {
			throw new IllegalArgumentException("Enemy " + definition.code() + " uses anatomy " + definition.anatomy()
					+ ", not " + anatomy.code());
		}
		if (!instance.body().matches(anatomy)) {
			throw new IllegalArgumentException("Enemy " + instance.entityId() + " body does not match anatomy " + anatomy.code());
		}
		if (!weapon.code().equals(instance.weaponCode())) {
			throw new IllegalArgumentException("Enemy " + instance.entityId() + " carries " + instance.weaponCode()
					+ ", not " + weapon.code());
		}
	}

	/** Looks up the definitions for an instance. */
	public static EnemyCombatant of(EnemyInstance instance, EnemyCatalog enemies, GameContentCatalog content) {
		EnemyDefinition definition = enemies.findEnemy(instance.definitionCode()).orElseThrow(
				() -> new IllegalArgumentException("Unknown enemy definition " + instance.definitionCode()));
		WeaponDefinition weapon = content.findWeapon(instance.weaponCode()).orElseThrow(
				() -> new IllegalArgumentException("Unknown weapon " + instance.weaponCode()));
		return new EnemyCombatant(instance, definition, enemies.anatomyOf(definition), weapon);
	}

	public String entityId() {
		return instance.entityId();
	}
}
