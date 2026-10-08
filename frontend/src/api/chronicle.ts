import { apiRequest } from './client';
import type { ChronicleView } from './types';

export const CHRONICLE_PAGE_SIZE = 20;

/**
 * One page of a run's story, oldest turn first. Read-only on the server: it never calls AI and never
 * changes the run. Pass the previous page's nextBefore as `before` for older turns.
 */
export function getChronicle(runId: string, token: string, options: { before?: number; limit?: number } = {}): Promise<ChronicleView> {
  const params = new URLSearchParams({ limit: String(options.limit ?? CHRONICLE_PAGE_SIZE) });
  if (options.before !== undefined) params.set('before', String(options.before));
  return apiRequest<ChronicleView>(`/api/v1/runs/${encodeURIComponent(runId)}/chronicle?${params}`, { token });
}
