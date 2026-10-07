package com.leeburke.springgame.enemy;

import java.util.ArrayList;
import java.util.List;

import com.leeburke.springgame.action.WeaponMethod;
import com.leeburke.springgame.action.resolution.ResolutionRules;
import com.leeburke.springgame.character.StatProfile;
import com.leeburke.springgame.character.StatShape;
import com.leeburke.springgame.character.StatShapeCatalog;
import com.leeburke.springgame.mechanics.CheckRules;
import com.leeburke.springgame.mechanics.Effectiveness;
import com.leeburke.springgame.mechanics.StatBlock;
import com.leeburke.springgame.mechanics.StatType;

/**
 * Stage 12 enemy rules (see docs/GAME_RULES.md "Enemies"). The baselines are explicit, replaceable
 * placeholders, like Stage 11's FAIR suitability: damage-type effectiveness, protection, anatomy
 * interaction, attack form and hit location are not designed yet.
 */
public final class EnemyRules {

	/** DC base for a player attacking an enemy, before the enemy's defensive stat modifier. */
	public static final int DEFENSE_DC_BASE = 10;
	/** Difficulty base of an enemy attack, before the enemy's attack stat modifier. */
	public static final int ATTACK_DIFFICULTY_BASE = 10;

	// Stage 12 baselines.
	public static final Effectiveness EFFECTIVENESS_BASELINE = Effectiveness.NORMAL;
	public static final int PROTECTION_BASELINE = 0;
	public static final int TRAUMA_PROTECTION_BASELINE = 0;
	public static final int DEFENSIVE_MITIGATION_BASELINE = 0;
	public static final int ANATOMY_INTERACTION_BASELINE = 0;
	public static final int ATTACK_FORM_BASELINE = 0;

	/** Normal-enemy stat shapes: SPECIALIZED then EXTREME, each in catalogue order. */
	private static final List<StatShape> ENEMY_SHAPES;

	static {
		List<StatShape> shapes = new ArrayList<>(StatShapeCatalog.shapesFor(StatProfile.SPECIALIZED));
		shapes.addAll(StatShapeCatalog.shapesFor(StatProfile.EXTREME));
		ENEMY_SHAPES = List.copyOf(shapes);
	}

	private EnemyRules() {
	}

	/** The shapes a normal enemy's stats are drawn from, in a stable order. */
	public static List<StatShape> enemyShapes() {
		return ENEMY_SHAPES;
	}

	/** {@code 10 + modifier(defensive stat)}. */
	public static int defenseDc(StatBlock stats, StatType defenseStat) {
		return DEFENSE_DC_BASE + CheckRules.statModifier(stats.get(defenseStat));
	}

	/** {@code 10 + modifier(stat for the method)}, using the same method-to-stat table as player attacks. */
	public static int attackDifficulty(StatBlock stats, WeaponMethod method) {
		return ATTACK_DIFFICULTY_BASE + CheckRules.statModifier(stats.get(ResolutionRules.attackStat(method)));
	}
}
