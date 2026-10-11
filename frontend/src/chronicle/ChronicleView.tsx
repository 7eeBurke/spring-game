import { useEffect, useRef } from 'react';
import { NarrationPassage, SceneHeading } from '../story/Passages';
import type { ChronicleState } from './chronicleState';
import styles from './Chronicle.module.css';
import { TurnEntry } from './TurnEntry';

export interface ChronicleViewProps {
  chronicle: ChronicleState;
  loadingOlder: boolean;
  onLoadOlder: () => void | Promise<void>;
  onRevealed: (turnNumber: number) => void;
}

/**
 * The loaded story, oldest first. Older pages load when the top comes into view, or with the
 * button (always present for keyboard and assistive technology). Not a live region: loaded
 * history is never announced.
 */
export function ChronicleView({ chronicle, loadingOlder, onLoadOlder, onRevealed }: ChronicleViewProps) {
  const sentinel = useRef<HTMLDivElement>(null);
  const canLoadOlder = !chronicle.reachedStart && chronicle.nextBefore !== null;

  useEffect(() => {
    const target = sentinel.current;
    if (!target || !canLoadOlder || typeof IntersectionObserver === 'undefined') return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) onLoadOlder();
    }, { rootMargin: '200px 0px 0px 0px' });
    observer.observe(target);
    return () => observer.disconnect();
  }, [canLoadOlder, onLoadOlder, chronicle.turns.length]);

  return (
    <>
      {canLoadOlder && (
        <div className={styles.older}>
          <div ref={sentinel} className={styles.sentinel} aria-hidden="true" />
          <button type="button" className={styles.olderButton} onClick={onLoadOlder} disabled={loadingOlder}>
            {loadingOlder ? 'Turning back the pages…' : 'Earlier in your tale'}
          </button>
        </div>
      )}
      {chronicle.opening && (
        <>
          {chronicle.opening.introduction && <NarrationPassage narration={chronicle.opening.introduction} intro />}
          {chronicle.opening.objective && <p className={styles.objective}>{chronicle.opening.objective}</p>}
          <SceneHeading scene={chronicle.opening.scene} zone={chronicle.opening.zone} />
        </>
      )}
      {chronicle.turns.map((turn) => (
        <TurnEntry key={turn.turnNumber} turn={turn} fresh={chronicle.fresh.has(turn.turnNumber)} onRevealed={onRevealed} />
      ))}
    </>
  );
}
