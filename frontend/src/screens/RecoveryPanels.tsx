import { useState, type FormEvent } from 'react';
import type { GameView } from '../api/types';
import { describeError } from '../domain/errors';
import { importRun } from '../runs/runService';
import { decodeRecoveryCode, encodeRecoveryCode } from '../security/recovery';
import type { VaultEntry } from '../storage/vault';
import styles from './Shelf.module.css';

/**
 * Shows a run's recovery code only after an explicit reveal. The code is the run's secret key: it is
 * never put in a URL, logged or sent anywhere; it is copied only when the player asks.
 */
export function RecoveryExport({ entry }: { entry: VaultEntry }) {
  const [code, setCode] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function reveal() {
    if (!entry.runId) return;
    setCode(await encodeRecoveryCode(entry.runId, entry.token));
  }

  async function copy() {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className={styles.form}>
      <p className={styles.warning}>
        This code is the key to your tale. Anyone who has it can play this run as you. Keep it somewhere
        private, such as a password manager, and never share it or post it.
      </p>
      <p className={styles.note}>
        Use it to restore the tale if this browser forgets it, or to continue on another device.
      </p>
      {code === null ? (
        <button type="button" className={styles.secondary} onClick={reveal} disabled={!entry.runId}>
          Reveal recovery code
        </button>
      ) : (
        <>
          <label className={styles.field}>
            Recovery code
            <textarea className={`${styles.input} ${styles.code}`} readOnly rows={4} value={code}
              onFocus={(e) => e.currentTarget.select()} autoComplete="off" spellCheck={false} />
          </label>
          <div className={styles.cardButtons}>
            <button type="button" className={styles.small} onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
            <button type="button" className={styles.small} onClick={() => { setCode(null); setCopied(false); }}>Hide</button>
          </div>
        </>
      )}
    </div>
  );
}

/** Restores a run from a recovery code after the server confirms the code opens it. */
export function RecoveryImport({ onImported }: { onImported: (entry: VaultEntry, view: GameView) => void }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const decoded = await decodeRecoveryCode(text);
    if (!decoded.ok) {
      setError(decoded.problem);
      return;
    }
    setBusy(true);
    try {
      const { entry, view } = await importRun(decoded.run.runId, decoded.run.token);
      setText('');
      onImported(entry, view);
    } catch (e) {
      setError(e instanceof Error && !('kind' in e) ? e.message : describeError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <p className={styles.note}>Paste the recovery code you saved for a tale. It is checked with the game before it is saved here.</p>
      <label className={styles.field}>
        Recovery code
        <textarea className={`${styles.input} ${styles.code}`} rows={4} value={text} autoComplete="off" spellCheck={false}
          autoCapitalize="off" autoCorrect="off" onChange={(e) => setText(e.target.value)} />
      </label>
      {error && <p className={styles.error} role="alert">{error}</p>}
      <button type="submit" className={styles.primary} disabled={busy || text.trim() === ''}>
        {busy ? 'Checking…' : 'Restore tale'}
      </button>
    </form>
  );
}
