package com.leeburke.springgame.game.view;

import java.util.List;
import java.util.UUID;

import com.leeburke.springgame.game.view.GameView.NarrationView;

/**
 * One page of a run's story, oldest turn first: what the player wrote, what Java confirmed, and how
 * it was told. Built only from stored, player-visible text and names; reading it never calls AI and
 * never writes. Nullable fields are absent information.
 *
 * @param latestTurnNumber the run's latest committed turn (0 before the first)
 * @param opening          the introduction and starting place; present only on the page that reaches
 *                         the start of the run (including a run with no turns yet)
 * @param nextBefore       the cursor for the next older page ({@code ?before=}), or null at the start
 */
public record ChronicleView(UUID runId, String status, int latestTurnNumber, Opening opening, List<Turn> turns,
		Integer nextBefore) {

	/** @param introduction null only if a run's introduction was never stored */
	public record Opening(NarrationView introduction, String scene, String zone) {
	}

	/**
	 * One committed turn.
	 *
	 * @param action           the player's exact accepted wording, or null for turns recorded before it was kept
	 * @param enteredScene     the scene and zone arrived in through an exit, or null
	 * @param narration        the outcome narration, or null while it is still being finalised
	 * @param narrationPending the mechanics are committed but the narration is not stored yet
	 * @param enemy            the enemy that responded, or null
	 * @param ending           DEAD or VICTORIOUS when this turn ended the run, else null
	 */
	public record Turn(int turnNumber, Action action, Place enteredScene, NarrationView narration, boolean narrationPending,
			Enemy enemy, String ending) {
	}

	/** @param kind FREE_TEXT or COMMAND (a slash command) */
	public record Action(String text, String kind) {
	}

	public record Place(String scene, String zone) {
	}

	/**
	 * @param action    ATTACK or HOLD
	 * @param cueText   Java's telegraph of an attack; kept after the attack is resolved
	 * @param narration the attack's narration; null for HOLD or until finalised
	 */
	public record Enemy(String attacker, String action, String cueText, NarrationView narration) {
	}
}
