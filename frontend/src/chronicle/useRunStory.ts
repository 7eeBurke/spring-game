import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { getChronicle } from '../api/chronicle';
import { getView } from '../api/runs';
import type { ChronicleTurn, GameView } from '../api/types';
import { describeError } from '../domain/errors';
import { summarize } from '../runs/runService';
import { newerView } from './freshness';
import { updateRun, type VaultEntry } from '../storage/vault';
import {
  applyLatestPage, applyOlderPage, EMPTY_CHRONICLE, receiveTurn as receive, settleTurn, type ChronicleState,
} from './chronicleState';

export interface RunStory {
  view: GameView;
  chronicle: ChronicleState;
  /** The first chronicle page is loading. */
  loading: boolean;
  loadingOlder: boolean;
  refreshing: boolean;
  /** A player-facing problem with the last request, if any. */
  error: string | null;
  /** This device's key no longer opens the run. */
  unavailable: boolean;
  loadOlder: () => Promise<void>;
  /** Re-reads the current view and latest page (GET only; never calls AI or changes the run). */
  refresh: () => Promise<void>;
  retry: () => void;
  /** For live play (Stage 15D): add a newly completed turn, revealed as new, with the view after it. */
  receiveTurn: (turn: ChronicleTurn, view: GameView) => void;
  settle: (turnNumber: number) => void;
}

/**
 * Owns one run's current view and loaded chronicle. Everything here is a read: no polling, no
 * timers, no AI. Requests that finish after the screen closed are ignored.
 */
export function useRunStory(entry: VaultEntry, initialView: GameView): RunStory {
  const runId = entry.runId!;
  const [view, setView] = useState(initialView);
  const [chronicle, setChronicle] = useState<ChronicleState>(EMPTY_CHRONICLE);
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const alive = useRef(true);
  const olderInFlight = useRef(false);
  const latest = useRef(chronicle);
  latest.current = chronicle;

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const fail = useCallback((e: unknown) => {
    if (!alive.current) return;
    if (e instanceof ApiError && e.kind === 'http' && (e.status === 401 || e.status === 404)) {
      setUnavailable(true);
      updateRun(entry.localId, (x) => ({ ...x, unavailable: true }));
    }
    setError(describeError(e));
  }, [entry.localId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    getChronicle(runId, entry.token)
      .then((page) => {
        if (cancelled || !alive.current) return;
        setChronicle((s) => applyLatestPage(s, page));
      })
      .catch(fail)
      .finally(() => {
        if (!cancelled && alive.current) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [runId, entry.token, attempt, fail]);

  const loadOlder = useCallback(async () => {
    const before = latest.current.nextBefore;
    if (olderInFlight.current || before === null) return;
    olderInFlight.current = true;
    setLoadingOlder(true);
    try {
      const page = await getChronicle(runId, entry.token, { before });
      if (!alive.current) return;
      // Ignore the page if a refresh reset the window meanwhile.
      setChronicle((s) => (s.nextBefore === before ? applyOlderPage(s, page) : s));
    } catch (e) {
      fail(e);
    } finally {
      olderInFlight.current = false;
      if (alive.current) setLoadingOlder(false);
    }
  }, [runId, entry.token, fail]);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    setError(null);
    try {
      const [nextView, page] = await Promise.all([getView(runId, entry.token), getChronicle(runId, entry.token)]);
      if (!alive.current) return;
      setView((current) => newerView(current, nextView));
      setChronicle((s) => applyLatestPage(s, page));
      updateRun(entry.localId, (x) => ({ ...x, summary: summarize(nextView) }));
    } catch (e) {
      fail(e);
    } finally {
      if (alive.current) setRefreshing(false);
    }
  }, [runId, entry.token, entry.localId, fail]);

  const receiveTurn = useCallback((turn: ChronicleTurn, nextView: GameView) => {
    setView((current) => newerView(current, nextView));
    setChronicle((s) => receive(s, turn));
  }, []);

  const settle = useCallback((turnNumber: number) => setChronicle((s) => settleTurn(s, turnNumber)), []);

  return {
    view, chronicle, loading, loadingOlder, refreshing, error, unavailable, loadOlder, refresh,
    retry: () => setAttempt((n) => n + 1), receiveTurn, settle,
  };
}
