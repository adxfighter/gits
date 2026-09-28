import { defineConfig, devices } from '@playwright/test';

/**
 * Records the demo sessions of seed/demo-sessions (npm run record-demo, against a running stack): a scripted
 * candidate passes the assessment three times in three manners, and each session is exported by the admin API.
 */
export default defineConfig({
  testDir: './record',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60 * 60_000,
  expect: { timeout: 30_000 },
  reporter: 'list',
  use: {
    baseURL: process.env.GITS_URL ?? 'http://localhost:8080',
    locale: 'ru-RU',
    timezoneId: 'Europe/Moscow',
    ...devices['Desktop Chrome'],
    viewport: { width: 1280, height: 800 },
    permissions: ['clipboard-read', 'clipboard-write'],
  },
});
