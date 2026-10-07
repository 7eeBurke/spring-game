package com.leeburke.springgame.content.enemy;

import java.util.Objects;

import com.leeburke.springgame.action.AttackTemplate;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.shared.DefinitionCodes;

/**
 * One authored attack an enemy can choose. Damage and trauma come from the enemy's weapon
 * definition and difficulty from the enemy's stats; the option only names how the weapon is used
 * and how much the enemy favours it.
 *
 * @param weight base behaviour weight, at least 1
 */
public record EnemyAttackOption(String code, WeaponMethod method, AttackTemplate template, int weight) {

	/** Reserved for the hold decision in behaviour history; never an attack code. */
	public static final String HOLD_CODE = "HOLD";

	public EnemyAttackOption {
		DefinitionCodes.requireCode(code, "Attack option code");
		if (code.equals(HOLD_CODE)) {
			throw new IllegalArgumentException("Attack option code " + HOLD_CODE + " is reserved");
		}
		Objects.requireNonNull(method, "method");
		Objects.requireNonNull(template, "template");
		if (weight < 1) {
			throw new IllegalArgumentException("Attack option " + code + " weight must be at least 1, but was " + weight);
		}
	}
}
