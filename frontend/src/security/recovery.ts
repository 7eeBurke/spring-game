import { isRunToken, isUuid } from './tokens';

/**
 * Recovery codes let a player keep a run when this browser's storage is cleared, or move it to
 * another device. A code IS the run's secret: whoever has it can play the run. It is shown only
 * after an explicit reveal, copied only on request, and never placed in a URL, log, analytics or
 * the service worker.
 *
 * Format: `HC1.<runId>.<token>.<check>` where check is the first 8 hex characters of
 * SHA-256(runId + "." + token), to catch copy mistakes (not a security feature).
 */
const PREFIX = 'HC1';

export interface RecoveredRun {
  runId: string;
  token: string;
}

export async function encodeRecoveryCode(runId: string, token: string): Promise<string> {
  if (!isUuid(runId) || !isRunToken(token)) throw new Error('Not a valid run to export.');
  return `${PREFIX}.${runId.toLowerCase()}.${token}.${await checksum(runId.toLowerCase(), token)}`;
}

export type DecodeResult = { ok: true; run: RecoveredRun } | { ok: false; problem: string };

export async function decodeRecoveryCode(input: string): Promise<DecodeResult> {
  const compact = input.replace(/\s+/g, '');
  const parts = compact.split('.');
  if (parts.length !== 4 || parts[0] !== PREFIX) {
    return { ok: false, problem: 'That does not look like a recovery code. It should start with "HC1."' };
  }
  const [, runId, token, check] = parts as [string, string, string, string];
  if (!isUuid(runId) || !isRunToken(token)) {
    return { ok: false, problem: 'The recovery code is incomplete. Copy the whole code and try again.' };
  }
  if (check.toLowerCase() !== (await checksum(runId.toLowerCase(), token))) {
    return { ok: false, problem: 'The recovery code has a typo in it. Copy it again exactly.' };
  }
  return { ok: true, run: { runId: runId.toLowerCase(), token } };
}

async function checksum(runId: string, token: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`${runId}.${token}`));
  return Array.from(new Uint8Array(digest).slice(0, 4), (b) => b.toString(16).padStart(2, '0')).join('');
}
