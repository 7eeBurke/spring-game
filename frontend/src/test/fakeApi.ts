import { vi } from 'vitest';
import { setFetcher } from '../api/client';
import type { ChronicleTurn, ChronicleView, GameView } from '../api/types';

export interface RecordedCall {
  url: string;
  method: string;
  headers: Record<string, string>;
  body: string | undefined;
}

type Handler = (call: RecordedCall, index: number) => Response | Promise<Response>;

/** Installs a scripted transport and records every request. No network is ever used. */
export function fakeApi(handler: Handler) {
  const calls: RecordedCall[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const call: RecordedCall = {
      url: String(input),
      method: init?.method ?? 'GET',
      headers: { ...(init?.headers as Record<string, string>) },
      body: init?.body as string | undefined,
    };
    calls.push(call);
    return handler(call, calls.length - 1);
  });
  setFetcher(fn as unknown as typeof fetch);
  return { calls, fn };
}

export function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

export function apiError(status: number, code: string, message = 'refused'): Response {
  return json(status, { error: { code, message } });
}

export function networkFailure(): never {
  throw new TypeError('Failed to fetch');
}

export const RUN_ID = '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f';

export function sampleView(overrides: Partial<GameView> = {}): GameView {
  return {
    runId: RUN_ID,
    status: 'ACTIVE',
    stateVersion: 0,
    awaiting: 'ACTION',
    finalizing: false,
    introduction: { text: 'You are Wren.', source: 'AI' },
    objective: 'Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.',
    character: {
      name: 'Wren', hp: 24, maxHp: 24, stats: { MIGHT: 5 }, fated: 1, fatedBand: 'TOUCHED',
      body: [{ part: 'HEAD', severity: 'HEALTHY' }], weapons: [{ alias: 'weapon_1', name: 'Longsword' }],
      items: [], abilities: [{ alias: 'ability_1', name: 'Warding Sigil' }], passive: 'Light Foot',
    },
    location: { region: null, scene: 'The Last Lantern', zone: { alias: 'zone_2', name: 'Lantern Hearth' } },
    scene: { zones: [{ alias: 'zone_2', name: 'Lantern Hearth' }], connections: [], creatures: [], objects: [], hazards: [], exits: [] },
    pendingAttack: null,
    lastTurn: null,
    ...overrides,
  };
}


/** A committed turn as the chronicle API returns it. */
export function turn(n: number, overrides: Partial<ChronicleTurn> = {}): ChronicleTurn {
  return {
    turnNumber: n,
    action: { text: `/hold ${n}`, kind: 'COMMAND' },
    enteredScene: null,
    narration: { text: `Narration of turn ${n}.`, source: 'AI' },
    narrationPending: false,
    enemy: null,
    ending: null,
    ...overrides,
  };
}

/** A chronicle page for turns [from..to], with the opening when it reaches turn 1. */
export function chroniclePage(from: number, to: number, overrides: Partial<ChronicleView> = {}): ChronicleView {
  const turns = [];
  for (let n = from; n <= to; n++) turns.push(turn(n));
  return {
    runId: RUN_ID,
    status: 'ACTIVE',
    latestTurnNumber: to,
    opening: from <= 1 ? { introduction: { text: 'You are Wren.', source: 'AI' }, objective: 'Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.', scene: 'The Last Lantern', zone: 'Lantern Hearth' } : null,
    turns,
    nextBefore: from <= 1 ? null : from,
    ...overrides,
  };
}

/** A fake server holding `total` turns that serves the view and chronicle pages like the real API. */
export function fakeServer(total: number, view: GameView = sampleView(), turnFor: (n: number) => ChronicleTurn = (n) => turn(n)) {
  const state = { total, view, turnFor };
  const api = fakeApi((call) => {
    if (call.method !== 'GET') return apiError(405, 'METHOD');
    const url = new URL(call.url, 'http://localhost');
    if (url.pathname.endsWith('/chronicle')) {
      const limit = Number(url.searchParams.get('limit') ?? '20');
      const before = Number(url.searchParams.get('before') ?? String(state.total + 1));
      const to = Math.min(before - 1, state.total);
      const from = Math.max(1, to - limit + 1);
      const page = chroniclePage(to < 1 ? 1 : from, to, { latestTurnNumber: state.total });
      page.turns = to < 1 ? [] : page.turns.map((t) => state.turnFor(t.turnNumber));
      page.nextBefore = from > 1 ? from : null;
      page.opening = from <= 1 ? chroniclePage(1, 0).opening : null;
      return json(200, page);
    }
    return json(200, state.view);
  });
  return { ...api, state };
}
