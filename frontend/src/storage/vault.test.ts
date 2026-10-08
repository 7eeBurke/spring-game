import { describe, expect, it } from 'vitest';
import { generateRunToken, newUuid } from '../security/tokens';
import { addRun, getRun, listRuns, removeRun, updateRun, type VaultEntry } from './vault';

function entry(overrides: Partial<VaultEntry> = {}): VaultEntry {
  return {
    localId: newUuid(), runId: null, token: generateRunToken(), creationKey: newUuid(),
    createdAt: '2026-10-08T10:00:00.000Z', lastOpenedAt: null, summary: null, ...overrides,
  };
}

describe('the run vault', () => {
  it('saves runs and lists the most recently used first', () => {
    const older = entry({ createdAt: '2026-10-01T00:00:00.000Z' });
    const newer = entry({ createdAt: '2026-10-02T00:00:00.000Z' });
    const opened = entry({ createdAt: '2026-09-01T00:00:00.000Z', lastOpenedAt: '2026-10-03T00:00:00.000Z' });
    addRun(older);
    addRun(newer);
    addRun(opened);
    expect(listRuns().map((e) => e.localId)).toEqual([opened.localId, newer.localId, older.localId]);
  });

  it('never overwrites an existing run', () => {
    const first = entry({ runId: '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f' });
    addRun(first);
    expect(() => addRun({ ...entry(), runId: first.runId })).toThrow();
    expect(() => addRun(first)).toThrow();
    expect(listRuns()).toHaveLength(1);
  });

  it('updates and removes by local id', () => {
    const e = entry();
    addRun(e);
    updateRun(e.localId, (x) => ({ ...x, unavailable: true }));
    expect(getRun(e.localId)?.unavailable).toBe(true);
    removeRun(e.localId);
    expect(listRuns()).toEqual([]);
  });

  it('survives corrupt, foreign or tampered storage', () => {
    localStorage.setItem('sg.vault.v1', '{not json');
    expect(listRuns()).toEqual([]);
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 2, entries: [entry()] }));
    expect(listRuns()).toEqual([]);
    const good = entry();
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 1, entries: [good, { ...entry(), token: 'bad' }, null, 42] }));
    expect(listRuns().map((e) => e.localId)).toEqual([good.localId]);
  });
});
