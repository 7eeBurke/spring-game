package com.leeburke.springgame.game.service;

import java.util.List;
import java.util.Objects;

import com.leeburke.springgame.action.resolution.OverallResult;
import com.leeburke.springgame.ai.narration.EnemyAttackNarrationContext;
import com.leeburke.springgame.ai.narration.OutcomeNarrationContext;
import com.leeburke.springgame.game.RunStatus;

/**
 * The confirmed result of a turn, written in the same transaction as its mechanics
 * ({@code run_turn.mechanics_summary}). It is everything finalisation needs to narrate and to build
 * the response after a crash, without resolving or applying anything again. Names are the player's
 * visible names; nothing hidden is stored here that the response would not show.
 *
 * @param movedTo      the zone moved to within the scene, or null
 * @param enteredScene the scene entered through an exit, or null
 * @param enemyTurn    the enemy's response, or null when none acted
 * @param attack       the new pending attack to narrate, or null
 */
record MechanicsSummary(int schemaVersion, int turnNumber, OverallResult overall, RunStatus runStatus, int playerHpLost,
		List<String> enemiesDefeated, String movedTo, String enteredScene, EnemyTurn enemyTurn,
		OutcomeNarrationContext narration, AttackNarration attack) {

	static final int CURRENT_SCHEMA_VERSION = 1;

	MechanicsSummary {
		Objects.requireNonNull(overall, "overall");
		Objects.requireNonNull(runStatus, "runStatus");
		enemiesDefeated = List.copyOf(enemiesDefeated);
		Objects.requireNonNull(narration, "narration");
	}

	/** @param action ATTACK or HOLD */
	record EnemyTurn(String attacker, String action) {
	}

	/** The pending attack this turn created, by its backend reference, and what its narrator may see. */
	record AttackNarration(String attackRef, EnemyAttackNarrationContext context) {
	}
}
