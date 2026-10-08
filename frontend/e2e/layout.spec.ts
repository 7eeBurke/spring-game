import { expect, test, type Page } from '@playwright/test';

async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
}

async function settle(page: Page) {
  await page.evaluate(() => document.fonts.ready);
}

test.describe('design preview', () => {
  test('lays out the story screen within the viewport', async ({ page }, info) => {
    await page.goto('/preview.html');
    await settle(page);

    await expect(page.getByText('Design preview · sample text')).toBeVisible();
    const viewport = page.viewportSize()!;
    const header = await page.locator('header').first().boundingBox();
    const composer = await page.getByLabel('Your action').boundingBox();
    expect(header!.y).toBeGreaterThanOrEqual(0);
    expect(composer!.y + composer!.height).toBeLessThanOrEqual(viewport.height);
    await expect(page.getByRole('alert')).toContainText('an overhead strike');
    await expectNoHorizontalScroll(page);

    // The display and body fonts are self-hosted and actually load.
    const fonts = await page.evaluate(() => [
      document.fonts.check('600 20px "Cormorant Garamond"'),
      document.fonts.check('18px "Literata Variable"'),
    ]);
    expect(fonts).toEqual([true, true]);

    // Narration uses comfortable reading sizes on every width.
    const size = await page.locator('section[aria-label="Narration"] p').first()
      .evaluate((p) => parseFloat(getComputedStyle(p).fontSize));
    expect(size).toBeGreaterThanOrEqual(17);

    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-top.png` });
    await page.locator('main').evaluate((m) => m.scrollTo(0, m.scrollHeight));
    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-bottom.png` });
  });

  test('opens the character and scene sheets', async ({ page }, info) => {
    await page.goto('/preview.html');
    await settle(page);
    await page.getByRole('button', { name: /Character details/ }).click();
    await expect(page.getByRole('dialog', { name: 'Character' })).toBeVisible();
    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-character.png` });
    await page.getByRole('dialog', { name: 'Character' }).locator('div').nth(1)
      .evaluate((body) => body.scrollTo(0, body.scrollHeight));
    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-character-lower.png` });
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: /Scene details/ }).click();
    await expect(page.getByRole('dialog', { name: 'Bell Passage' })).toContainText('Bone Warden');
    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-scene.png` });
  });

  test('scrolls the story while the header and composer stay fixed', async ({ page }) => {
    await page.goto('/preview.html');
    await settle(page);
    const header = page.locator('header').first();
    const composer = page.getByLabel('Your action');
    const before = { header: await header.boundingBox(), composer: await composer.boundingBox() };

    const scrolled = await page.locator('main').evaluate((m) => {
      m.scrollTo(0, m.scrollHeight / 2);
      return m.scrollTop;
    });
    expect(scrolled).toBeGreaterThan(0);
    expect(await header.boundingBox()).toEqual(before.header);
    expect(await composer.boundingBox()).toEqual(before.composer);

    // 16px or more in the field, so iOS never zooms the page when typing.
    const inputSize = await composer.evaluate((t) => parseFloat(getComputedStyle(t).fontSize));
    expect(inputSize).toBeGreaterThanOrEqual(16);
    // The defense guidance is visible above the field, not under it.
    const guidance = await page.getByRole('alert').boundingBox();
    expect(guidance!.y + guidance!.height).toBeLessThanOrEqual(before.composer!.y);
  });

  test('works from the keyboard', async ({ page }) => {
    await page.goto('/preview.html');
    await settle(page);
    const character = page.getByRole('button', { name: /Character details/ });
    await character.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog', { name: 'Character' })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: 'Character' })).toBeHidden();
    await expect(character).toBeFocused();

    await page.getByLabel('Your action').focus();
    await page.keyboard.type('I raise my sword');
    await expect(page.getByLabel('Your action')).toHaveValue('I raise my sword');
  });

  test('shows the ending state', async ({ page }, info) => {
    await page.goto('/preview.html');
    await settle(page);
    await page.getByRole('button', { name: 'Fallen' }).click();
    await page.locator('main').evaluate((m) => m.scrollTo(0, m.scrollHeight));
    await expect(page.getByText('Here your story ends')).toBeVisible();
    await page.screenshot({ path: `test-results/screens/preview-${info.project.name}-ending.png` });
  });
});
