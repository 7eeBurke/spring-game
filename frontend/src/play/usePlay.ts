import { useCallback, useEffect, useRef, useState } from 'react';
import { submitTurn } from '../api/turns';
import type { TurnResponse } from '../api/types';
import type { RunStory } from '../chronicle/useRunStory';
import { newUuid } from '../security/tokens';
import { readDraft, writeDraft } from '../storage/drafts';
import { clearOutbox, discardDamagedOutbox, outboxKey, readOutbox, writeOutboxIfEmpty, type OutboxEntry } from '../storage/outbox';
import { turnFromResponse } from './turnFromResponse';
import { withTurnLock } from './turnLock';
import {
  classify, isDefinitive, rejectionMessage, retryTiming, type TurnOutcome,
} from './turnOutcome';

/** Where the run's one outstanding action stands. Only `idle` accepts a new action. */
export type PlayPhase =
  | { kind: 'idle' }
  | { kind: 'sending'; entry: OutboxEntry; recovering: boolean }
  | { kind: 'waiting'; entry: OutboxEntry; retryAt: number }
  | { kind: 'cooling'; entry: OutboxEntry; until: number }
  | { kind: 'uncertain'; entry: OutboxEntry; autoRetryAt: number | null }
  | { kind: 'elsewhere'; entry: OutboxEntry | null }
  | { kind: 'damaged' };

export interface PlayNotice {
  message: string;
  hint?: string;
  /** The action was refused, so no turn was spent: shown quietly, after the explanation. */
  noTurnSpent?: boolean;
}

export interface Play {
  phase: PlayPhase;
  notice: PlayNotice | null;
  text: string;
  setText: (text: string) => void;
  /** Sends the composer text as a new action, if nothing is outstanding. */
  submit: () => void;
  /** Re-sends the outstanding action with its original key and body (recovery through the turn endpoint). */
  retryNow: () => void;
  /** Clears a damaged outbox record after the player reviewed the story. */
  discardDamaged: () => void;
  dismissNotice: () => void;
  /** No action is outstanding: the player may act. */
  canAct: boolean;
}

/** Runs with an action being sent from this tab; guards against a second sender in the same tab. */
const sendingRuns = new Set<string>();

type LockedResult =
  | { kind: 'sent'; outcome: TurnOutcome }
  | { kind: 'orphaned' } // the outbox no longer holds this entry: another tab finished it
  | { kind: 'occupied' } // a different action is already outstanding
  | { kind: 'damaged' }
  | { kind: 'storageFailed' };

/**
 * Live play for one run. Every action goes through the outbox: saved and verified before it is
 * sent, sent while holding the run's turn lock, and retried only with its original key and body.
 * Nothing here decides game outcomes: Java's answer, and then the chronicle, are authoritative.
 */
export function usePlay(runId: string, token: string, story: RunStory, announce: (text: string) => void): Play {
  const [phase, setPhase] = useState<PlayPhase>({ kind: 'idle' });
  const [notice, setNotice] = useState<PlayNotice | null>(null);
  const [text, setTextState] = useState(() => readDraft(runId));
  const alive = useRef(true);
  const busy = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const inProgressAttempts = useRef(0);
  const autoRetried = useRef<string | null>(null);
  const storyRef = useRef(story);
  storyRef.current = story;
  const announceRef = useRef(announce);
  announceRef.current = announce;

  const setText = useCallback((next: string) => {
    setTextState(next);
    writeDraft(runId, next);
  }, [runId]);

  const clearTimer = () => {
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = null;
  };

  const complete = useCallback((entry: OutboxEntry, response: TurnResponse) => {
    const turn = turnFromResponse(entry.body.input, response);
    storyRef.current.receiveTurn(turn, response.view);
    const cue = response.enemyTurn?.action === 'ATTACK' ? response.view.pendingAttack?.cueText : null;
    announceRef.current(cue ? `${response.narration.text} ${cue}` : response.narration.text);
    void storyRef.current.refresh(); // reconcile with the authoritative chronicle
  }, []);

  // Defined below; referenced by timers.
  const sendRef = useRef<(entry: OutboxEntry, create: boolean) => void>(() => undefined);

  const handle = useCallback((entry: OutboxEntry, outcome: TurnOutcome) => {
    if (isDefinitive(outcome)) {
      clearOutbox(runId, entry.key);
      inProgressAttempts.current = 0;
    }
    switch (outcome.kind) {
      case 'completed':
        setNotice(null);
        setPhase({ kind: 'idle' });
        complete(entry, outcome.response);
        return;
      case 'rejected':
      case 'stale':
      case 'aiUnavailable':
      case 'unexpected': {
        // Never a confirmed turn: the words go back into the field.
        setTextState((current) => {
          const restored = current.trim() ? current : entry.body.input;
          writeDraft(runId, restored);
          return restored;
        });
        setNotice(rejectionMessage(outcome));
        setPhase({ kind: 'idle' });
        if (outcome.kind === 'stale' || outcome.kind === 'unexpected') void storyRef.current.refresh();
        return;
      }
      case 'finished':
      case 'gone':
        setPhase({ kind: 'idle' });
        void storyRef.current.refresh();
        return;
      case 'inProgress': {
        const attempt = inProgressAttempts.current;
        if (attempt < retryTiming.inProgressMs.length) {
          inProgressAttempts.current = attempt + 1;
          const delay = retryTiming.inProgressMs[attempt]!;
          setPhase({ kind: 'waiting', entry, retryAt: Date.now() + delay });
          timer.current = setTimeout(() => sendRef.current(entry, false), delay);
        } else {
          setPhase({ kind: 'uncertain', entry, autoRetryAt: null });
        }
        return;
      }
      case 'rateLimited':
        setPhase({ kind: 'cooling', entry, until: Date.now() + (outcome.retryAfterMs ?? retryTiming.rateLimitFallbackMs) });
        return;
      case 'uncertain':
        if (autoRetried.current !== entry.key) {
          autoRetried.current = entry.key;
          const at = Date.now() + retryTiming.uncertainAutoRetryMs;
          setPhase({ kind: 'uncertain', entry, autoRetryAt: at });
          timer.current = setTimeout(() => {
            timer.current = null;
            if (navigator.onLine !== false) sendRef.current(entry, false);
            // Offline: the 'online' listener sends it when the connection returns.
          }, retryTiming.uncertainAutoRetryMs);
        } else {
          setPhase({ kind: 'uncertain', entry, autoRetryAt: null });
        }
    }
  }, [runId, complete]);

  /** The only path that sends. One sender per tab, one owner per run across tabs. */
  const send = useCallback((entry: OutboxEntry, create: boolean) => {
    if (busy.current || sendingRuns.has(runId)) return;
    busy.current = true;
    sendingRuns.add(runId);
    clearTimer();
    setPhase({ kind: 'sending', entry, recovering: !create });

    void withTurnLock<LockedResult>(runId, async () => {
      if (create) {
        const written = writeOutboxIfEmpty(runId, entry);
        if (written === 'occupied') return { kind: 'occupied' };
        if (written === 'damaged') return { kind: 'damaged' };
        if (written === 'failed') return { kind: 'storageFailed' };
      } else {
        const current = readOutbox(runId);
        if (current.kind === 'damaged') return { kind: 'damaged' };
        if (current.kind !== 'pending' || current.entry.key !== entry.key) return { kind: 'orphaned' };
        entry = current.entry; // exactly what was saved: same key, same input, same stateVersion
      }
      try {
        const response = await submitTurn(runId, token, entry.key, entry.body);
        return { kind: 'sent', outcome: classify({ ok: true, response }) };
      } catch (error) {
        return { kind: 'sent', outcome: classify({ ok: false, error }) };
      }
    }).then((locked) => {
      busy.current = false;
      sendingRuns.delete(runId);
      if (!alive.current) return;
      if (!locked.acquired) {
        setPhase({ kind: 'elsewhere', entry: create ? null : entry });
        if (create) restoreUnsent(entry);
        return;
      }
      const result = locked.value;
      switch (result.kind) {
        case 'sent':
          handle(entry, result.outcome);
          return;
        case 'orphaned': {
          const now = readOutbox(runId);
          setPhase(now.kind === 'pending' ? { kind: 'elsewhere', entry: now.entry } : { kind: 'idle' });
          void storyRef.current.refresh();
          return;
        }
        case 'occupied': {
          restoreUnsent(entry);
          const now = readOutbox(runId);
          setPhase(now.kind === 'pending' ? { kind: 'elsewhere', entry: now.entry } : { kind: 'idle' });
          return;
        }
        case 'damaged':
          if (create) restoreUnsent(entry);
          setPhase({ kind: 'damaged' });
          return;
        case 'storageFailed':
          restoreUnsent(entry);
          setNotice({ message: 'Your action could not be saved on this device, so it was not sent. Check that storage is allowed for this site.' });
          setPhase({ kind: 'idle' });
      }
    });

    function restoreUnsent(unsent: OutboxEntry) {
      setTextState((current) => {
        const restored = current.trim() ? current : unsent.body.input;
        writeDraft(runId, restored);
        return restored;
      });
    }
  }, [runId, token, handle]);
  sendRef.current = send;

  const submit = useCallback(() => {
    const input = text;
    if (!input.trim() || input.length > 500 || busy.current || phase.kind !== 'idle') return;
    const view = storyRef.current.view;
    if (view.status !== 'ACTIVE') return;
    const entry: OutboxEntry = {
      key: newUuid(),
      body: { input, stateVersion: view.stateVersion },
      createdAt: new Date().toISOString(),
    };
    setNotice(null);
    setText('');
    send(entry, true);
  }, [text, phase.kind, send, setText]);

  const retryNow = useCallback(() => {
    if (busy.current) return;
    if (phase.kind === 'cooling' && Date.now() < phase.until) return;
    const current = readOutbox(runId);
    if (current.kind === 'pending') {
      inProgressAttempts.current = 0;
      send(current.entry, false);
    } else if (current.kind === 'none') {
      setPhase({ kind: 'idle' });
      void storyRef.current.refresh();
    } else {
      setPhase({ kind: 'damaged' });
    }
  }, [runId, phase, send]);

  const discardDamaged = useCallback(() => {
    discardDamagedOutbox(runId);
    if (readOutbox(runId).kind === 'none') {
      setPhase({ kind: 'idle' });
      void storyRef.current.refresh();
    }
  }, [runId]);

  // Reopening the run: an outstanding action is recovered automatically, once, with its own key.
  useEffect(() => {
    alive.current = true;
    const current = readOutbox(runId);
    if (current.kind === 'pending') send(current.entry, false);
    else if (current.kind === 'damaged') setPhase({ kind: 'damaged' });
    return () => {
      alive.current = false;
      clearTimer();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [runId]);

  // Another tab started, finished or damaged this run's action.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== outboxKey(runId) || busy.current) return;
      const current = readOutbox(runId);
      setPhase((p) => {
        if (current.kind === 'damaged') return { kind: 'damaged' };
        if (current.kind === 'pending') return p.kind === 'idle' || p.kind === 'elsewhere' ? { kind: 'elsewhere', entry: current.entry } : p;
        return p.kind === 'elsewhere' ? { kind: 'idle' } : p;
      });
      if (current.kind === 'none') void storyRef.current.refresh();
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [runId]);

  // Connection back: send the uncertain action's single automatic retry if it is still due.
  useEffect(() => {
    const onOnline = () => {
      setPhase((p) => {
        if (p.kind === 'uncertain' && p.autoRetryAt !== null && timer.current === null) {
          queueMicrotask(() => sendRef.current(p.entry, false));
        }
        return p;
      });
    };
    window.addEventListener('online', onOnline);
    return () => window.removeEventListener('online', onOnline);
  }, []);

  return {
    phase, notice, text, setText, submit, retryNow, discardDamaged,
    dismissNotice: () => setNotice(null),
    canAct: phase.kind === 'idle' && story.view.status === 'ACTIVE' && !story.unavailable,
  };
}
