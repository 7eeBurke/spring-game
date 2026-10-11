import { useId, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import styles from './Composer.module.css';

/** The defense prompt shown above the composer while an attack is pending. Never delayed. */
export function DefenseBanner({ attacker, cueText, children }: { attacker: string; cueText: string; children?: ReactNode }) {
  return (
    <div className={styles.defense} role="alert">
      <span className={styles.defenseLabel}>Defend</span>
      {cueText.replace(/^Incoming:\s*/, '')}
      <span className={styles.defenseHint}>Tell how you meet the {attacker}'s blow: parry, dodge, block or brace.</span>
      {children}
    </div>
  );
}

export const MAX_ACTION_LENGTH = 500;
const COUNTER_FROM = 450;

export interface ComposerProps {
  placeholder?: string;
  disabled?: boolean;
  /** Shown under the field, for example why sending is unavailable. */
  note?: string;
  /** Controlled text; without it the composer keeps its own (design preview). */
  value?: string;
  onChange?: (text: string) => void;
  /** Sending: present only where play is live. */
  onSubmit?: () => void;
  /** An action is outstanding: sending is locked, typing is not. */
  busy?: boolean;
}

/** Touch devices put line breaks on Enter (the keyboard's return key) and send with the button. */
function prefersButtonSend(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(pointer: coarse)').matches;
}

/**
 * The action composer: free text is the primary way to play. On a desktop keyboard Enter sends and
 * Shift+Enter starts a new line; during IME composition Enter never sends. The field grows with
 * the text, up to the 500-character limit the server accepts.
 */
export function Composer({ placeholder = 'What do you do?', disabled = false, note, value, onChange, onSubmit, busy = false }: ComposerProps) {
  const [ownText, setOwnText] = useState('');
  const text = value ?? ownText;
  const setText = onChange ?? setOwnText;
  const id = useId();
  const field = useRef<HTMLTextAreaElement>(null);
  const canSend = !!onSubmit && !disabled && !busy && text.trim().length > 0 && text.length <= MAX_ACTION_LENGTH;

  useLayoutEffect(() => {
    const el = field.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [text]);

  function onKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== 'Enter' || e.shiftKey || e.nativeEvent.isComposing || e.keyCode === 229) return;
    if (prefersButtonSend()) return;
    e.preventDefault();
    if (canSend) onSubmit!();
  }

  return (
    <>
      <form className={styles.composer} aria-describedby={note ? `${id}-note` : undefined}
        onSubmit={(e) => { e.preventDefault(); if (canSend) onSubmit!(); }}>
        <label className="visually-hidden" htmlFor={`${id}-input`}>Your action</label>
        <textarea ref={field} id={`${id}-input`} className={styles.input} rows={1} value={text} placeholder={placeholder}
          maxLength={MAX_ACTION_LENGTH} enterKeyHint={prefersButtonSend() ? 'enter' : 'send'} autoComplete="off" spellCheck
          onChange={(e) => setText(e.target.value)} onKeyDown={onKeyDown} disabled={disabled} aria-busy={busy || undefined} />
        <button type="submit" className={styles.send} disabled={!canSend} aria-label={busy ? 'Sending action' : 'Send action'}>
          {busy ? <span className={styles.spinner} aria-hidden="true" /> : '➤'}
        </button>
      </form>
      {text.length >= COUNTER_FROM && <p className={styles.counter} aria-live="polite">{text.length} / {MAX_ACTION_LENGTH}</p>}
      {note && <p id={`${id}-note`} className={styles.note}>{note}</p>}
    </>
  );
}
