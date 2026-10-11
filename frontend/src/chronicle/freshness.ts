import type { ChronicleTurn, GameView } from '../api/types';

/**
 * Responses can arrive out of order (a slow GET after a newer POST, a refresh racing a turn).
 * These rules make sure an older answer never rolls back what the player has already seen.
 */

/**
 * The newer of two views of the same run. A higher stateVersion always wins. At the same
 * version only narration can still change, and only forwards: finalising never un-finalises.
 */
export function newerView(current: GameView, next: GameView): GameView {
  if (next.stateVersion < current.stateVersion) return current;
  if (next.stateVersion > current.stateVersion) return next;
  const regressesFinalizing = !current.finalizing && next.finalizing;
  const regressesNarration = current.lastTurn?.narration && !next.lastTurn?.narration;
  const regressesAttackNarration = current.pendingAttack?.narration && next.pendingAttack && !next.pendingAttack.narration;
  return regressesFinalizing || regressesNarration || regressesAttackNarration ? current : next;
}

/**
 * The better of two copies of the same turn: it never loses a narration it already has (a stale
 * page cannot turn a told turn back into a pending one). Otherwise the incoming copy wins, so the
 * authoritative chronicle replaces a provisional entry.
 */
export function betterTurn(existing: ChronicleTurn, incoming: ChronicleTurn): ChronicleTurn {
  const narration = incoming.narration ?? existing.narration;
  const enemy = incoming.enemy && existing.enemy && !incoming.enemy.narration && existing.enemy.narration
    ? { ...incoming.enemy, narration: existing.enemy.narration, cueText: incoming.enemy.cueText ?? existing.enemy.cueText }
    : incoming.enemy ?? existing.enemy;
  const enteredScene = incoming.enteredScene
    ? { scene: incoming.enteredScene.scene, zone: incoming.enteredScene.zone ?? existing.enteredScene?.zone ?? null }
    : existing.enteredScene;
  return {
    ...incoming,
    action: incoming.action ?? existing.action,
    narration,
    narrationPending: narration ? false : incoming.narrationPending,
    enemy,
    enteredScene,
  };
}
