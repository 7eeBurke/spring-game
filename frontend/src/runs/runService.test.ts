import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { addRun, getRun, listRuns } from '../storage/vault';
import { generateRunToken, newUuid } from '../security/tokens';
import { apiError, fakeApi, json, networkFailure, RUN_ID, sampleView } from '../test/fakeApi';
import { finishCreation, importRun, openRun, startNewRun } from './runService';

const INVITE = 'secret-invite';

describe('creating a run', () => {
  it('saves the token and creation key before sending, and uses them for the request', async () => {
    let savedAtSend: ReturnType<typeof listRuns> = [];
    const api = fakeApi((call) => {
      savedAtSend = listRuns();
      expect(call.headers['X-Invite-Code']).toBe(INVITE);
      return json(201, { runId: RUN_ID, view: sampleView() });
    });

    const { entry, view } = await startNewRun(INVITE);

    expect(savedAtSend).toHaveLength(1);
    expect(savedAtSend[0]!.runId).toBeNull();
    expect(api.calls[0]!.headers.Authorization).toBe(`Bearer ${savedAtSend[0]!.token}`);
    expect(api.calls[0]!.headers['Idempotency-Key']).toBe(savedAtSend[0]!.creationKey);
    expect(entry.runId).toBe(RUN_ID);
    expect(entry.summary).toMatchObject({ characterName: 'Wren', status: 'ACTIVE' });
    expect(view.runId).toBe(RUN_ID);
  });

  it('never stores the invite code', async () => {
    fakeApi(() => json(201, { runId: RUN_ID, view: sampleView() }));
    await startNewRun(INVITE);
    for (let i = 0; i < localStorage.length; i++) {
      expect(localStorage.getItem(localStorage.key(i)!)).not.toContain(INVITE);
    }
  });

  it('retries a lost creation with exactly the same credentials', async () => {
    const api = fakeApi((_, i) => (i === 0 ? networkFailure() : json(201, { runId: RUN_ID, view: sampleView() })));

    const lost = await startNewRun(INVITE).catch((e: unknown) => e);
    expect((lost as ApiError).uncertain).toBe(true);
    const pending = listRuns()[0]!;
    expect(pending.runId).toBeNull();
    expect(pending.creationUncertain).toBe(true);

    const { entry } = await finishCreation(pending.localId, INVITE);
    expect(api.calls[1]!.headers.Authorization).toBe(api.calls[0]!.headers.Authorization);
    expect(api.calls[1]!.headers['Idempotency-Key']).toBe(api.calls[0]!.headers['Idempotency-Key']);
    expect(entry.runId).toBe(RUN_ID);
    expect(listRuns()).toHaveLength(1);
  });

  it('forgets a first attempt the server definitely refused', async () => {
    fakeApi(() => apiError(401, 'UNAUTHORIZED', 'The invite code was not accepted.'));
    await expect(startNewRun('wrong')).rejects.toBeInstanceOf(ApiError);
    expect(listRuns()).toEqual([]);
  });

  it('keeps an entry whose earlier attempt may have created the run, even if a retry is refused', async () => {
    fakeApi((_, i) => (i === 0 ? networkFailure() : apiError(401, 'UNAUTHORIZED', 'The invite code was not accepted.')));
    await startNewRun(INVITE).catch(() => undefined);
    const pending = listRuns()[0]!;
    await expect(finishCreation(pending.localId, 'typo')).rejects.toBeInstanceOf(ApiError);
    expect(getRun(pending.localId)).toBeDefined();
  });

  it('keeps the entry when rate limited', async () => {
    fakeApi(() => apiError(429, 'RATE_LIMITED'));
    await expect(startNewRun(INVITE)).rejects.toBeInstanceOf(ApiError);
    expect(listRuns()).toHaveLength(1);
  });
});

describe('opening and restoring runs', () => {
  function saved(runId: string | null = RUN_ID) {
    const e = { localId: newUuid(), runId, token: generateRunToken(), creationKey: newUuid(),
      createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null, summary: null };
    addRun(e);
    return e;
  }

  it('opens a run with a GET and refreshes its summary', async () => {
    const e = saved();
    const api = fakeApi(() => json(200, sampleView({ status: 'DEAD' })));
    const result = await openRun(e.localId);
    expect(result.kind).toBe('opened');
    expect(api.calls[0]!.method).toBe('GET');
    expect(getRun(e.localId)?.summary?.status).toBe('DEAD');
  });

  it('marks a run this device can no longer open', async () => {
    const e = saved();
    fakeApi(() => apiError(404, 'RUN_NOT_FOUND'));
    expect((await openRun(e.localId)).kind).toBe('unavailable');
    expect(getRun(e.localId)?.unavailable).toBe(true);
  });

  it('sends an unconfirmed run back to creation without calling the server', async () => {
    const e = saved(null);
    const api = fakeApi(() => json(200, {}));
    expect((await openRun(e.localId)).kind).toBe('needsCreation');
    expect(api.calls).toHaveLength(0);
  });

  it('imports a recovered run only after the server accepts its token', async () => {
    const token = generateRunToken();
    const api = fakeApi(() => json(200, sampleView()));
    const { entry, alreadySaved } = await importRun(RUN_ID, token);
    expect(alreadySaved).toBe(false);
    expect(api.calls[0]!.headers.Authorization).toBe(`Bearer ${token}`);
    expect(entry).toMatchObject({ runId: RUN_ID, token });
    expect((await importRun(RUN_ID, token)).alreadySaved).toBe(true);
    await expect(importRun(RUN_ID, generateRunToken())).rejects.toThrow(/different key/);
    expect(listRuns()).toHaveLength(1);
  });

  it('does not save a code the server rejects', async () => {
    fakeApi(() => apiError(404, 'RUN_NOT_FOUND'));
    await expect(importRun(RUN_ID, generateRunToken())).rejects.toBeInstanceOf(ApiError);
    expect(listRuns()).toEqual([]);
  });
});
