import { expect, test, type Page } from '@playwright/test';

// The live story screen against a faked API (no Spring Boot, no AI). A saved run is placed in the
// vault and opened from the shelf exactly as a player would.

const RUN_ID = '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f';
const TOKEN = 'A'.repeat(21) + '_' + 'b'.repeat(21);

type Turn = Record<string, unknown> & { turnNumber: number };

function view(overrides: Record<string, unknown> = {}) {
  return {
    runId: RUN_ID, status: 'ACTIVE', stateVersion: 3, awaiting: 'ACTION', finalizing: false,
    introduction: { text: 'You are Wren.', source: 'AI' },
    character: {
      name: 'Wren', hp: 17, maxHp: 24, stats: { MIGHT: 5, AGILITY: 8, PERCEPTION: 6, ARCANA: 4, RESOLVE: 6 }, fated: 1,
      fatedBand: 'TOUCHED', body: [{ part: 'HEAD', severity: 'HEALTHY' }], weapons: [{ alias: 'weapon_1', name: 'Longsword' }],
      items: [{ alias: 'item_1', name: 'Lockpicks' }], abilities: [{ alias: 'ability_1', name: 'Warding Sigil' }], passive: 'Light Foot',
    },
    location: { region: 'Hollow Chapel', scene: 'Bell Passage', zone: { alias: 'zone_1', name: 'Bell Landing' } },
    scene: { zones: [{ alias: 'zone_1', name: 'Bell Landing' }], connections: [], objects: [], hazards: [], exits: [],
      creatures: [{ alias: 'entity_1', name: 'Bone Warden', zone: 'zone_1', condition: 'ACTIVE' }] },
    pendingAttack: null, lastTurn: null,
    ...overrides,
  };
}

function storyTurn(n: number): Turn {
  return {
    turnNumber: n,
    action: { text: n % 3 === 0 ? `I press the Warden hard, step ${n}.` : `/hold`, kind: n % 3 === 0 ? 'FREE_TEXT' : 'COMMAND' },
    enteredScene: n === 2 ? { scene: 'Bell Passage', zone: 'Bell Landing' } : null,
    narration: { text: `The bell above turns on its chain (turn ${n}). Dust drifts down through the dark, and somewhere `
      + 'below the landing water drips on stone, slow and patient.', source: 'AI' },
    narrationPending: false,
    enemy: n % 5 === 0 ? { attacker: 'Bone Warden', action: 'HOLD', cueText: null, narration: null } : null,
    ending: null,
  };
}

interface Server {
  total: number;
  view: Record<string, unknown>;
  turnFor: (n: number) => Turn;
  chronicleCalls: string[];
}

async function serve(page: Page, server: Server) {
  await page.route('**/api/v1/runs/**', async (route) => {
    const request = route.request();
    if (request.method() !== 'GET') return route.fulfill({ status: 405, body: '' });
    const url = new URL(request.url());
    if (url.pathname.endsWith('/chronicle')) {
      server.chronicleCalls.push(url.search);
      const limit = Number(url.searchParams.get('limit') ?? '20');
      const before = Number(url.searchParams.get('before') ?? String(server.total + 1));
      const to = Math.min(before - 1, server.total);
      const from = Math.max(1, to - limit + 1);
      const turns = [];
      for (let n = from; n <= to; n++) turns.push(server.turnFor(n));
      return route.fulfill({ json: {
        runId: RUN_ID, status: server.view.status, latestTurnNumber: server.total, turns,
        opening: from <= 1 ? { introduction: { text: 'You are Wren, a Bound Soul.\n\nThe Last Lantern is the final refuge on the road.', source: 'AI' },
          scene: 'The Last Lantern', zone: 'Lantern Hearth' } : null,
        nextBefore: from > 1 ? from : null,
      } });
    }
    return route.fulfill({ json: server.view });
  });
}

async function openSavedRun(page: Page, server: Server) {
  await serve(page, server);
  await page.addInitScript(([runId, token]) => {
    if (localStorage.getItem('sg.vault.v1')) return;
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 1, entries: [{
      localId: 'local-1', runId, token, creationKey: '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
      createdAt: '2026-10-08T00:00:00.000Z', lastOpenedAt: null, summary: null,
    }] }));
  }, [RUN_ID, TOKEN]);
  await page.goto('/');
  await page.getByRole('button', { name: /Continue|Read/ }).click();
  await page.evaluate(() => document.fonts.ready);
}

const turnNumbers = (page: Page) => page.locator('article[data-turn]').evaluateAll((els) => els.map((e) => Number(e.getAttribute('data-turn'))));

test('restores a long chronicle page by page without moving the passage being read', async ({ page }, info) => {
  const server: Server = { total: 45, view: view(), turnFor: storyTurn, chronicleCalls: [] };
  await openSavedRun(page, server);

  // The latest passage is in view on arrival.
  await expect(page.getByText('(turn 45)')).toBeInViewport();
  expect(await turnNumbers(page)).toEqual(Array.from({ length: 20 }, (_, i) => 26 + i));
  await page.screenshot({ path: `test-results/screens/live-${info.project.name}-latest.png` });

  // Scrolling to the top loads the previous page; the first loaded turn stays where it was.
  const anchor = page.locator('article[data-turn="26"]');
  await page.locator('main').evaluate((m) => m.scrollTo(0, 0));
  const yBefore = (await anchor.boundingBox())!.y;
  await expect(page.locator('article[data-turn="6"]')).toBeAttached();
  await page.waitForTimeout(100);
  const yAfter = (await anchor.boundingBox())!.y;
  expect(Math.abs(yAfter - yBefore)).toBeLessThanOrEqual(2);

  // All the way back to the introduction: every turn exactly once, in order.
  for (let i = 0; i < 5 && (await page.getByLabel('Introduction').count()) === 0; i++) {
    await page.locator('main').evaluate((m) => m.scrollTo(0, 0));
    await page.waitForTimeout(150);
  }
  await expect(page.getByLabel('Introduction')).toBeAttached();
  expect(await turnNumbers(page)).toEqual(Array.from({ length: 45 }, (_, i) => i + 1));
  // Distinct pages, newest first (React's development StrictMode may repeat the very first read).
  expect([...new Set(server.chronicleCalls)]).toEqual(['?limit=20', '?limit=20&before=26', '?limit=20&before=6']);
  expect(server.chronicleCalls.filter((c) => c.includes('before'))).toHaveLength(2);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.screenshot({ path: `test-results/screens/live-${info.project.name}-opening.png` });
});

test('shows a pending defense immediately and keeps the composer on screen', async ({ page }, info) => {
  const server: Server = {
    total: 4,
    view: view({ awaiting: 'DEFENSE', pendingAttack: { alias: 'attack_1', attacker: 'Bone Warden',
      cueText: 'Incoming: an overhead strike coming down from above.', narration: { text: 'The Warden raises its hammer.', source: 'AI' } } }),
    turnFor: (n) => (n === 4 ? { ...storyTurn(4), enemy: { attacker: 'Bone Warden', action: 'ATTACK',
      cueText: 'Incoming: an overhead strike coming down from above.', narration: { text: 'The Warden raises its hammer.', source: 'AI' } } } : storyTurn(n)),
    chronicleCalls: [],
  };
  await openSavedRun(page, server);
  await expect(page.getByRole('alert')).toContainText('an overhead strike');
  await expect(page.getByPlaceholder('How do you defend?')).toBeInViewport();
  await expect(page.getByLabel('Incoming attack from the Bone Warden')).toBeInViewport();
  await page.screenshot({ path: `test-results/screens/live-${info.project.name}-defense.png` });
});

test('a finalizing turn fills in on a check, and the reader is not pulled away', async ({ page }) => {
  const server: Server = {
    total: 30,
    view: view({ finalizing: true }),
    turnFor: (n) => (n === 30 ? { ...storyTurn(30), narration: null, narrationPending: true } : storyTurn(n)),
    chronicleCalls: [],
  };
  await openSavedRun(page, server);
  await expect(page.getByText('The telling of this turn has not arrived yet.')).toBeVisible();

  // The reader scrolls up to an earlier passage; meanwhile the narration completes elsewhere.
  await page.locator('main').evaluate((m) => m.scrollTo(0, 200));
  const top = await page.locator('main').evaluate((m) => m.scrollTop);
  server.view = view();
  server.turnFor = storyTurn;
  await page.getByRole('button', { name: 'Check again' }).click();

  const pill = page.getByRole('button', { name: 'New passage ↓' });
  await expect(pill).toBeVisible();
  expect(await page.locator('main').evaluate((m) => m.scrollTop)).toBe(top);
  await pill.click();
  await expect(page.getByText('(turn 30)')).toBeInViewport();
  await expect(pill).toBeHidden();
});

test('a finished tale reads as a completed chronicle', async ({ page }, info) => {
  const server: Server = {
    total: 6,
    view: view({ status: 'DEAD', awaiting: 'NONE', character: { ...view().character as object, hp: 0 } }),
    turnFor: (n) => (n === 6 ? { ...storyTurn(6), ending: 'DEAD' } : storyTurn(n)),
    chronicleCalls: [],
  };
  await openSavedRun(page, server);
  await expect(page.getByText('Here your story ends')).toBeInViewport();
  await expect(page.getByRole('textbox')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Return to your tales' })).toBeVisible();
  await page.screenshot({ path: `test-results/screens/live-${info.project.name}-ended.png` });
});
