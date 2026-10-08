import { defineConfig, devices } from '@playwright/test';

// Browser checks against the Vite dev server. The API is always faked with page.route, so these
// tests need neither Spring Boot nor a model. Chromium emulates the phone viewports; real-device
// checks on iOS Safari remain manual.
export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results',
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    // A genuine 390 x 844 CSS-pixel phone (iPhone 12-15 size), touch and mobile emulation on.
    { name: 'phone-390', use: { ...devices['iPhone 13'], browserName: 'chromium', viewport: { width: 390, height: 844 } } },
    { name: 'phone-android', use: { ...devices['Pixel 7'] } },
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
