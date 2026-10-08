import styles from './Composer.module.css';

/** A finished tale has no action composer: it is a completed chronicle to read. */
export function EndedFooter({ ending, onReturn }: { ending: 'DEAD' | 'VICTORIOUS'; onReturn?: () => void }) {
  return (
    <div className={styles.ended} role="status">
      <p className={styles.endedText}>{ending === 'DEAD' ? 'This tale has ended in death.' : 'This tale has ended in victory.'}</p>
      {onReturn && <button type="button" className={styles.endedButton} onClick={onReturn}>Return to your tales</button>}
    </div>
  );
}
