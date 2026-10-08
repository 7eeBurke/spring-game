package com.leeburke.springgame.game;

import java.util.Collection;

import com.leeburke.springgame.enemy.EnemyInstance;

/**
 * When a run ends, decided by Java only. The player at 0 HP is DEAD; the Chapel Guardian brought to
 * 0 HP while the player lives is VICTORIOUS; death takes precedence when both happen in one turn. No
 * reward is granted: reward mechanics are deferred.
 */
public final class TerminalRules {

	public static final String GUARDIAN_CODE = "CHAPEL_GUARDIAN";

	private TerminalRules() {
	}

	/** @param defeatedThisTurn enemies brought to 0 HP by this turn */
	public static RunStatus after(boolean playerDown, Collection<EnemyInstance> defeatedThisTurn) {
		if (playerDown) {
			return RunStatus.DEAD;
		}
		boolean guardianFell = defeatedThisTurn.stream().anyMatch(e -> e.definitionCode().equals(GUARDIAN_CODE));
		return guardianFell ? RunStatus.VICTORIOUS : RunStatus.ACTIVE;
	}
}
