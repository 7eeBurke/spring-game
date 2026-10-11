import { memo } from 'react';
import type { ChronicleTurn } from '../api/types';
import { EndingPassage, IncomingAttack, NarrationPassage, PendingNarration, PlaceSubheading, PlayerPassage, QuietNote, SceneHeading } from '../story/Passages';

export interface TurnEntryProps {
  turn: ChronicleTurn;
  /** Reveal this turn's narration as new (live play only). */
  fresh: boolean;
  onRevealed: (turnNumber: number) => void;
}

/**
 * One committed turn of the chronicle, in reading order: what the player wrote, the scene they
 * entered, how it was told, how the enemy answered, and the end of the tale if it ended here.
 * Only player-safe fields from the chronicle API are shown, always as text.
 */
export const TurnEntry = memo(function TurnEntry({ turn, fresh, onRevealed }: TurnEntryProps) {
  return (
    <article aria-label={`Turn ${turn.turnNumber}`} data-turn={turn.turnNumber}>
      {turn.action
        ? <PlayerPassage text={turn.action.text} kind={turn.action.kind} />
        : <QuietNote>Your words for this turn were not recorded.</QuietNote>}
      {turn.enteredScene && <SceneHeading scene={turn.enteredScene.scene} zone={turn.enteredScene.zone ?? ''} />}
      {!turn.enteredScene && turn.movedTo && <PlaceSubheading place={turn.movedTo} />}
      {turn.narration
        ? <NarrationPassage narration={turn.narration} reveal={fresh} onRevealed={() => onRevealed(turn.turnNumber)} />
        : turn.narrationPending && <PendingNarration />}
      {turn.enemy?.action === 'ATTACK' && turn.enemy.cueText && (
        <IncomingAttack attacker={turn.enemy.attacker} cueText={turn.enemy.cueText} narration={turn.enemy.narration} />
      )}
      {turn.enemy?.action === 'HOLD' && <QuietNote>{`The ${turn.enemy.attacker} holds back.`}</QuietNote>}
      {turn.ending && <EndingPassage ending={turn.ending} />}
    </article>
  );
});
