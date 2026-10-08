import { vi } from 'vitest';
import { setFetcher } from '../api/client';
import type { GameView } from '../api/types';

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
