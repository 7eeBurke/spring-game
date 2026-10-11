import { expect, test } from '@playwright/test';

const RUN_ID = '3f2b8c1e-7d4a-4c6b-9e2f-1a2b3c4d5e6f';

const view = {
  runId: RUN_ID, status: 'ACTIVE', stateVersion: 0, awaiting: 'ACTION', finalizing: false,
  introduction: { text: 'The flame gutters, and you wake at the Last Lantern.', source: 'AI' },
  objective: 'Follow Chapel Road to the Hollow Chapel, and discover what guards its depths.',
  character: {
    name: 'Wren', hp: 24, maxHp: 24, stats: { MIGHT: 5, AGILITY: 8 }, fated: 1, fatedBand: 'TOUCHED',
    body: [{ part: 'HEAD', severity: 'HEALTHY' }], weapons: [{ alias: 'weapon_1', name: 'Longsword' }],
    items: [], abilities: [], passive: 'Light Foot',
  },
  location: { region: null, scene: 'The Last Lantern', zone: { alias: 'zone_2', name: 'Lantern Hearth' } },
  scene: { zones: [{ alias: 'zone_2', name: 'Lantern Hearth' }], connections: [], creatures: [], objects: [], hazards: [], exits: [] },
  pendingAttack: null, lastTurn: null,
};

test('creates a tale, survives a lost response and resumes after reload', async ({ page }, info) => {
  const creationCalls: { auth: string; key: string }[] = [];
  await page.route('**/api/v1/runs', async (route) => {
    const headers = route.request().headers();
    creationCalls.push({ auth: headers.authorization ?? '', key: headers['idempotency-key'] ?? '' });
    if (creationCalls.length === 1) return route.abort('connectionreset'); // the response is lost
    return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({ runId: RUN_ID, view }) });
  });
  await page.route(`**/api/v1/runs/${RUN_ID}/chronicle*`, (route) => route.fulfill({ json: {
    runId: RUN_ID, status: 'ACTIVE', latestTurnNumber: 0, turns: [], nextBefore: null,
    opening: { introduction: view.introduction, objective: view.objective, scene: 'The Last Lantern', zone: 'Lantern Hearth' },
  } }));
  await page.route(`**/api/v1/runs/${RUN_ID}`, (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(view) }));

  await page.goto('/');
  await page.getByRole('button', { name: 'Begin a new tale' }).click();
  await page.getByLabel('Invite code').fill('local-invite');
  await page.getByRole('button', { name: 'Begin' }).click();
  await expect(page.getByRole('alert')).toContainText('Could not reach the game');

  // Retry from the same form: the same token and creation key, never new ones.
  await page.getByRole('form', { name: 'Begin a new tale' }).getByRole('button', { name: 'Finish creating' }).click();
  await expect(page.getByRole('button', { name: /Character details: Wren/ })).toBeVisible();
  expect(creationCalls).toHaveLength(2);
  expect(creationCalls[1]).toEqual(creationCalls[0]);
  expect(creationCalls[0]!.auth).toMatch(/^Bearer [A-Za-z0-9_-]{43}$/);
  await page.screenshot({ path: `test-results/screens/story-${info.project.name}.png` });

  // Nothing secret in the address bar, before or after reloading.
  const token = creationCalls[0]!.auth.slice('Bearer '.length);
  expect(page.url()).not.toContain(token);
  await page.reload();
  await expect(page.getByText('Wren')).toBeVisible();
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByLabel('Introduction')).toContainText('The flame gutters');
  expect(page.url()).not.toContain(token);
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('local-invite');
});

test('the shelf fits the phone screen', async ({ page }, info) => {
  await page.goto('/');
  await page.evaluate(() => document.fonts.ready);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  const begin = await page.getByRole('button', { name: 'Begin a new tale' }).boundingBox();
  expect(begin!.height).toBeGreaterThanOrEqual(44);
  await page.screenshot({ path: `test-results/screens/shelf-${info.project.name}.png` });
});
