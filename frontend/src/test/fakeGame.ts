import { vi } from 'vitest';
import { setFetcher } from '../api/client';
import type { ChronicleTurn, GameView, NarrationView, TurnResponse } from '../api/types';
import { chroniclePage, RUN_ID, sampleView, turn as baseTurn, type RecordedCall } from './fakeApi';

/**
 * A stateful stand-in for the Stage 14/15A API, faithful to its idempotency contract: answers are
 * stored per idempotency key and replayed exactly; a request that was lost after the server applied
 * it is answered from storage on retry. Scripts (`next`) override the next turn request.
 */
export interface Answer {
  status: number;
  json?: unknown;
  headers?: Record<string, string>;
  /** The request was processed but the response never reached the client. */
  lost?: boolean;
  /** Stored for replay with the same key (Stage 14 stores 200, STALE_VIEW and 422 rejections). */
  store?: boolean;
}

export interface TurnPlan {
  narration?: NarrationView;
  enemy?: { attacker: string; action: 'ATTACK' | 'HOLD'; cueText?: string; narration?: NarrationView | null };
  /** The answer is not a completed turn. */
  answer?: Answer;
  /** Apply the turn on the server, but lose the response. */
  lost?: boolean;
  hpLost?: number;
  ending?: 'DEAD' | 'VICTORIOUS';
}

export class FakeGame {
  view: GameView;
  turns: ChronicleTurn[] = [];
  calls: RecordedCall[] = [];
  next: TurnPlan[] = [];
  private stored = new Map<string, Answer>();
  /** Delay before answering turn POSTs (ms), to observe in-flight states. */
  turnDelay = 0;
  /** Out-of-date views served to the next GETs of the view (a delayed or stale read). */
  staleViews: GameView[] = [];

  constructor(view: GameView = sampleView(), existing = 0) {
    this.view = { ...view, stateVersion: existing };
    for (let n = 1; n <= existing; n++) this.turns.push(baseTurn(n));
  }

  get posts(): RecordedCall[] {
    return this.calls.filter((c) => c.method === 'POST');
  }

  install() {
    const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const call: RecordedCall = { url: String(input), method: init?.method ?? 'GET', headers: { ...(init?.headers as Record<string, string>) },
        body: init?.body as string | undefined };
      this.calls.push(call);
      const answer = await this.answer(call);
      if (answer.lost) throw new TypeError('Failed to fetch');
      return new Response(answer.json === undefined ? '' : JSON.stringify(answer.json),
        { status: answer.status, headers: { 'Content-Type': 'application/json', ...answer.headers } });
    });
    setFetcher(fn as unknown as typeof fetch);
    return this;
  }

  private async answer(call: RecordedCall): Promise<Answer> {
    const url = new URL(call.url, 'http://localhost');
    if (call.method === 'GET' && url.pathname.endsWith('/chronicle')) {
      const limit = Number(url.searchParams.get('limit') ?? '20');
      const before = Number(url.searchParams.get('before') ?? String(this.turns.length + 1));
      const upto = this.turns.filter((t) => t.turnNumber < before);
      const page = upto.slice(-limit);
      const from = page[0]?.turnNumber ?? 1;
      const base = chroniclePage(1, 0);
      return { status: 200, json: { ...base, status: this.view.status, latestTurnNumber: this.turns.length, turns: page,
        opening: from <= 1 ? base.opening : null, nextBefore: from > 1 ? from : null } };
    }
    if (call.method === 'GET') return { status: 200, json: this.staleViews.shift() ?? this.view };
    if (this.turnDelay) await new Promise((r) => setTimeout(r, this.turnDelay));
    const key = call.headers['Idempotency-Key']!;
    const replay = this.stored.get(key);
    if (replay) return { ...replay, lost: false };
    const body = JSON.parse(call.body!) as { input: string; stateVersion: number };
    const plan = this.next.shift() ?? {};
    if (plan.answer) {
      if (plan.answer.store) this.stored.set(key, plan.answer);
      return plan.answer;
    }
    if (body.stateVersion !== this.view.stateVersion) {
      const stale = { status: 409, json: { error: { code: 'STALE_VIEW', message: 'stale' } }, store: true };
      this.stored.set(key, stale);
      return stale;
    }
    const answer: Answer = { status: 200, json: this.apply(body.input, plan), store: true };
    this.stored.set(key, answer);
    return plan.lost ? { ...answer, lost: true } : answer;
  }

  /** Applies a completed turn on the "server" and returns its TurnResponse. */
  private apply(input: string, plan: TurnPlan): TurnResponse {
    const n = this.turns.length + 1;
    const narration = plan.narration ?? { text: `Narration of turn ${n}.\n\nIt goes on.`, source: 'AI' as const };
    const attacking = plan.enemy?.action === 'ATTACK';
    const hp = Math.max(0, this.view.character.hp - (plan.hpLost ?? 0));
    const status = plan.ending ?? (hp === 0 ? 'DEAD' : 'ACTIVE');
    this.view = {
      ...this.view,
      stateVersion: this.view.stateVersion + 1,
      status,
      awaiting: status !== 'ACTIVE' ? 'NONE' : attacking ? 'DEFENSE' : 'ACTION',
      character: { ...this.view.character, hp },
      pendingAttack: attacking && status === 'ACTIVE'
        ? { alias: 'attack_1', attacker: plan.enemy!.attacker, cueText: plan.enemy!.cueText ?? 'Incoming: a thrust aimed at your chest.',
          narration: plan.enemy!.narration ?? null }
        : null,
      lastTurn: { turnNumber: n, narration },
      finalizing: false,
    };
    const enemy = plan.enemy
      ? { attacker: plan.enemy.attacker, action: plan.enemy.action, cueText: attacking ? this.view.pendingAttack?.cueText ?? null : null,
        narration: attacking ? this.view.pendingAttack?.narration ?? null : null }
      : null;
    this.turns.push({ turnNumber: n, action: { text: input, kind: input.trim().startsWith('/') ? 'COMMAND' : 'FREE_TEXT' },
      enteredScene: null, narration, narrationPending: false, enemy, ending: status === 'ACTIVE' ? null : status });
    return {
      turnNumber: n, overall: 'COMPLETE_SUCCESS', narration,
      changes: { playerHpLost: plan.hpLost ?? 0, enemiesDefeated: [], movedTo: null, enteredScene: null },
      enemyTurn: plan.enemy ? { attacker: plan.enemy.attacker, action: plan.enemy.action } : null,
      view: this.view,
    };
  }
}

export function error(status: number, code: string, extra: Record<string, unknown> = {}, headers?: Record<string, string>): Answer {
  return { status, json: { error: { code, message: `${code} message`, ...extra } }, headers };
}

export { RUN_ID };
