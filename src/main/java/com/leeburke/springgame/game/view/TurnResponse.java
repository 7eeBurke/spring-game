package com.leeburke.springgame.game.view;

import java.util.List;

/**
 * The result of one committed turn: Java's confirmed changes, the narration, the enemy's response
 * and the view after the turn. Rebuilt only from the turn's stored mechanics summary, never by
 * resolving anything again.
 *
 * @param enemyTurn null when no enemy acted
 */
public record TurnResponse(int turnNumber, String overall, GameView.NarrationView narration, Changes changes,
		EnemyTurn enemyTurn, GameView view) {

	/**
	 * @param movedTo      the zone moved to within the scene, or null
	 * @param enteredScene the scene entered through an exit, or null
	 */
	public record Changes(int playerHpLost, List<String> enemiesDefeated, String movedTo, String enteredScene) {
	}

	/** @param action ATTACK or HOLD */
	public record EnemyTurn(String attacker, String action) {
	}
}
