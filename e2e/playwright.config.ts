import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests of GITS against the running docker compose stack (scripts/up.*). One worker: the scenarios share the
 * sandbox runner, and the candidate's runs are checked one after another.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  // the scenarios depend on each other (serial): a retry would repeat the whole chain
  retries: 0,
  // a full assessment runs code in the sandbox four times: minutes, not seconds
  timeout: 20 * 60_000,
  expect: { timeout: 30_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.GITS_URL ?? 'http://localhost:8080',
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 800 },
  },
});
