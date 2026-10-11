import { expect, test, type Page, type Route } from '@playwright/test';

// Live play against a stateful fake of the Stage 14/15A API (no Spring Boot, no AI). Like the real
// server, it stores each answer under its idempotency key and replays it for the same key.

const RUN_ID = '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f';
const TOKEN = 'A'.repeat(21) + '_' + 'b'.repeat(21);

interface Plan {
  enemy?: { attacker: string; action: 'ATTACK' | 'HOLD'; cueText?: string };
  /** Apply the turn but drop the connection instead of answering. */
  drop?: boolean;
  /** Hold the answer for this many ms. */
  delayMs?: number;
}

class Server {
  turns: Record<string, unknown>[] = [];
  view: Record<string, unknown>;
  stored = new Map<string, { status: number; body: unknown }>();
  posts: { key: string; body: { input: string; stateVersion: number } }[] = [];
  plans: Plan[] = [];

  constructor(existing = 0) {
    this.view = {
      runId: RUN_ID, status: 'ACTIVE', stateVersion: existing, awaiting: 'ACTION', finalizing: false,
      introduction: { text: 'You are Wren, a Bound Soul.\n\nThe Last Lantern is the final refuge on the road.', source: 'AI' },
      objective: 'Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.',
      character: { name: 'Wren', hp: 24, maxHp: 24, stats: { MIGHT: 5, AGILITY: 8 }, fated: 1, fatedBand: 'TOUCHED',
        body: [], weapons: [{ alias: 'weapon_1', name: 'Longsword' }], items: [], abilities: [{ alias: 'ability_1', name: 'Warding Sigil' }],
        passive: 'Light Foot' },
      location: { region: 'Hollow Chapel', scene: 'Bell Passage', zone: { alias: 'zone_1', name: 'Bell Landing' } },
      scene: { zones: [{ alias: 'zone_1', name: 'Bell Landing' }], connections: [], objects: [], hazards: [], exits: [],
        creatures: [{ alias: 'entity_1', name: 'Bone Warden', zone: 'zone_1', condition: 'ACTIVE' }] },
      pendingAttack: null, lastTurn: null,
    };
    for (let n = 1; n <= existing; n++) this.turns.push(this.turnRecord(n, `/hold`, null));
  }

  private narration(n: number) {
    return { text: `The bell turns on its chain (turn ${n}).\n\nDust drifts down through the dark, slow and patient.`, source: 'AI' };
  }

  private turnRecord(n: number, input: string, enemy: Record<string, unknown> | null) {
    return { turnNumber: n, action: { text: input, kind: input.startsWith('/') ? 'COMMAND' : 'FREE_TEXT' }, enteredScene: null,
      narration: this.narration(n), narrationPending: false, enemy, ending: null };
  }

  apply(input: string, plan: Plan) {
    const n = this.turns.length + 1;
    const attack = plan.enemy?.action === 'ATTACK';
    const pendingAttack = attack ? { alias: 'attack_1', attacker: plan.enemy!.attacker,
      cueText: plan.enemy!.cueText ?? 'Incoming: an overhead strike coming down from above.',
      narration: { text: 'The Warden raises its hammer high.', source: 'AI' } } : null;
    this.view = { ...this.view, stateVersion: (this.view.stateVersion as number) + 1, awaiting: attack ? 'DEFENSE' : 'ACTION',
      pendingAttack, lastTurn: { turnNumber: n, narration: this.narration(n) } };
    const enemy = plan.enemy ? { attacker: plan.enemy.attacker, action: plan.enemy.action,
      cueText: pendingAttack?.cueText ?? null, narration: pendingAttack?.narration ?? null } : null;
    this.turns.push(this.turnRecord(n, input, enemy));
    return { turnNumber: n, overall: 'COMPLETE_SUCCESS', narration: this.narration(n),
      changes: { playerHpLost: 0, enemiesDefeated: [], movedTo: null, enteredScene: null },
      enemyTurn: plan.enemy ? { attacker: plan.enemy.attacker, action: plan.enemy.action } : null, view: this.view };
  }

  async handle(route: Route) {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() === 'GET' && url.pathname.endsWith('/chronicle')) {
      const limit = Number(url.searchParams.get('limit') ?? '20');
      const before = Number(url.searchParams.get('before') ?? String(this.turns.length + 1));
      const page = this.turns.filter((t) => (t.turnNumber as number) < before).slice(-limit);
      const from = (page[0]?.turnNumber as number | undefined) ?? 1;
      return route.fulfill({ json: { runId: RUN_ID, status: this.view.status, latestTurnNumber: this.turns.length, turns: page,
        opening: from <= 1 ? { introduction: this.view.introduction, objective: this.view.objective, scene: 'The Last Lantern', zone: 'Lantern Hearth' } : null,
        nextBefore: from > 1 ? from : null } });
    }
    if (request.method() === 'GET') return route.fulfill({ json: this.view });
    const key = request.headers()['idempotency-key']!;
    const body = request.postDataJSON() as { input: string; stateVersion: number };
    this.posts.push({ key, body });
    const stored = this.stored.get(key);
    if (stored) return route.fulfill({ status: stored.status, json: stored.body });
    const plan = this.plans.shift() ?? {};
    const response = this.apply(body.input, plan);
    this.stored.set(key, { status: 200, body: response });
    if (plan.delayMs) await new Promise((r) => setTimeout(r, plan.delayMs));
    if (plan.drop) return route.abort('connectionreset');
    return route.fulfill({ json: response });
  }
}

async function start(page: Page, server: Server) {
  await page.route('**/api/v1/runs/**', (route) => server.handle(route));
  await page.addInitScript(([runId, token]) => {
    if (localStorage.getItem('sg.vault.v1')) return;
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 1, entries: [{
      localId: 'local-1', runId, token, creationKey: '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
      createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null, summary: null,
    }] }));
  }, [RUN_ID, TOKEN]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.evaluate(() => document.fonts.ready);
}

async function act(page: Page, text: string, isMobile: boolean) {
  await page.getByLabel('Your action').fill(text);
  if (isMobile) await page.getByRole('button', { name: 'Send action' }).click();
  else await page.getByLabel('Your action').press('Enter');
}

test('a written action is resolved and its telling revealed', async ({ page, isMobile }, info) => {
  const server = new Server();
  await start(page, server);
  await act(page, 'I slash at the Bone Warden,\naiming for the wire that binds its ribs.', isMobile);

  await expect(page.locator('main').getByText('(turn 1)')).toBeVisible();
  expect(server.posts).toHaveLength(1);
  expect(server.posts[0]!.body).toEqual({ input: 'I slash at the Bone Warden,\naiming for the wire that binds its ribs.', stateVersion: 0 });
  await expect(page.getByRole('article', { name: 'Turn 1' }).locator('blockquote')).toHaveText(/aiming for the wire/);
  await expect(page.getByLabel('Your action')).toHaveValue('');
  await page.screenshot({ path: `test-results/screens/play-${info.project.name}-turn.png` });
});

test('a lost response is recovered with the same key and appears once', async ({ page, isMobile }) => {
  const server = new Server();
  server.plans.push({ drop: true });
  await start(page, server);
  await act(page, 'I wait in the dark.', isMobile);

  await expect(page.getByText(/could not confirm|faltered/)).toBeVisible();
  await expect(page.getByLabel('Your action, not yet confirmed')).toContainText('I wait in the dark.');
  await page.getByRole('button', { name: 'Check again' }).click();
  await expect(page.locator('main').getByText('(turn 1)')).toBeVisible();
  expect(server.posts.length).toBeGreaterThanOrEqual(2);
  expect(new Set(server.posts.map((p) => JSON.stringify(p))).size).toBe(1);
  await expect(page.locator('article[data-turn]')).toHaveCount(1);
  expect(server.turns).toHaveLength(1);
});

test('closing the page mid-turn and returning recovers the same action', async ({ page, isMobile }) => {
  const server = new Server();
  server.plans.push({ delayMs: 30_000 });
  await start(page, server);
  await act(page, 'I listen at the door.', isMobile);
  await expect(page.getByText('The storyteller is weighing your action…')).toBeVisible();
  await expect.poll(() => server.posts.length).toBe(1);

  await page.reload();
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.locator('main').getByText('(turn 1)')).toBeVisible();
  expect(server.posts.map((p) => p.key)).toEqual([server.posts[0]!.key, server.posts[0]!.key]);
  expect(server.turns).toHaveLength(1);
  await expect(page.locator('article[data-turn]')).toHaveCount(1);
});

test('an incoming attack is met with a discreet shortcut or with words', async ({ page, isMobile }, info) => {
  const server = new Server();
  server.plans.push({ enemy: { attacker: 'Bone Warden', action: 'ATTACK' } });
  await start(page, server);
  await act(page, 'I slash at the Warden.', isMobile);

  await expect(page.getByRole('alert')).toContainText('an overhead strike coming down from above.');
  await expect(page.getByLabel('Your action')).toHaveAttribute('placeholder', 'How do you defend?');
  await page.screenshot({ path: `test-results/screens/play-${info.project.name}-defense.png` });
  await page.getByRole('button', { name: /^Parry/ }).click();
  await expect(page.getByLabel('Your action')).toHaveValue('/defend parry');
  expect(server.posts).toHaveLength(1);

  if (isMobile) await page.getByRole('button', { name: 'Send action' }).click();
  else await page.getByLabel('Your action').press('Enter');
  await expect(page.locator('main').getByText('(turn 2)')).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await expect(page.getByLabel('Your action')).toHaveAttribute('placeholder', 'What do you do?');
});

test('a new passage does not pull a reader away from earlier pages', async ({ page, isMobile }) => {
  const server = new Server(30);
  server.plans.push({ delayMs: 600 });
  await start(page, server);
  await act(page, 'I wait.', isMobile);
  await page.locator('main').evaluate((m) => m.scrollTo(0, 150)); // the reader scrolls back while it resolves
  const top = await page.locator('main').evaluate((m) => m.scrollTop);

  await expect(page.getByRole('button', { name: 'New passage ↓' })).toBeVisible();
  expect(Math.abs((await page.locator('main').evaluate((m) => m.scrollTop)) - top)).toBeLessThanOrEqual(2);
  await page.getByRole('button', { name: 'New passage ↓' }).click();
  await expect(page.locator('main').getByText('(turn 31)')).toBeInViewport();
});
