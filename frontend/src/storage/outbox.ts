import type { TurnRequestBody } from '../api/types';
import { isUuid } from '../security/tokens';

/**
 * The one turn request a run may have outstanding: its idempotency key and the exact body, saved
 * and verified BEFORE the request is sent. Retries always resend this record unchanged, so an
 * action can never be applied twice or under a different key.
 *
 * An entry is never overwritten by a new action. A record that exists but cannot be read is
 * reported as damaged, never silently dropped: it may describe an action the server applied.
 */
export interface OutboxEntry {
  key: string;
  body: TurnRequestBody;
  createdAt: string;
}

export type OutboxRead =
  | { kind: 'none' }
  | { kind: 'pending'; entry: OutboxEntry }
  | { kind: 'damaged' };

const PREFIX = 'sg.outbox.v1.';

export function outboxKey(runId: string): string {
  return PREFIX + runId;
}

function valid(value: unknown): value is OutboxEntry {
  const e = value as Partial<OutboxEntry> | null;
  return !!e && typeof e.key === 'string' && isUuid(e.key) && typeof e.createdAt === 'string'
    && !!e.body && typeof e.body.input === 'string' && e.body.input.length >= 1 && e.body.input.length <= 500
    && Number.isInteger(e.body.stateVersion) && e.body.stateVersion >= 0;
}

export function readOutbox(runId: string): OutboxRead {
  let raw: string | null;
  try {
    raw = localStorage.getItem(outboxKey(runId));
  } catch {
    return { kind: 'damaged' };
  }
  if (raw === null) return { kind: 'none' };
  try {
    const parsed: unknown = JSON.parse(raw);
    return valid(parsed) ? { kind: 'pending', entry: parsed } : { kind: 'damaged' };
  } catch {
    return { kind: 'damaged' };
  }
}

export type WriteResult = 'written' | 'occupied' | 'damaged' | 'failed';

/**
 * Saves a new request only if the run has none outstanding, and confirms it reads back exactly.
 * 'failed' means storage is unavailable: the caller must not send.
 */
export function writeOutboxIfEmpty(runId: string, entry: OutboxEntry): WriteResult {
  const current = readOutbox(runId);
  if (current.kind === 'pending') return 'occupied';
  if (current.kind === 'damaged') return 'damaged';
  const serialized = JSON.stringify(entry);
  try {
    localStorage.setItem(outboxKey(runId), serialized);
    return localStorage.getItem(outboxKey(runId)) === serialized ? 'written' : 'failed';
  } catch {
    return 'failed';
  }
}

/** Removes the request once the server answered it definitively; only if it is still this key. */
export function clearOutbox(runId: string, key: string): void {
  const current = readOutbox(runId);
  if (current.kind === 'pending' && current.entry.key === key) {
    try {
      localStorage.removeItem(outboxKey(runId));
    } catch {
      // Storage unavailable: the entry stays and is resent (same key) next time, which is harmless.
    }
  }
}

/** Removes a damaged record, only after the player reconciled with the server and chose to. */
export function discardDamagedOutbox(runId: string): void {
  if (readOutbox(runId).kind === 'damaged') {
    try {
      localStorage.removeItem(outboxKey(runId));
    } catch {
      // Nothing more can be done; the record stays reported as damaged.
    }
  }
}
