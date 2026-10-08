import { useId, useState } from 'react';
import styles from './Composer.module.css';

/** The defense prompt shown above the composer while an attack is pending. Never delayed. */
export function DefenseBanner({ attacker, cueText }: { attacker: string; cueText: string }) {
  return (
    <div className={styles.defense} role="alert">
      <span className={styles.defenseLabel}>Defend</span>
      {cueText.replace(/^Incoming:\s*/, '')}
      <span className={styles.defenseHint}>Tell how you meet the {attacker}'s blow: parry, dodge, block or brace.</span>
    </div>
  );
}

export interface ComposerProps {
  placeholder?: string;
  disabled?: boolean;
  /** Shown under the field, for example why sending is unavailable. */
  note?: string;
  initialText?: string;
}

/**
 * The action composer. In 15B it is presentation only: submission and the turn outbox arrive in
 * 15D, so the send button stays disabled with an explanatory note.
 */
export function Composer({ placeholder = 'What do you do?', disabled = false, note, initialText = '' }: ComposerProps) {
  const [text, setText] = useState(initialText);
  const noteId = useId();
  return (
    <>
      <form className={styles.composer} onSubmit={(e) => e.preventDefault()} aria-describedby={note ? noteId : undefined}>
        <label className="visually-hidden" htmlFor={`${noteId}-input`}>Your action</label>
        <textarea id={`${noteId}-input`} className={styles.input} rows={1} value={text} placeholder={placeholder}
          maxLength={500} enterKeyHint="send" autoComplete="off" spellCheck
          onChange={(e) => setText(e.target.value)} disabled={disabled} />
        <button type="submit" className={styles.send} disabled aria-label="Send action">➤</button>
      </form>
      {note && <p id={noteId} className={styles.note}>{note}</p>}
    </>
  );
}
