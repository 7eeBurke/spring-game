import type { RunStatus } from '../api/types';
import { isRunToken, isUuid } from '../security/tokens';

/**
 * The runs this device can open: each one's secret token and creation key, saved BEFORE the
 * creation request is sent so a lost response can be retried with the same credentials.
 *
 * Stored in localStorage. Anything running on this page could read it, so the app allows no
 * third-party scripts and renders all game text as plain text. Tokens never go in URLs, logs or the
 * service worker. The invite code is never stored.
 */
export interface VaultEntry {
  /** Local identifier, stable before the server assigns a runId. */
  localId: string;
  /** Null until the server confirmed the run (creation pending). */
  runId: string | null;
  token: string;
  creationKey: string;
  createdAt: string;
  lastOpenedAt: string | null;
  /** Last known summary, for the shelf; refreshed from the server whenever the run is opened. */
  summary: RunSummary | null;
  /** The server no longer accepts this device's token for the run. */
  unavailable?: boolean;
  /** A creation attempt ended without a definite answer, so the run may already exist. */
  creationUncertain?: boolean;
}

export interface RunSummary {
  characterName: string;
  status: RunStatus;
  region: string | null;
  scene: string;
  hp: number;
  maxHp: number;
}

const KEY = 'sg.vault.v1';

interface StoredVault {
  version: 1;
  entries: VaultEntry[];
}

function valid(entry: unknown): entry is VaultEntry {
  const e = entry as Partial<VaultEntry> | null;
  return !!e && typeof e.localId === 'string' && (e.runId === null || (typeof e.runId === 'string' && isUuid(e.runId)))
    && typeof e.token === 'string' && isRunToken(e.token)
    && typeof e.creationKey === 'string' && isUuid(e.creationKey) && typeof e.createdAt === 'string';
}

/** All saved runs, most recently opened or created first. Corrupt data is ignored, never fatal. */
export function listRuns(): VaultEntry[] {
  return read().sort((a, b) => (b.lastOpenedAt ?? b.createdAt).localeCompare(a.lastOpenedAt ?? a.createdAt));
}

export function getRun(localId: string): VaultEntry | undefined {
  return read().find((e) => e.localId === localId);
}

export function findByRunId(runId: string): VaultEntry | undefined {
  return read().find((e) => e.runId === runId);
}

/** Adds a new entry; never replaces an existing run. */
export function addRun(entry: VaultEntry): void {
  const entries = read();
  if (entries.some((e) => e.localId === entry.localId || (entry.runId && e.runId === entry.runId))) {
    throw new Error('This run is already saved on this device.');
  }
  write([...entries, entry]);
}

export function updateRun(localId: string, change: (entry: VaultEntry) => VaultEntry): VaultEntry | undefined {
  const entries = read();
  const index = entries.findIndex((e) => e.localId === localId);
  if (index < 0) return undefined;
  const updated = change(entries[index]!);
  entries[index] = updated;
  write(entries);
  return updated;
}

export function removeRun(localId: string): void {
  write(read().filter((e) => e.localId !== localId));
}

function read(): VaultEntry[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as Partial<StoredVault>;
    if (parsed.version !== 1 || !Array.isArray(parsed.entries)) return [];
    return parsed.entries.filter(valid);
  } catch {
    return [];
  }
}

function write(entries: VaultEntry[]): void {
  const vault: StoredVault = { version: 1, entries };
  localStorage.setItem(KEY, JSON.stringify(vault));
}

/** Asks the browser not to evict saved runs under storage pressure (best effort). */
export async function requestPersistentStorage(): Promise<boolean> {
  try {
    return (await navigator.storage?.persist?.()) ?? false;
  } catch {
    return false;
  }
}
