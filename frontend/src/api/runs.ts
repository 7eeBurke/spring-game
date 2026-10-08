import { apiRequest } from './client';
import type { CreateRunResponse, GameView } from './types';

/** Run creation waits for world generation and the AI introduction. */
const CREATE_TIMEOUT_MS = 90_000;

/**
 * Creates the run for these client-generated credentials, or resumes its creation: the server
 * treats the same creation key and token as the same run, so retrying is always safe.
 */
export function createRun(inviteCode: string, creationKey: string, token: string): Promise<CreateRunResponse> {
  return apiRequest<CreateRunResponse>('/api/v1/runs', {
    method: 'POST',
    token,
    headers: { 'X-Invite-Code': inviteCode, 'Idempotency-Key': creationKey },
    timeoutMs: CREATE_TIMEOUT_MS,
  });
}

/** The current view: never calls AI and never changes the run. */
export function getView(runId: string, token: string): Promise<GameView> {
  return apiRequest<GameView>(`/api/v1/runs/${encodeURIComponent(runId)}`, { token });
}
