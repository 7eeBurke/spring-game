import { describe, expect, it, vi } from 'vitest';
import { ApiError, parseRetryAfter } from '../api/client';
import type { TurnResponse } from '../api/types';
import { betterTurn, newerView } from '../chronicle/freshness';
import { mergeTurns } from '../chronicle/chronicleState';
import { clearOutbox, discardDamagedOutbox, outboxKey, readOutbox, writeOutboxIfEmpty, type OutboxEntry } from '../storage/outbox';
import { sampleView, turn } from '../test/fakeApi';
import { turnFromResponse } from './turnFromResponse';
import { withTurnLock } from './turnLock';
import { classify, isDefinitive, rejectionMessage } from './turnOutcome';

const RUN = 'run-1';
const entry = (key = '11111111-2222-4333-8444-555555555555', input = 'I parry.'): OutboxEntry =>
  ({ key, body: { input, stateVersion: 3 }, createdAt: '2026-10-08T00:00:00.000Z' });

describe('outbox integrity', () => {
  it('saves a request and reads back exactly what was saved', () => {
    expect(writeOutboxIfEmpty(RUN, entry())).toBe('written');
    expect(readOutbox(RUN)).toEqual({ kind: 'pending', entry: entry() });
  });

  it('never overwrites an outstanding request with a new action', () => {
    writeOutboxIfEmpty(RUN, entry());
    expect(writeOutboxIfEmpty(RUN, entry('99999999-2222-4333-8444-555555555555', 'Something else'))).toBe('occupied');
    expect(readOutbox(RUN)).toEqual({ kind: 'pending', entry: entry() });
  });

  it('reports a damaged record instead of discarding it, and refuses new actions over it', () => {
    localStorage.setItem(outboxKey(RUN), '{"key": "not a uuid"');
    expect(readOutbox(RUN)).toEqual({ kind: 'damaged' });
    expect(writeOutboxIfEmpty(RUN, entry())).toBe('damaged');
    localStorage.setItem(outboxKey(RUN), JSON.stringify({ key: entry().key, body: { input: '', stateVersion: 1 }, createdAt: 'x' }));
    expect(readOutbox(RUN).kind).toBe('damaged');
    discardDamagedOutbox(RUN);
    expect(readOutbox(RUN)).toEqual({ kind: 'none' });
  });

  it('reports failure when storage refuses the write, so nothing is sent', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });
    expect(writeOutboxIfEmpty(RUN, entry())).toBe('failed');
    setItem.mockRestore();
  });

  it('clears only the entry with the same key', () => {
    writeOutboxIfEmpty(RUN, entry());
    clearOutbox(RUN, '99999999-2222-4333-8444-555555555555');
    expect(readOutbox(RUN).kind).toBe('pending');
    clearOutbox(RUN, entry().key);
    expect(readOutbox(RUN).kind).toBe('none');
  });
});

describe('outcomes of a turn request', () => {
  const http = (status: number, code: string, reason?: string, retryAfterMs?: number) =>
    ({ ok: false as const, error: new ApiError('http', status, code, 'm', reason, undefined, retryAfterMs) });

  it('keeps the request outstanding only when its fate is unknown or deferred', () => {
    expect(classify(http(409, 'REQUEST_IN_PROGRESS')).kind).toBe('inProgress');
    expect(classify(http(429, 'RATE_LIMITED', undefined, 5000))).toEqual({ kind: 'rateLimited', retryAfterMs: 5000 });
    expect(classify(http(502, 'HTTP_502')).kind).toBe('uncertain');
    expect(classify({ ok: false, error: new ApiError('network', 0, 'NETWORK', 'x') }).kind).toBe('uncertain');
    expect(classify({ ok: false, error: new ApiError('timeout', 0, 'TIMEOUT', 'x') }).kind).toBe('uncertain');
    for (const kind of ['inProgress', 'rateLimited', 'uncertain'] as const) {
      expect(isDefinitive({ kind } as never)).toBe(false);
    }
  });

  it('finishes the request on every definitive answer', () => {
    expect(classify(http(409, 'STALE_VIEW')).kind).toBe('stale');
    expect(classify(http(409, 'RUN_FINISHED')).kind).toBe('finished');
    expect(classify(http(422, 'DEFENSE_REQUIRED')).kind).toBe('rejected');
    expect(classify(http(422, 'INTERPRETATION_FAILED', 'INVALID_OUTPUT')).kind).toBe('rejected');
    expect(classify(http(422, 'INTERPRETATION_FAILED', 'AI_UNAVAILABLE')).kind).toBe('aiUnavailable');
    expect(classify(http(404, 'RUN_NOT_FOUND')).kind).toBe('gone');
    expect(classify(http(400, 'INVALID_REQUEST')).kind).toBe('unexpected');
    expect(isDefinitive(classify(http(422, 'INVALID_COMMAND')))).toBe(true);
  });

  it('explains a refused action with the server hint, and asks which way when a place was unclear', () => {
    const refused = (code: string, reason: string, hint: string) =>
      rejectionMessage(classify({ ok: false, error: new ApiError('http', 422, code, 'm', reason, hint) }));
    const where = 'You are in the Lantern Hearth. From here you can go to the Chapel Road.';

    expect(refused('ACTION_NOT_SUPPORTED', 'NOT_POSSIBLE_YET', where))
      .toEqual({ message: "That can't be done here yet.", hint: where, noTurnSpent: true });
    expect(refused('INTERPRETATION_FAILED', 'UNCLEAR_DESTINATION', 'You named the Chapel Road. ' + where))
      .toEqual({ message: 'Which way did you mean? Name the place you want to go.', hint: 'You named the Chapel Road. ' + where, noTurnSpent: true });
    expect(refused('INTERPRETATION_FAILED', 'UNCLEAR', where))
      .toEqual({ message: "That wasn't clear enough to act on. Try naming what you mean.", hint: where, noTurnSpent: true });
    expect(refused('ACTION_NOT_SUPPORTED', 'ALREADY_THERE', where))
      .toEqual({ message: "You're already there. Say where you want to go next.", hint: where, noTurnSpent: true });
    expect(refused('ACTION_NOT_SUPPORTED', 'UNSUPPORTED', where)?.message).toBe('That is not something you can attempt here.');
  });

  it('asks at a threshold, says where an unreachable thing is, and never says that time has passed', () => {
    const atThreshold = rejectionMessage(classify({ ok: false, error: new ApiError('http', 422, 'ACTION_NOT_SUPPORTED',
      "Before you: the Hollow Chapel's sagging west doors. Do you want to go through?", 'AT_THRESHOLD', 'Say "go through" to enter.') }));
    expect(atThreshold).toEqual({ message: "Before you: the Hollow Chapel's sagging west doors. Do you want to go through?",
      hint: 'Say "go through" to enter.', noTurnSpent: true });
    const outOfReach = rejectionMessage(classify({ ok: false, error: new ApiError('http', 422, 'ACTION_NOT_SUPPORTED',
      'The crate is in the vestment racks, out of reach from here. Go there first.', 'OUT_OF_REACH', 'You are at the threshold.') }));
    expect(outOfReach).toEqual({ message: 'The crate is in the vestment racks, out of reach from here. Go there first.',
      hint: 'You are at the threshold.', noTurnSpent: true });
    for (const reason of ['NOT_POSSIBLE_YET', 'ALREADY_THERE', 'UNSUPPORTED', 'AT_THRESHOLD', 'OUT_OF_REACH']) {
      const shown = rejectionMessage(classify({ ok: false, error: new ApiError('http', 422, 'ACTION_NOT_SUPPORTED', 'm', reason) }));
      expect(shown?.message).not.toMatch(/time has passed/i);
    }
  });

  it('reads Retry-After as seconds or a date', () => {
    expect(parseRetryAfter('7')).toBe(7000);
    expect(parseRetryAfter('Thu, 08 Oct 2026 12:00:10 GMT', Date.parse('Thu, 08 Oct 2026 12:00:00 GMT'))).toBe(10_000);
    expect(parseRetryAfter('soon')).toBeUndefined();
    expect(parseRetryAfter(null)).toBeUndefined();
  });
});

describe('turns built from a response', () => {
  const response = (overrides: Partial<TurnResponse> = {}): TurnResponse => ({
    turnNumber: 4, overall: 'COMPLETE_SUCCESS', narration: { text: 'It lands.', source: 'AI' },
    changes: { playerHpLost: 0, enemiesDefeated: [], movedTo: null, enteredScene: null }, enemyTurn: null,
    view: sampleView({ stateVersion: 4 }), ...overrides,
  });

  it('use only confirmed fields: the exact words and the narration', () => {
    const t = turnFromResponse('I wait.\nThen strike.', response());
    expect(t).toMatchObject({ turnNumber: 4, action: { text: 'I wait.\nThen strike.', kind: 'FREE_TEXT' }, enemy: null, ending: null });
  });

  it('take an attack cue only from the pending attack the response confirms', () => {
    const pendingAttack = { alias: 'attack_1', attacker: 'Bone Warden', cueText: 'Incoming: a sweep.', narration: null };
    const attack = turnFromResponse('/hold', response({ enemyTurn: { attacker: 'Bone Warden', action: 'ATTACK' },
      view: sampleView({ stateVersion: 4, pendingAttack, awaiting: 'DEFENSE' }) }));
    expect(attack.enemy).toEqual({ attacker: 'Bone Warden', action: 'ATTACK', cueText: 'Incoming: a sweep.', narration: null });
    expect(attack.action?.kind).toBe('COMMAND');

    const hold = turnFromResponse('/hold', response({ enemyTurn: { attacker: 'Bone Warden', action: 'HOLD' } }));
    expect(hold.enemy).toEqual({ attacker: 'Bone Warden', action: 'HOLD', cueText: null, narration: null });
  });

  it('name an entered scene without inventing its zone', () => {
    const t = turnFromResponse('/move exit_1', response({ changes: { playerHpLost: 0, enemiesDefeated: [], movedTo: null, enteredScene: 'Ossuary' } }));
    expect(t.enteredScene).toEqual({ scene: 'Ossuary', zone: null });
  });

  it('a defense-only turn carries no enemy', () => {
    expect(turnFromResponse('/defend parry', response()).enemy).toBeNull();
  });
});

describe('never rolling back', () => {
  it('keeps the newer view when an older one arrives late', () => {
    const newer = sampleView({ stateVersion: 5, character: { ...sampleView().character, hp: 9 } });
    const older = sampleView({ stateVersion: 4 });
    expect(newerView(newer, older)).toBe(newer);
    expect(newerView(older, newer)).toBe(newer);
  });

  it('never un-finalises at the same version', () => {
    const done = sampleView({ stateVersion: 5, finalizing: false, lastTurn: { turnNumber: 5, narration: { text: 'x', source: 'AI' } } });
    const stale = sampleView({ stateVersion: 5, finalizing: true, lastTurn: { turnNumber: 5, narration: null } });
    expect(newerView(done, stale)).toBe(done);
    expect(newerView(stale, done)).toBe(done);
  });

  it('never loses a narration to a stale chronicle copy, and fills the zone later', () => {
    const told = turn(3, { enteredScene: { scene: 'Ossuary', zone: null } });
    const stalePending = turn(3, { narration: null, narrationPending: true, enteredScene: { scene: 'Ossuary', zone: 'Bone Stair' } });
    const merged = betterTurn(told, stalePending);
    expect(merged.narration).toEqual(told.narration);
    expect(merged.narrationPending).toBe(false);
    expect(merged.enteredScene).toEqual({ scene: 'Ossuary', zone: 'Bone Stair' });
    expect(mergeTurns([told], [stalePending])).toHaveLength(1);
  });
});

describe('the per-run turn lock without Web Locks', () => {
  it('does not run when another tab holds a live lease', async () => {
    localStorage.setItem('sg.turnlock.v1.run-1', JSON.stringify({ owner: 'other-tab', until: Date.now() + 60_000 }));
    const work = vi.fn(async () => 'sent');
    expect(await withTurnLock('run-1', work)).toEqual({ acquired: false });
    expect(work).not.toHaveBeenCalled();
  });

  it('runs and releases when free, or when the other lease expired', async () => {
    localStorage.setItem('sg.turnlock.v1.run-1', JSON.stringify({ owner: 'other-tab', until: Date.now() - 1 }));
    expect(await withTurnLock('run-1', async () => 'sent')).toEqual({ acquired: true, value: 'sent' });
    expect(localStorage.getItem('sg.turnlock.v1.run-1')).toBeNull();
  });

  it('uses Web Locks when available, without waiting for a held lock', async () => {
    const request = vi.fn(async (_name: string, options: { ifAvailable: boolean }, cb: (lock: unknown) => Promise<unknown>) => {
      expect(options.ifAvailable).toBe(true);
      return cb(null); // held elsewhere
    });
    Object.defineProperty(navigator, 'locks', { value: { request }, configurable: true });
    const work = vi.fn(async () => 'sent');
    expect(await withTurnLock('run-1', work)).toEqual({ acquired: false });
    expect(work).not.toHaveBeenCalled();
    expect(request.mock.calls[0]![0]).toBe('sg-turn-run-1');
    Reflect.deleteProperty(navigator, 'locks');
  });
});
