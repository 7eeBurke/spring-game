import { useState } from 'react';
import type { NarrationView } from '../api/types';
import { useParagraphReveal } from '../reveal/useParagraphReveal';
import { toParagraphs } from './paragraphs';
import styles from './Passages.module.css';

// Every piece of game text is rendered as React text children: never as HTML.

export interface NarrationPassageProps {
  narration: NarrationView;
  intro?: boolean;
  /** New narration from live play: reveal it paragraph by paragraph. Recovered narration never animates. */
  reveal?: boolean;
  /** Called once the passage is fully shown (or immediately when not revealing). */
  onRevealed?: () => void;
}

export function NarrationPassage({ narration, intro = false, reveal = false, onRevealed }: NarrationPassageProps) {
  const paragraphs = toParagraphs(narration.text);
  const { visible, revealing, revealAll } = useParagraphReveal(paragraphs.length, reveal, onRevealed);
  // Whether this passage arrived as new: keeps its fade even after the reveal settles.
  const [arrivedNew] = useState(reveal);
  return (
    <section className={`${styles.passage} ${styles.narration} ${intro ? styles.introduction : ''} ${arrivedNew ? styles.fresh : ''}`}
      aria-label={intro ? 'Introduction' : 'Narration'} data-revealing={revealing ? '' : undefined}
      onClick={revealing ? revealAll : undefined}>
      {revealing && <button type="button" className={styles.skip} onClick={revealAll}>Show the whole passage</button>}
      {/* Hidden paragraphs keep their space (no layout jump) and stay readable to screen readers. */}
      {paragraphs.map((paragraph, i) => <p key={i} data-hidden={i >= visible ? '' : undefined}>{paragraph}</p>)}
      {narration.source === 'FALLBACK' && <small className={styles.plain}>Told plainly — the storyteller was unavailable</small>}
    </section>
  );
}

export function PlayerPassage({ text, kind }: { text: string; kind: 'FREE_TEXT' | 'COMMAND' }) {
  return (
    <blockquote className={`${styles.player} ${kind === 'COMMAND' ? styles.command : ''}`}>
      <span className={styles.label}>{kind === 'COMMAND' ? 'Command' : 'You'}</span>
      {text}
    </blockquote>
  );
}

export function SceneHeading({ scene, zone, region }: { scene: string; zone: string; region?: string | null }) {
  return (
    <header className={styles.scene}>
      <div className={styles.sceneRule} aria-hidden="true">✦</div>
      <h2 className={styles.sceneTitle}>{scene}</h2>
      <p className={styles.sceneSub}>{region ? `${zone} · ${region}` : zone}</p>
    </header>
  );
}

/** A walk to another place in the same scene: a quiet map reference, not a new chapter. */
export function PlaceSubheading({ place }: { place: string }) {
  return <p className={styles.placeSub} aria-label={`Now at ${place}`}>{place}</p>;
}

export function IncomingAttack({ attacker, cueText, narration }:
  { attacker: string; cueText: string; narration: NarrationView | null }) {
  return (
    <section className={styles.attack} aria-label={`Incoming attack from the ${attacker}`}>
      <span className={styles.attackLabel}>Incoming — the {attacker}</span>
      <p className={styles.cue}>{cueText.replace(/^Incoming:\s*/, '')}</p>
      {narration && (
        <div className={styles.narration}>
          {toParagraphs(narration.text).map((p, i) => <p key={i}>{p}</p>)}
        </div>
      )}
    </section>
  );
}

export function EndingPassage({ ending }: { ending: 'DEAD' | 'VICTORIOUS' }) {
  const dead = ending === 'DEAD';
  return (
    <section className={styles.ending} aria-label="The end of the tale">
      <h2 className={`${styles.endingTitle} ${dead ? styles.endingDead : styles.endingVictory}`}>
        {dead ? 'Here your story ends' : 'The Chapel falls still'}
      </h2>
      <p className={styles.endingSub}>{dead ? 'The lantern gutters out.' : 'The Guardian lies fallen. The flame endures a while longer.'}</p>
    </section>
  );
}

/** A turn whose outcome is confirmed but whose telling has not been saved yet. Not a live region. */
export function PendingNarration() {
  return <p className={styles.pending}>The telling of this turn has not arrived yet.</p>;
}

/** A quiet factual line confirmed by the game (an enemy holding back), not narration. */
export function QuietNote({ children }: { children: string }) {
  return <p className={styles.quiet}>{children}</p>;
}
