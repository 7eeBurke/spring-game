import { useState, type FormEvent } from 'react';
import type { GameView } from '../api/types';
import { GAME_TAGLINE, GAME_TITLE } from '../config';
import { describeError } from '../domain/errors';
import { finishCreation, openRun, prepareNewRun } from '../runs/runService';
import { Sheet } from '../story/Sheet';
import { getRun, listRuns, removeRun, type VaultEntry } from '../storage/vault';
import { RecoveryExport, RecoveryImport } from './RecoveryPanels';
import styles from './Shelf.module.css';

type SheetState = { kind: 'none' } | { kind: 'export'; entry: VaultEntry } | { kind: 'import' };

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'In progress', DEAD: 'Fallen', VICTORIOUS: 'Victorious', INITIALIZING: 'Being created',
};

export function ShelfScreen({ onOpen }: { onOpen: (entry: VaultEntry, view: GameView) => void }) {
  const [runs, setRuns] = useState<VaultEntry[]>(() => listRuns());
  const [creating, setCreating] = useState<{ localId: string | null } | null>(null);
  const [invite, setInvite] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [sheet, setSheet] = useState<SheetState>({ kind: 'none' });

  const refresh = () => setRuns(listRuns());

  async function create(event: FormEvent) {
    event.preventDefault();
    if (busy || !creating) return;
    setBusy(true);
    setError(null);
    // Bind the form to the saved credentials first: any retry after a failure reuses them.
    const localId = creating.localId ?? prepareNewRun().localId;
    setCreating({ localId });
    try {
      const { entry, view } = await finishCreation(localId, invite);
      setInvite('');
      setCreating(null);
      refresh();
      onOpen(entry, view);
    } catch (e) {
      setError(describeError(e));
      // A definite refusal of a fresh attempt removes the entry: start clean next time.
      if (!getRun(localId)) setCreating({ localId: null });
      refresh();
    } finally {
      setBusy(false);
    }
  }

  async function open(entry: VaultEntry) {
    setError(null);
    setNotice(null);
    try {
      const result = await openRun(entry.localId);
      refresh();
      if (result.kind === 'opened') onOpen(result.entry, result.view);
      else if (result.kind === 'unavailable') setNotice('This device can no longer open that tale.');
      else setCreating({ localId: entry.localId });
    } catch (e) {
      setNotice(describeError(e));
    }
  }

  function forget(entry: VaultEntry) {
    // Forgetting removes only this device's key; the run itself stays on the server.
    const warning = entry.runId
      ? 'Forget this tale on this device?\n\nThis only removes this device\'s key to it: the tale itself is not deleted '
        + 'from the game. But unless you have saved its recovery code, you will not be able to open it again.'
      : 'Forget this unconfirmed tale on this device?\n\nIf it was in fact created, it is not deleted from the game, '
        + 'but this device will no longer be able to open it.';
    if (window.confirm(warning)) {
      removeRun(entry.localId);
      refresh();
    }
  }

  return (
    <div className={styles.shelf}>
      <div className={styles.column}>
        <h1 className={styles.title}>{GAME_TITLE}</h1>
        <p className={styles.subtitle}>{GAME_TAGLINE}</p>
        <div className={styles.ornament} aria-hidden="true">✦</div>

        {creating ? (
          <form className={styles.form} onSubmit={create} aria-label="Begin a new tale">
            <label className={styles.field}>
              Invite code
              <input className={styles.input} value={invite} onChange={(e) => setInvite(e.target.value)}
                autoComplete="off" autoCapitalize="off" spellCheck={false} required />
            </label>
            {creating.localId && <p className={styles.note}>This tale was started but not confirmed. Enter the invite code again to finish creating it.</p>}
            {error && <p className={styles.error} role="alert">{error}</p>}
            {busy && <p className={styles.status} role="status">The lantern is lit, and a stranger stirs…</p>}
            <button type="submit" className={styles.primary} disabled={busy || invite.trim() === ''}>
              {creating.localId ? 'Finish creating' : 'Begin'}
            </button>
            <button type="button" className={styles.secondary} disabled={busy} onClick={() => { setCreating(null); setError(null); }}>
              Cancel
            </button>
          </form>
        ) : (
          <div className={styles.actions}>
            <button type="button" className={styles.primary} onClick={() => setCreating({ localId: null })}>Begin a new tale</button>
            <button type="button" className={styles.secondary} onClick={() => setSheet({ kind: 'import' })}>Restore from a recovery code</button>
          </div>
        )}

        {notice && <p className={styles.error} role="alert">{notice}</p>}

        <h2 className={styles.heading}>Your tales</h2>
        {runs.length === 0 ? <p className={styles.empty}>No tales on this device yet.</p> : (
          <ul className={styles.cards}>
            {runs.map((entry) => {
              const pending = entry.runId === null;
              const status = entry.unavailable ? 'UNAVAILABLE' : pending ? 'PENDING' : entry.summary?.status ?? 'ACTIVE';
              const label = entry.unavailable ? 'Unavailable' : pending ? 'Not confirmed' : STATUS_LABEL[status] ?? status;
              const finished = status === 'DEAD' || status === 'VICTORIOUS';
              return (
                <li key={entry.localId} className={styles.card}>
                  <div className={styles.cardTop}>
                    <h3 className={styles.cardName}>{entry.summary?.characterName ?? 'An unnamed stranger'}</h3>
                    <span className={`${styles.pill} ${styles[status] ?? ''}`}>{label}</span>
                  </div>
                  <p className={styles.cardPlace}>
                    {entry.summary
                      ? `${entry.summary.region ? `${entry.summary.region} · ` : ''}${entry.summary.scene} · ♥ ${entry.summary.hp}/${entry.summary.maxHp}`
                      : 'Creation was not confirmed.'}
                  </p>
                  <div className={styles.cardButtons}>
                    {pending
                      ? <button type="button" className={styles.small} onClick={() => setCreating({ localId: entry.localId })}>Finish creating</button>
                      : !entry.unavailable && <button type="button" className={styles.small} onClick={() => open(entry)}>{finished ? 'Read' : 'Continue'}</button>}
                    {!pending && !entry.unavailable && (
                      <button type="button" className={styles.small} onClick={() => setSheet({ kind: 'export', entry })}>Recovery code</button>
                    )}
                    <button type="button" className={`${styles.small} ${styles.danger}`} onClick={() => forget(entry)}>Forget</button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <Sheet title="Recovery code" open={sheet.kind === 'export'} onClose={() => setSheet({ kind: 'none' })}>
        {sheet.kind === 'export' && <RecoveryExport entry={sheet.entry} />}
      </Sheet>
      <Sheet title="Restore a tale" open={sheet.kind === 'import'} onClose={() => setSheet({ kind: 'none' })}>
        {sheet.kind === 'import' && (
          <RecoveryImport onImported={(entry, view) => { setSheet({ kind: 'none' }); refresh(); onOpen(entry, view); }} />
        )}
      </Sheet>
    </div>
  );
}
