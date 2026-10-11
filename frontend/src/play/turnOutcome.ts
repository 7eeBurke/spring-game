import { ApiError } from '../api/client';
import type { TurnResponse } from '../api/types';

/**
 * What a turn request's answer means for the outstanding request, from the Stage 14 contract:
 * - definitive answers (the outbox entry is finished): completed, a stored rejection, stale,
 *   AI unavailable (key released), finished, gone, unexpected;
 * - answers that leave the request outstanding (retry with the SAME key): in progress, rate
 *   limited, uncertain (lost connection, timeout, server error).
 */
export type TurnOutcome =
  | { kind: 'completed'; response: TurnResponse }
  | { kind: 'rejected'; code: string; reason?: string; message: string; hint?: string }
  | { kind: 'stale' }
  | { kind: 'aiUnavailable'; hint?: string }
  | { kind: 'finished' }
  | { kind: 'gone' }
  | { kind: 'unexpected'; code: string }
  | { kind: 'inProgress' }
  | { kind: 'rateLimited'; retryAfterMs?: number }
  | { kind: 'uncertain' };

export function classify(result: { ok: true; response: TurnResponse } | { ok: false; error: unknown }): TurnOutcome {
  if (result.ok) return { kind: 'completed', response: result.response };
  const error = result.error;
  if (!(error instanceof ApiError)) return { kind: 'uncertain' };
  if (error.kind !== 'http' || error.status >= 500) {
    return error.status === 503 && error.retryAfterMs !== undefined ? { kind: 'rateLimited', retryAfterMs: error.retryAfterMs } : { kind: 'uncertain' };
  }
  switch (error.status) {
    case 401:
    case 404:
      return { kind: 'gone' };
    case 429:
      return { kind: 'rateLimited', retryAfterMs: error.retryAfterMs };
    case 409:
      switch (error.code) {
        case 'STALE_VIEW': return { kind: 'stale' };
        case 'REQUEST_IN_PROGRESS': return { kind: 'inProgress' };
        case 'RUN_FINISHED': return { kind: 'finished' };
        default: return { kind: 'unexpected', code: error.code };
      }
    case 422:
      if (error.code === 'INTERPRETATION_FAILED' && error.reason === 'AI_UNAVAILABLE') return { kind: 'aiUnavailable', hint: error.hint };
      return { kind: 'rejected', code: error.code, reason: error.reason, message: error.message, hint: error.hint };
    default:
      return { kind: 'unexpected', code: error.code };
  }
}

/** Whether the outbox entry is finished with (true) or must be kept for a same-key retry (false). */
export function isDefinitive(outcome: TurnOutcome): boolean {
  return !['inProgress', 'rateLimited', 'uncertain'].includes(outcome.kind);
}

/**
 * Retry timing, in one place. REQUEST_IN_PROGRESS gets four bounded retries before the request is
 * shown as uncertain; a rate limit waits for the server's Retry-After (or the fallback); an
 * uncertain request gets exactly one automatic retry.
 */
export const retryTiming = {
  inProgressMs: [3_000, 6_000, 12_000, 24_000],
  rateLimitFallbackMs: 20_000,
  uncertainAutoRetryMs: 3_000,
};

/**
 * Player-facing wording for definitive problems. A refused action (422) never spends a turn; the UI
 * says so quietly after the explanation (`noTurnSpent`).
 */
export function rejectionMessage(outcome: TurnOutcome): { message: string; hint?: string; noTurnSpent?: boolean } | null {
  switch (outcome.kind) {
    case 'rejected':
      return { ...refusal(outcome), noTurnSpent: true };
    case 'aiUnavailable':
      return { message: 'Free-text actions are unavailable right now. Commands still work.', hint: outcome.hint };
    case 'stale':
      return { message: 'The story moved on while you were writing. Read on, then send your action again.' };
    case 'unexpected':
      return { message: 'The game could not accept that action. Your words are back in the field.' };
    default:
      return null;
  }
}

/** What a refusal means, explained first; the hint is the server's grounded guidance. */
function refusal(outcome: Extract<TurnOutcome, { kind: 'rejected' }>): { message: string; hint?: string } {
  switch (outcome.code) {
    case 'DEFENSE_REQUIRED':
      return { message: 'An attack is coming: your action must begin by defending against it.', hint: outcome.hint };
    case 'DEFENSE_NOT_RESOLVED':
      return { message: 'That defense could not be carried out, so nothing happened. The attack is still coming.', hint: outcome.hint };
    case 'INVALID_COMMAND':
      return { message: 'That command could not be understood.', hint: outcome.hint };
    case 'ACTION_NOT_SUPPORTED':
      // At the threshold of a new place, the server asks the specific question ("Before you: ... Do you want to go through?").
      if (outcome.reason === 'AT_THRESHOLD') return { message: outcome.message, hint: outcome.hint };
      // Reaching for something not within reach: the server says where it is and what to do.
      if (outcome.reason === 'OUT_OF_REACH') return { message: outcome.message, hint: outcome.hint };
      if (outcome.reason === 'ALREADY_THERE') return { message: "You're already there. Say where you want to go next.", hint: outcome.hint };
      if (outcome.reason === 'NOT_POSSIBLE_YET') return { message: "That can't be done here yet.", hint: outcome.hint };
      return { message: 'That is not something you can attempt here.', hint: outcome.hint };
    case 'INTERPRETATION_FAILED':
      if (outcome.reason === 'UNCLEAR') return { message: "That wasn't clear enough to act on. Try naming what you mean.", hint: outcome.hint };
      if (outcome.reason === 'UNCLEAR_DESTINATION') return { message: 'Which way did you mean? Name the place you want to go.', hint: outcome.hint };
      return { message: 'The storyteller could not make sense of that. Try saying it another way.', hint: outcome.hint };
    default:
      return { message: outcome.message, hint: outcome.hint };
  }
}
