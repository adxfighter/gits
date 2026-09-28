import { Page, expect, test } from '@playwright/test';

import { employer } from '../support/env';
import { signIn } from '../support/flows';

// P16 acceptance after demo-seed (scripts/demo-reset or the CI step): the three demo sessions are in the employer's
// dashboard, scored, with different trust levels. Runs only when GITS_DEMO_SEEDED is set: on a stand without the
// seed there is nothing to check.

test.skip(!process.env.GITS_DEMO_SEEDED, 'set GITS_DEMO_SEEDED=1 after demo-seed');

const DEMOS = ['Демо: честное решение', 'Демо: решение крупными вставками', 'Демо: перепечатывание со второго экрана'];

/** The row whose label is exactly this: «Запись: Демо: …» (the recording a file was made from) is another row. */
function rowOf(page: Page, label: string) {
  const exact = new RegExp(`^\\s*${label}\\s*$`);
  return page
    .getByTestId('invite-row')
    .filter({ has: page.locator('td.table__label', { hasText: exact }) })
    .first();
}

test('после загрузки демо-данных в кабинете три демо-сессии с разными индикаторами', async ({ page }) => {
  await signIn(page, employer());
  const levels: string[] = [];
  for (const label of DEMOS) {
    const row = rowOf(page, label);
    await expect(row.getByTestId('invite-status')).toHaveText('Завершено');
    // gits-api scores the seeded sessions by itself (every 15 s); the list refreshes every 20 s
    await expect(row.getByTestId('invite-score')).toHaveText(/^\d/, { timeout: 90_000 });
    levels.push((await row.getByTestId('trust').getAttribute('data-level')) ?? '');
  }
  expect(levels[0]).toBe('GREEN');
  expect(levels.slice(1)).not.toContain('GREEN');
  expect(new Set(levels).size).toBeGreaterThan(1);

  await rowOf(page, DEMOS[2]).getByTestId('invite-report').click();
  await page.getByTestId('task-card').nth(1).getByTestId('replay').click();
  await expect(page.getByTestId('replay-check')).toContainText('совпадает с итоговым');
});
