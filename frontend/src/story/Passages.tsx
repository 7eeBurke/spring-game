import type { NarrationView } from '../api/types';
import { toParagraphs } from './paragraphs';
import styles from './Passages.module.css';

// Every piece of game text is rendered as React text children: never as HTML.

export function NarrationPassage({ narration, intro = false }: { narration: NarrationView; intro?: boolean }) {
  return (
    <section className={`${styles.passage} ${styles.narration} ${intro ? styles.introduction : ''}`}
      aria-label={intro ? 'Introduction' : 'Narration'}>
      {toParagraphs(narration.text).map((paragraph, i) => <p key={i}>{paragraph}</p>)}
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

export function PendingNarration() {
  return <p className={styles.pending} role="status">The storyteller is still writing…</p>;
}
