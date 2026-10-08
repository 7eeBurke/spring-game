import styles from './StoryHeader.module.css';

export interface StoryHeaderProps {
  region: string | null;
  scene: string;
  name: string;
  hp: number;
  maxHp: number;
  onOpenScene?: () => void;
  onOpenCharacter?: () => void;
  /** Back to the shelf of saved tales. */
  onBack?: () => void;
}

/** Low HP is at or below 30% of maximum. Shown immediately: never delayed by narration. */
export function isLowHp(hp: number, maxHp: number): boolean {
  return hp * 10 <= maxHp * 3;
}

export function StoryHeader({ region, scene, name, hp, maxHp, onOpenScene, onOpenCharacter, onBack }: StoryHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        {onBack && <button type="button" className={styles.back} onClick={onBack} aria-label="Back to your tales">‹</button>}
        <button type="button" className={styles.place} onClick={onOpenScene} aria-label={`Scene details: ${scene}`}>
          <span className={styles.region}>{region ?? 'The road to the Chapel'}</span>
          <span className={styles.scene}>{scene}</span>
        </button>
        <button type="button" className={styles.who} onClick={onOpenCharacter}
          aria-label={`Character details: ${name}, ${hp} of ${maxHp} health`}>
          <span className={styles.name}>{name}</span>
          <span className={`${styles.hp} ${isLowHp(hp, maxHp) ? styles.hpLow : ''}`}>
            <span className={styles.heart} aria-hidden="true">♥</span>{hp} / {maxHp}
          </span>
        </button>
      </div>
    </header>
  );
}
