import { apiRequest } from './client';
import type { TurnRequestBody, TurnResponse } from './types';

/** Interpretation, resolution and narration can take a while; beyond this the outcome is uncertain. */
export const TURN_TIMEOUT_MS = 100_000;

/**
 * Submits one turn. The server answers the same key with the same stored result, so a retry with
 * the identical key and body can never apply the action twice.
 */
export function submitTurn(runId: string, token: string, key: string, body: TurnRequestBody): Promise<TurnResponse> {
  return apiRequest<TurnResponse>(`/api/v1/runs/${encodeURIComponent(runId)}/turns`, {
    method: 'POST',
    token,
    headers: { 'Idempotency-Key': key },
    body: { input: body.input, stateVersion: body.stateVersion },
    timeoutMs: TURN_TIMEOUT_MS,
  });
}
