import { ApiError } from '../api/client';
import { createRun, getView } from '../api/runs';
import type { GameView } from '../api/types';
import { generateRunToken, newUuid } from '../security/tokens';
import { addRun, findByRunId, getRun, removeRun, updateRun, type RunSummary, type VaultEntry } from '../storage/vault';

export function summarize(view: GameView): RunSummary {
  return {
    characterName: view.character.name,
    status: view.status,
    region: view.location.region,
    scene: view.location.scene,
    hp: view.character.hp,
    maxHp: view.character.maxHp,
  };
}

/**
 * Saves the credentials for a new tale: a fresh token and creation key, stored BEFORE any request
 * is sent so a lost response can be retried with exactly the same credentials.
 */
export function prepareNewRun(): VaultEntry {
  const entry: VaultEntry = {
    localId: newUuid(),
    runId: null,
    token: generateRunToken(),
    creationKey: newUuid(),
    createdAt: new Date().toISOString(),
    lastOpenedAt: null,
    summary: null,
  };
  addRun(entry);
  return entry;
}

/** Starts a new tale. The invite code is used for this request only and never stored. */
export async function startNewRun(inviteCode: string): Promise<{ entry: VaultEntry; view: GameView }> {
  return finishCreation(prepareNewRun().localId, inviteCode);
}

/**
 * Sends (or re-sends) the creation request for a saved entry with its original key and token.
 *
 * A definite refusal (wrong invite, bad request) of an entry that never had an uncertain attempt
 * removes the entry: no run can exist for it. After an uncertain attempt (lost response, timeout,
 * server error) the entry is always kept, because the run may already exist.
 */
export async function finishCreation(localId: string, inviteCode: string): Promise<{ entry: VaultEntry; view: GameView }> {
  const entry = getRun(localId);
  if (!entry) throw new Error('That tale is no longer saved on this device.');
  try {
    const created = await createRun(inviteCode.trim(), entry.creationKey, entry.token);
    const updated = updateRun(localId, (e) => ({
      ...e, runId: created.runId, summary: summarize(created.view), lastOpenedAt: new Date().toISOString(), creationUncertain: false,
    }))!;
    return { entry: updated, view: created.view };
  } catch (error) {
    if (error instanceof ApiError && !error.uncertain && error.status !== 429 && !entry.creationUncertain && entry.runId === null) {
      removeRun(localId);
    } else if (error instanceof ApiError && error.uncertain) {
      updateRun(localId, (e) => ({ ...e, creationUncertain: true }));
    }
    throw error;
  }
}

export type OpenResult =
  | { kind: 'opened'; entry: VaultEntry; view: GameView }
  | { kind: 'unavailable'; entry: VaultEntry }
  | { kind: 'needsCreation'; entry: VaultEntry };

/** Opens a saved run: reads the current view and refreshes the shelf summary. Never changes the run. */
export async function openRun(localId: string): Promise<OpenResult> {
  const entry = getRun(localId);
  if (!entry) throw new Error('That tale is no longer saved on this device.');
  if (!entry.runId) return { kind: 'needsCreation', entry };
  try {
    const view = await getView(entry.runId, entry.token);
    const updated = updateRun(localId, (e) => ({
      ...e, summary: summarize(view), lastOpenedAt: new Date().toISOString(), unavailable: false,
    }))!;
    return { kind: 'opened', entry: updated, view };
  } catch (error) {
    if (error instanceof ApiError && error.kind === 'http' && (error.status === 401 || error.status === 404)) {
      return { kind: 'unavailable', entry: updateRun(localId, (e) => ({ ...e, unavailable: true }))! };
    }
    if (error instanceof ApiError && error.code === 'RUN_INITIALIZING') return { kind: 'needsCreation', entry };
    throw error;
  }
}

/**
 * Saves a run from a recovery code after the server confirms the token opens it. An already saved
 * run is not duplicated or overwritten.
 */
export async function importRun(runId: string, token: string): Promise<{ entry: VaultEntry; view: GameView; alreadySaved: boolean }> {
  const existing = findByRunId(runId);
  const view = await getView(runId, token);
  if (existing) {
    if (existing.token !== token) {
      throw new Error('A different key for this tale is already saved on this device.');
    }
    return { entry: existing, view, alreadySaved: true };
  }
  const now = new Date().toISOString();
  const entry: VaultEntry = {
    localId: newUuid(),
    runId,
    token,
    // The original creation key is not part of a recovery code; the run already exists, so it is never needed.
    creationKey: newUuid(),
    createdAt: now,
    lastOpenedAt: now,
    summary: summarize(view),
  };
  addRun(entry);
  return { entry, view, alreadySaved: false };
}
