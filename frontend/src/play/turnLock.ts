/**
 * One owner per run for anything that sends a turn: writing a new outbox entry and sending it, or
 * replaying an outstanding one. Two tabs therefore never send different keys for the same run, nor
 * replay the same entry at once.
 *
 * Uses the Web Locks API (exclusive, released automatically if the tab dies). Without it, a
 * localStorage lease is used: best effort only, because storage has no atomic compare-and-set, but
 * conservative: a tab that does not clearly own the lease does not send. Either way the server
 * still enforces one unfinished turn per run and same-key idempotency.
 */
export type LockResult<T> = { acquired: true; value: T } | { acquired: false };

const TAB_ID = typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : String(Math.random());
const LEASE_MS = 130_000; // longer than the slowest turn request (100 s)

interface LocksLike {
  request<T>(name: string, options: { ifAvailable: boolean }, callback: (lock: unknown) => Promise<T>): Promise<T>;
}

function webLocks(): LocksLike | null {
  const locks = (globalThis.navigator as Navigator & { locks?: LocksLike } | undefined)?.locks;
  return locks && typeof locks.request === 'function' ? locks : null;
}

/** Runs `work` while holding the run's turn lock, or reports that another owner holds it. Never waits. */
export async function withTurnLock<T>(runId: string, work: () => Promise<T>): Promise<LockResult<T>> {
  const locks = webLocks();
  if (locks) {
    return locks.request(`sg-turn-${runId}`, { ifAvailable: true }, async (lock) => {
      if (!lock) return { acquired: false } as LockResult<T>;
      return { acquired: true, value: await work() } as LockResult<T>;
    });
  }
  return withLease(runId, work);
}

const leaseKey = (runId: string) => `sg.turnlock.v1.${runId}`;

interface Lease {
  owner: string;
  until: number;
}

function readLease(runId: string): Lease | null {
  try {
    const raw = localStorage.getItem(leaseKey(runId));
    if (!raw) return null;
    const lease = JSON.parse(raw) as Lease;
    return typeof lease.owner === 'string' && typeof lease.until === 'number' ? lease : null;
  } catch {
    return null;
  }
}

async function withLease<T>(runId: string, work: () => Promise<T>): Promise<LockResult<T>> {
  const now = Date.now();
  const held = readLease(runId);
  if (held && held.owner !== TAB_ID && held.until > now) return { acquired: false };
  try {
    localStorage.setItem(leaseKey(runId), JSON.stringify({ owner: TAB_ID, until: now + LEASE_MS }));
  } catch {
    return { acquired: false };
  }
  // Let a competing tab's write land, then confirm we still own the lease.
  await new Promise((resolve) => setTimeout(resolve, 50));
  if (readLease(runId)?.owner !== TAB_ID) return { acquired: false };
  try {
    return { acquired: true, value: await work() };
  } finally {
    if (readLease(runId)?.owner === TAB_ID) {
      try {
        localStorage.removeItem(leaseKey(runId));
      } catch {
        // The lease expires on its own.
      }
    }
  }
}
