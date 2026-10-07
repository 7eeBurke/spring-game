package com.leeburke.springgame.action.resolution;

import com.leeburke.springgame.action.DefenseMethod;
import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.mechanics.DegreeOfSuccess;
import com.leeburke.springgame.mechanics.StatType;
import com.leeburke.springgame.mechanics.Suitability;

/**
 * V1 action-resolution policy. See docs/GAME_RULES.md "Action Resolution".
 * <p>
 * Stat tables follow the documented stat domains: AGILITY covers finesse attacks, dodging and
 * parries; MIGHT covers heavy physical actions, blocks and bracing; ARCANA covers controlling
 * supernatural forces. A weapon's identity implies no stat. The baselines are explicit
 * placeholders until their rules are designed.
 */
public final class ResolutionRules {

	/** Baseline suitability for every rolled check until suitability rules exist. */
	public static final Suitability BASELINE_SUITABILITY = Suitability.FAIR;
	/** Baseline attack-form trauma modifier until attack forms are designed. */
	public static final int ATTACK_FORM_BASELINE = 0;
	/** Normal player HP protection in the MVP (no armour). */
	public static final int PLAYER_PROTECTION = 0;
	/** Normal player trauma protection in the MVP (no armour). */
	public static final int PLAYER_TRAUMA_PROTECTION = 0;
	/** Baseline defensive mitigation until defense-method differences are designed. */
	public static final int DEFENSIVE_MITIGATION_BASELINE = 0;

	private ResolutionRules() {
	}

	public static StatType attackStat(WeaponMethod method) {
		return switch (method) {
			case SLASH, THRUST, HOOK -> StatType.AGILITY;
			case SMASH, POMMEL_STRIKE -> StatType.MIGHT;
			case PROJECT -> StatType.ARCANA;
		};
	}

	/** A check's degree as a step success level: critical and success are SUCCESS. */
	public static StepSuccess successFor(DegreeOfSuccess degree) {
		return switch (degree) {
			case CRITICAL_SUCCESS, SUCCESS -> StepSuccess.SUCCESS;
			case PARTIAL_SUCCESS -> StepSuccess.PARTIAL;
			case FAILURE -> StepSuccess.FAILURE;
		};
	}

	public static StatType defenseStat(DefenseMethod method) {
		return switch (method) {
			case EVADE, PARRY, TAKE_COVER -> StatType.AGILITY;
			case BLOCK, BRACE -> StatType.MIGHT;
		};
	}
}
