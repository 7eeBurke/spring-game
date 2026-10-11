import { randomBytes, randomUUID } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * OPT-IN: real play through the UI against the real Spring Boot API and PostgreSQL.
 *
 * AI disabled (no API key, no cost), E2E_REAL_API=1:
 *   docker compose up -d
 *   $env:GAME_AI_ENABLED="false"; $env:GAME_INVITE_CODES="e2e-local"
 *   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--game.api.limits.turns-per-minute-per-run=200 --logging.file.name=target/e2e-server.log"
 *   (frontend/) $env:E2E_REAL_API="1"; $env:E2E_SERVER_LOG="..\target\e2e-server.log"; npx playwright test real- --project=phone-390 --workers=1
 *   (one worker: the AI-log checks count lines for the whole server, so the specs must not overlap)
 *
 * Real OpenAI (spends a few small calls; never part of the normal suite), E2E_REAL_AI=1, with the
 * server started with GAME_AI_ENABLED=true, GAME_AI_MODEL=gpt-4.1-mini and OPENAI_API_KEY set.
 */
const REAL_API = process.env.E2E_REAL_API === '1';
const REAL_AI = process.env.E2E_REAL_AI === '1';
const INVITE = process.env.E2E_INVITE ?? 'e2e-local';

type Thing = { alias: string; name: string; zone: string; container?: string | null; reach?: string };
type View = {
  stateVersion: number; status: string; awaiting: string;
  location: { scene: string; zone: { alias: string; name: string } };
  scene: { zones: { alias: string; name: string }[]; connections: { zoneA: string; zoneB: string }[];
    exits: { alias: string; zone: string; leadsTo: string }[]; creatures: { alias: string; name: string; zone: string; condition: string }[];
    objects: Thing[] };
  character: { weapons: { alias: string; name: string }[]; items: { alias: string; name: string }[] };
};

async function createRun(request: APIRequestContext) {
  const token = randomBytes(32).toString('base64url');
  const created = await request.post('/api/v1/runs', {
    headers: { 'X-Invite-Code': INVITE, 'Idempotency-Key': randomUUID(), Authorization: `Bearer ${token}` },
  });
  expect(created.status(), await created.text()).toBe(201);
  return { runId: (await created.json()).runId as string, token };
}

async function getView(request: APIRequestContext, runId: string, token: string): Promise<View> {
  const r = await request.get(`/api/v1/runs/${runId}`, { headers: { Authorization: `Bearer ${token}` } });
  expect(r.status()).toBe(200);
  return r.json();
}

async function openInBrowser(page: Page, runId: string, token: string) {
  await page.addInitScript(([id, secret]) => {
    if (localStorage.getItem('sg.vault.v1')) return;
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 1, entries: [{
      localId: 'real-1', runId: id, token: secret, creationKey: '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
      createdAt: new Date().toISOString(), lastOpenedAt: null, summary: null,
    }] }));
  }, [runId, token]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByLabel('Introduction')).toBeAttached();
}

/** Types an action in the UI and sends it with the button; waits for its confirmed turn. */
async function play(page: Page, text: string, expectedTurn: number) {
  const field = page.getByLabel('Your action');
  await expect(field).toBeEnabled();
  await field.fill(text);
  await page.getByRole('button', { name: 'Send action' }).click();
  await expect(page.locator(`article[data-turn="${expectedTurn}"]`)).toBeAttached({ timeout: 90_000 });
  await expect(page.getByRole('button', { name: 'Send action' })).toBeVisible();
}

function nextStep(view: View, targetZone: string): string | null {
  const from = view.location.zone.alias;
  if (from === targetZone) return null;
  const previous = new Map<string, string | null>([[from, null]]);
  const queue = [from];
  while (queue.length) {
    const zone = queue.shift()!;
    for (const c of view.scene.connections) {
      const next = c.zoneA === zone ? c.zoneB : c.zoneB === zone ? c.zoneA : null;
      if (next && !previous.has(next)) {
        previous.set(next, zone);
        queue.push(next);
      }
    }
  }
  let step = targetZone;
  while (previous.get(step) !== from) step = previous.get(step)!;
  return step;
}

/**
 * Walks into the Hollow Chapel with slash commands until a living enemy shares the player's place
 * (only an enemy in the player's own zone can attack). The view shows only what the player has seen,
 * and aliases are renumbered per view, so places are tracked by name: each scene's places are visited
 * first, then an unexplored way out is taken; a scene with nothing left is not returned to if
 * another way exists.
 */
async function walkToAnEnemy(page: Page, request: APIRequestContext, runId: string, token: string, turn: { n: number }) {
  const visited = new Map<string, Set<string>>();
  const finished = new Set<string>();
  const sceneOf = (leadsTo: string) => leadsTo.replace(/^the way to (the )?/i, '');
  for (let i = 0; i < 80; i++) {
    const view = await getView(request, runId, token);
    const enemy = view.scene.creatures.find((c) => c.condition === 'ACTIVE');
    if (enemy) {
      const toward = nextStep(view, enemy.zone);
      if (!toward) return view;
      await play(page, `/move ${toward}`, ++turn.n);
      continue;
    }
    const scene = view.location.scene;
    const been = visited.get(scene) ?? new Set<string>();
    visited.set(scene, been);
    been.add(view.location.zone.name);
    const unvisited = view.scene.zones.find((z) => !been.has(z.name) && nextStep(view, z.alias));
    if (unvisited) {
      await play(page, `/move ${nextStep(view, unvisited.alias)}`, ++turn.n);
      continue;
    }
    let way = view.scene.exits.find((x) => x.leadsTo === 'an unexplored way');
    if (!way) {
      finished.add(scene);
      way = view.scene.exits.find((x) => !finished.has(sceneOf(x.leadsTo))) ?? view.scene.exits[0]!;
    }
    const step = nextStep(view, way.zone);
    await play(page, step ? `/move ${step}` : `/move ${way.alias}`, ++turn.n);
  }
  throw new Error('No enemy found within 80 moves');
}

/** Walks from the hearth to the road's end and through the chapel doors; returns the first chapel view. */
async function enterTheChapel(page: Page, request: APIRequestContext, runId: string, token: string, turn: { n: number }) {
  const hub = (await getView(request, runId, token)).location.scene;
  for (let i = 0; i < 6; i++) {
    const view = await getView(request, runId, token);
    if (view.location.scene !== hub) return view;
    const exit = view.scene.exits[0];
    if (!exit) throw new Error('No way out of the hub is known');
    const step = nextStep(view, exit.zone);
    await play(page, step ? `/move ${step}` : `/move ${exit.alias}`, ++turn.n);
  }
  throw new Error('Did not reach the Hollow Chapel');
}

test.describe('real backend, AI disabled', () => {
  test.skip(!REAL_API, 'opt-in: E2E_REAL_API=1 with a local server (AI disabled)');

  test('plays through the UI: commands, a refused free-text action, attack, defense and a counter', async ({ page, request }, info) => {
    test.setTimeout(300_000);
    test.skip(info.project.name !== 'phone-390', 'one viewport is enough against the real server');
    const { runId, token } = await createRun(request);
    await openInBrowser(page, runId, token);
    const turn = { n: 0 };
    const sent: string[] = [];
    const playLogged = async (text: string) => { sent.push(text); await play(page, text, ++turn.n); };

    await playLogged('/hold');
    // Without AI, free text is refused and never becomes a turn; the words stay in the field.
    await page.getByLabel('Your action').fill('I look carefully around the hearth.');
    await page.getByRole('button', { name: 'Send action' }).click();
    await expect(page.getByRole('alert')).toContainText('Free-text actions are unavailable');
    await expect(page.getByLabel('Your action')).toHaveValue('I look carefully around the hearth.');
    await expect(page.locator('article[data-turn]')).toHaveCount(1);
    await page.getByLabel('Your action').fill('');

    const before = turn.n;
    let view = await walkToAnEnemy(page, request, runId, token, turn);
    for (let n = before + 1; n <= turn.n; n++) sent.push('(move)');

    let defended = false;
    let countered = false;
    for (let i = 0; i < 25 && !(defended && countered); i++) {
      view = await getView(request, runId, token);
      if (view.status !== 'ACTIVE') break;
      const target = view.scene.creatures.find((c) => c.condition === 'ACTIVE');
      if (!target) break;
      const attack = `/attack ${target.alias} slash with ${view.character.weapons[0]!.alias}`;
      if (view.awaiting === 'DEFENSE') {
        await expect(page.getByRole('alert')).toContainText(/Defend/);
        if (!defended) {
          await page.getByRole('button', { name: /^Parry/ }).click();
          await expect(page.getByLabel('Your action')).toHaveValue('/defend parry');
          sent.push('/defend parry');
          await page.getByRole('button', { name: 'Send action' }).click();
          await expect(page.locator(`article[data-turn="${++turn.n}"]`)).toBeAttached({ timeout: 90_000 });
          const after = await getView(request, runId, token);
          expect(after.awaiting, 'a defense-only turn never provokes a new attack').not.toBe('DEFENSE');
          defended = true;
        } else {
          await playLogged(`/defend parry ; ${attack}`);
          countered = true;
        }
      } else {
        await playLogged(attack);
      }
    }
    expect(defended, 'an enemy attacked and was defended').toBe(true);

    // Reload: the chronicle restored from PostgreSQL shows every turn exactly once with the exact words.
    const aiBefore = aiLines();
    await page.reload();
    await page.getByRole('button', { name: 'Continue' }).click();
    for (let i = 0; i < 6 && (await page.getByLabel('Introduction').count()) === 0; i++) {
      await page.locator('main').evaluate((m) => m.scrollTo(0, 0));
      await page.waitForTimeout(400);
    }
    const restored = await page.locator('article[data-turn]').evaluateAll((articles) => articles.map((a) => ({
      n: Number(a.getAttribute('data-turn')), words: a.querySelector('blockquote')?.textContent?.replace(/^(You|Command)/, '') ?? null,
    })));
    expect(restored.map((t) => t.n)).toEqual(Array.from({ length: turn.n }, (_, i) => i + 1));
    sent.forEach((words, i) => { if (words !== '(move)') expect(restored[i]!.words).toBe(words); });
    expect(aiLines(), 'reading the restored chronicle made no AI call').toBe(aiBefore);
    console.log(`played ${turn.n} turns; defended=${defended} countered=${countered}; ai log lines=${aiBefore}`);
    await page.screenshot({ path: 'test-results/screens/real-play.png' });
  });
});

test.describe('real backend, AI disabled: exploring the Hollow Chapel', () => {
  test.skip(!REAL_API, 'opt-in: E2E_REAL_API=1 with a local server (AI disabled)');

  test('finds the first crate, inspects it, opens it once in reach and takes what it holds', async ({ page, request }, info) => {
    test.setTimeout(300_000);
    test.skip(info.project.name !== 'phone-390', 'one viewport is enough against the real server');
    const { runId, token } = await createRun(request);
    await openInBrowser(page, runId, token);
    const turn = { n: 0 };

    // An enemy elsewhere in the scene may attack after a meaningful step; a pending defense comes first.
    const act = async (command: string) => {
      if ((await getView(request, runId, token)).awaiting === 'DEFENSE') await play(page, '/defend parry', ++turn.n);
      await play(page, command, ++turn.n);
    };

    let view = await enterTheChapel(page, request, runId, token, turn);
    const arrivedIn = view.location.zone.alias;
    // Only what has been seen is known: on arrival, every known place is here or one passage away.
    for (const zone of view.scene.zones) {
      expect(zone.alias === arrivedIn || nextStep(view, zone.alias) === zone.alias, `${zone.name} is in sight`).toBe(true);
    }
    const crates = view.scene.objects.filter((o) => o.container === 'closed' && o.reach === 'one step away');
    expect(crates.length, 'a closed crate in sight, one step from the doors').toBeGreaterThan(0);
    expect(view.scene.objects.every((o) => !o.container || o.container === 'closed'), 'nothing is told of a closed crate').toBe(true);

    // Inspecting from here moves nothing. Opening it from here cannot begin: refused, with where it is, and no turn spent.
    const crate = crates[0]!;
    await act(`/inspect ${crate.alias}`);
    const field = page.getByLabel('Your action');
    await field.fill(`/open ${crate.alias}`);
    await page.getByRole('button', { name: 'Send action' }).click();
    await expect(page.getByRole('alert')).toContainText('out of reach from here');
    await expect(page.getByRole('alert')).toContainText('No turn spent');
    await expect(page.locator('article[data-turn]')).toHaveCount(turn.n);
    await field.fill('');
    view = await getView(request, runId, token);
    expect(view.location.zone.alias).toBe(arrivedIn);
    expect(view.scene.objects.find((o) => o.alias === crate.alias)?.container).toBe('closed');

    // One step to it, then open it: one of the crates in sight holds the first find.
    const itemsBefore = view.character.items.length;
    let taken: string | null = null;
    // Aliases are numbered per view (they shift as more of the scene becomes known), so places are compared
    // by name and the crate is found again in each new view.
    const zoneName = (v: View, alias: string) => v.scene.zones.find((z) => z.alias === alias)?.name;
    const places = [...new Set(crates.map((c) => zoneName(view, c.zone)))];
    const crateHere = () => view.scene.objects.find((o) => !!o.container && o.reach === 'here');
    for (const place of places) {
      const target = view.scene.objects.find((o) => o.container === 'closed' && zoneName(view, o.zone) === place);
      if (!target) continue;
      if (view.location.zone.name !== place) {
        await act(`/move ${target.alias}`);
        view = await getView(request, runId, token);
        expect(view.location.zone.name).toBe(place);
        await expect(page.locator(`article[data-turn="${turn.n}"]`).getByLabel(`Now at ${view.location.zone.name}`)).toBeAttached();
      }
      expect(crateHere(), 'the crate is within reach').toBeTruthy();
      await act(`/open ${crateHere()!.alias}`);
      view = await getView(request, runId, token);
      const state = crateHere()?.container ?? '';
      expect(state).toMatch(/^open/);
      if (!state.startsWith('open, holding ')) continue;
      taken = state.slice('open, holding '.length).replace(/^an? /, '');
      await act(`/take ${crateHere()!.alias}`);
      view = await getView(request, runId, token);
      expect(crateHere()?.container).toBe('open and empty');
      expect(view.character.items.length).toBe(itemsBefore + 1);
      expect(view.character.items.map((i) => i.name)).toContain(taken);
      // Taking again finds nothing: the item is never duplicated.
      await act(`/take ${crateHere()!.alias}`);
      expect((await getView(request, runId, token)).character.items.length).toBe(itemsBefore + 1);
      break;
    }
    expect(taken, 'the first find was taken').not.toBeNull();

    // A reload restores the same world and the same belongings.
    await page.reload();
    await page.getByRole('button', { name: 'Continue' }).click();
    await expect(page.getByLabel('Your action')).toBeVisible();
    const again = await getView(request, runId, token);
    expect(again.scene.objects).toEqual(view.scene.objects);
    expect(again.character.items).toEqual(view.character.items);
    console.log(`explored in ${turn.n} turns; took ${taken}`);
    await page.screenshot({ path: 'test-results/screens/real-explore.png' });
  });
});

test.describe('real OpenAI (spends a few small calls)', () => {
  test.skip(!REAL_AI, 'opt-in: E2E_REAL_AI=1 with a server using real AI');

  test('a natural-language attack and defense are interpreted and told by the model', async ({ page, request }, info) => {
    test.setTimeout(420_000);
    test.skip(info.project.name !== 'phone-390', 'one viewport is enough against the real model');
    const { runId, token } = await createRun(request);
    await openInBrowser(page, runId, token);
    await expect(page.getByLabel('Introduction')).not.toContainText('Told plainly');
    const turn = { n: 0 };
    await walkToAnEnemy(page, request, runId, token, turn);

    for (let i = 0; i < 4; i++) {
      const view = await getView(request, runId, token);
      if (view.awaiting === 'DEFENSE' || view.status !== 'ACTIVE') break;
      const target = view.scene.creatures.find((c) => c.condition === 'ACTIVE')!;
      await play(page, `I attack the ${target.name} with my ${view.character.weapons[0]!.name}, aiming for its chest.`, ++turn.n);
      await expect(page.locator(`article[data-turn="${turn.n}"]`)).not.toContainText('Told plainly');
    }
    const view = await getView(request, runId, token);
    if (view.awaiting === 'DEFENSE') {
      await play(page, `I raise my ${view.character.weapons[0]!.name} and parry the blow.`, ++turn.n);
      await expect(page.locator(`article[data-turn="${turn.n}"]`)).not.toContainText('Told plainly');
    }
    await page.screenshot({ path: 'test-results/screens/real-ai-play.png' });
  });
});

function aiLines(): number | null {
  const log = process.env.E2E_SERVER_LOG;
  if (!log || !existsSync(log)) return null;
  return readFileSync(log, 'utf8').split(/\r?\n/).filter((l) => l.includes(' ai role=')).length;
}
