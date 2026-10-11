/** Unsent composer text per run, kept across reloads. Best effort: drafts are a convenience. */
const PREFIX = 'sg.draft.v1.';

export function readDraft(runId: string): string {
  try {
    const value = localStorage.getItem(PREFIX + runId);
    return typeof value === 'string' ? value.slice(0, 500) : '';
  } catch {
    return '';
  }
}

export function writeDraft(runId: string, text: string): void {
  try {
    if (text) localStorage.setItem(PREFIX + runId, text);
    else localStorage.removeItem(PREFIX + runId);
  } catch {
    // Storage unavailable: the draft simply is not kept.
  }
}
