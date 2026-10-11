import { randomBytes, randomUUID } from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import { expect, test, type APIRequestContext } from '@playwright/test';

/*
 * OPT-IN: a real saved run restored from PostgreSQL through the real API, with AI disabled.
 * Runs only with E2E_REAL_API=1, against Spring Boot on :8080 (reached through the Vite proxy):
 *
 *   docker compose up -d
 *   $env:GAME_AI_ENABLED="false"; $env:GAME_INVITE_CODES="e2e-local"
 *   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--game.api.limits.turns-per-minute-per-run=200 --logging.file.name=target/e2e-server.log"
 *   (in frontend/) $env:E2E_REAL_API="1"; $env:E2E_SERVER_LOG="..\target\e2e-server.log"; npx playwright test real- --project=phone-390 --workers=1
 *
 * The turn-limit override is test-only (a command-line argument); production defaults are
 * unchanged. The spec also paces itself: a 429 is retried later with the SAME idempotency key.
 */
test.skip(process.env.E2E_REAL_API !== '1', 'opt-in: set E2E_REAL_API=1 with a local server running (AI disabled)');

const INVITE = process.env.E2E_INVITE ?? 'e2e-local';
const TURNS = 25;

function inputFor(n: number): string {
  if (n % 5 === 0) return `/say "Turn ${n}: is anyone there? <hello> & ünïcode — still here."`;
  return n % 2 === 0 ? '/listen' : '/hold';
}

async function playTurn(request: APIRequestContext, runId: string, token: string, input: string, stateVersion: number) {
  const key = randomUUID();
  for (let attempt = 0; attempt < 10; attempt++) {
    const response = await request.post(`/api/v1/runs/${runId}/turns`, {
      headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': key },
      data: { input, stateVersion },
    });
    if (response.status() === 429) {
      await new Promise((r) => setTimeout(r, 6000)); // paced retry, same key: never a second turn
      continue;
    }
    expect(response.status(), await response.text()).toBe(200);
    return response.json();
  }
  throw new Error('rate limited for too long');
}

function aiCallLines(): number | null {
  const log = process.env.E2E_SERVER_LOG;
  if (!log || !existsSync(log)) return null;
  return readFileSync(log, 'utf8').split(/\r?\n/).filter((l) => l.includes(' ai role=')).length;
}

test('a real run is restored from PostgreSQL, complete and exact, without any AI call', async ({ page, request }, info) => {
  test.setTimeout(240_000);
  test.skip(info.project.name !== 'phone-390', 'one viewport is enough for the data check');

  const token = randomBytes(32).toString('base64url');
  const created = await request.post('/api/v1/runs', {
    headers: { 'X-Invite-Code': INVITE, 'Idempotency-Key': randomUUID(), Authorization: `Bearer ${token}` },
  });
  expect(created.status(), await created.text()).toBe(201);
  const { runId, view } = await created.json();
  expect(view.introduction.source).toBe('FALLBACK'); // AI is disabled: no model was called

  let version: number = view.stateVersion;
  const sent: string[] = [];
  for (let n = 1; n <= TURNS; n++) {
    const input = inputFor(n);
    const reply = await playTurn(request, runId, token, input, version);
    expect(reply.turnNumber).toBe(n);
    version = reply.view.stateVersion;
    sent.push(input);
  }

  const aiBefore = aiCallLines();
  await page.addInitScript(([id, secret]) => {
    if (localStorage.getItem('sg.vault.v1')) return;
    localStorage.setItem('sg.vault.v1', JSON.stringify({ version: 1, entries: [{
      localId: 'real-1', runId: id, token: secret, creationKey: '9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d',
      createdAt: new Date().toISOString(), lastOpenedAt: null, summary: null,
    }] }));
  }, [runId, token]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Continue' }).click();
  await expect(page.getByRole('article', { name: `Turn ${TURNS}` })).toBeAttached();

  for (let i = 0; i < 6 && (await page.getByLabel('Introduction').count()) === 0; i++) {
    await page.locator('main').evaluate((m) => m.scrollTo(0, 0));
    await page.waitForTimeout(400);
  }
  await expect(page.getByLabel('Introduction')).toBeAttached();

  const restored = await page.locator('article[data-turn]').evaluateAll((articles) => articles.map((a) => ({
    n: Number(a.getAttribute('data-turn')),
    words: a.querySelector('blockquote')?.textContent?.replace(/^(You|Command)/, '') ?? null,
  })));
  expect(restored.map((t) => t.n)).toEqual(Array.from({ length: TURNS }, (_, i) => i + 1));
  expect(restored.map((t) => t.words)).toEqual(sent);
  await expect(page.getByText('Told plainly', { exact: false }).first()).toBeAttached();

  const aiAfter = aiCallLines();
  if (aiBefore !== null) expect(aiAfter, 'reading the chronicle made no AI call').toBe(aiBefore);
  console.log(`restored ${restored.length} turns; ai call log lines before/after browser phase: ${aiBefore}/${aiAfter}`);
  await page.screenshot({ path: 'test-results/screens/real-backend-opening.png' });
});
