import type { ChronicleTurn, TurnResponse } from '../api/types';

/**
 * A provisional chronicle entry for a just-completed turn, built only from what the turn response
 * confirms, so the result can be shown at once:
 * - the player's words: the exact input the request carried;
 * - narration: `response.narration`;
 * - entered scene: `changes.enteredScene` (the response names the scene only, so the zone is left
 *   empty until the chronicle supplies it);
 * - the enemy: `enemyTurn`; for an ATTACK the cue and attack narration come from the pending
 *   attack in the response's view, which is exactly the attack this turn created;
 * - ending: the run status in the response's view.
 * The chronicle API stays authoritative and replaces this entry when it is next read.
 */
export function turnFromResponse(input: string, response: TurnResponse): ChronicleTurn {
  const view = response.view;
  const attack = response.enemyTurn?.action === 'ATTACK' ? view.pendingAttack : null;
  return {
    turnNumber: response.turnNumber,
    action: { text: input, kind: input.trim().startsWith('/') ? 'COMMAND' : 'FREE_TEXT' },
    enteredScene: response.changes.enteredScene ? { scene: response.changes.enteredScene, zone: null } : null,
    movedTo: response.changes.enteredScene ? null : response.changes.movedTo,
    narration: response.narration,
    narrationPending: false,
    enemy: response.enemyTurn
      ? { attacker: response.enemyTurn.attacker, action: response.enemyTurn.action, cueText: attack?.cueText ?? null,
        narration: attack?.narration ?? null }
      : null,
    ending: view.status === 'DEAD' || view.status === 'VICTORIOUS' ? view.status : null,
  };
}
