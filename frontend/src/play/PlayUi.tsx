import { useEffect, useState } from 'react';
import { PlayerPassage } from '../story/Passages';
import styles from './Play.module.css';
import type { Play, PlayPhase } from './usePlay';

/** Supported one-step defenses: exact slash commands (`take_cover` needs an object, so it is not offered). */
export const DEFENSE_SHORTCUTS = [
  { label: 'Parry', command: '/defend parry' },
  { label: 'Dodge', command: '/defend evade' },
  { label: 'Block', command: '/defend block' },
  { label: 'Brace', command: '/defend brace' },
] as const;

/**
 * Discreet helpers under the defense cue. A tap writes the command into an empty field for the
 * player to send (or edit); it never sends, so free text stays the way to play.
 */
export function DefenseShortcuts({ onPick }: { onPick: (command: string) => void }) {
  return (
    <span className={styles.shortcuts} role="group" aria-label="Quick defenses">
      <span>Or quickly:</span>
      {DEFENSE_SHORTCUTS.map((s) => (
        <button key={s.command} type="button" className={styles.shortcut} onClick={() => onPick(s.command)}
          aria-label={`${s.label}: write ${s.command} in the action field`}>{s.label}</button>
      ))}
    </span>
  );
}

function useNow(active: boolean): number {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    if (!active) return;
    setNow(Date.now()); // count from now, not from when the status first appeared
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [active]);
  return now;
}

/** One quiet line about the outstanding action, with the actions that are safe right now. */
export function PlayStatus({ play, onReloadStory, reloading }: { play: Play; onReloadStory: () => void; reloading: boolean }) {
  const phase = play.phase;
  const now = useNow(phase.kind === 'cooling');

  const line = (message: string, actions?: React.ReactNode, problem = false) => (
    <div className={`${styles.status} ${problem ? styles.problem : ''}`} role="status">
      <span>{message}</span>
      {actions && <span className={styles.actions}>{actions}</span>}
    </div>
  );
  const reload = (
    <button type="button" className={styles.button} onClick={onReloadStory} disabled={reloading}>
      {reloading ? 'Reading…' : 'Reload story'}
    </button>
  );

  switch (phase.kind) {
    case 'sending':
      return line(phase.recovering ? 'Reconnecting to your last action…' : 'The storyteller is weighing your action…');
    case 'waiting':
      return line('Your action is still being resolved. Checking again shortly…');
    case 'cooling': {
      const seconds = Math.max(0, Math.ceil((phase.until - now) / 1000));
      return line(seconds > 0 ? `The game needs a moment (${seconds}s). Your action is saved.` : 'Your action is saved and can be sent again.',
        <button type="button" className={styles.button} onClick={play.retryNow} disabled={seconds > 0}>Try again</button>);
    }
    case 'uncertain':
      return line(phase.autoRetryAt !== null
        ? 'The connection faltered. Trying your action again…'
        : 'We could not confirm what happened to your action. Checking is safe: it can never be applied twice.',
      <>
        <button type="button" className={styles.button} onClick={play.retryNow}>Check again</button>
        {reload}
      </>, true);
    case 'elsewhere':
      return line('An action for this tale is being sent from another tab or window.',
        <button type="button" className={styles.button} onClick={play.retryNow}>Check again</button>);
    case 'damaged':
      return line('This device\'s record of your last action is damaged. Reload the story to see what happened, then clear it.',
        <>
          {reload}
          <button type="button" className={styles.button} onClick={() => {
            if (window.confirm('Clear the damaged record? Read the story first: if your last action was applied, it is already there.')) {
              play.discardDamaged();
            }
          }}>Clear it</button>
        </>, true);
    case 'idle':
      return null;
  }
}

/** A notice after a refused action: plain words, the server's hint, and a way to dismiss it. */
export function PlayNoticeLine({ play }: { play: Play }) {
  if (!play.notice) return null;
  return (
    <div className={styles.notice} role="alert">
      <span>
        {play.notice.message}
        {play.notice.hint && <span className={styles.noticeHint}>{play.notice.hint}</span>}
        {play.notice.noTurnSpent && <span className={styles.noTurn}>No turn spent</span>}
      </span>
      <button type="button" className={styles.dismiss} onClick={play.dismissNotice} aria-label="Dismiss">×</button>
    </div>
  );
}

/** The player's words while their outcome is unknown: shown in their place, never as a confirmed turn. */
export function OutboxPassage({ phase }: { phase: PlayPhase }) {
  const entry = 'entry' in phase ? phase.entry : null;
  if (!entry) return null;
  const caption = phase.kind === 'sending' || phase.kind === 'waiting' ? 'awaiting the storyteller…' : 'not yet confirmed';
  return (
    <div data-outbox="" aria-label="Your action, not yet confirmed">
      <PlayerPassage text={entry.body.input} kind={entry.body.input.trim().startsWith('/') ? 'COMMAND' : 'FREE_TEXT'} />
      <p className={styles.awaiting}>{caption}</p>
    </div>
  );
}

/** A single polite live region: each new turn's narration is announced once, history never. */
export function LiveAnnouncer({ message }: { message: string }) {
  return <div className="visually-hidden" aria-live="polite" aria-atomic="true">{message}</div>;
}
