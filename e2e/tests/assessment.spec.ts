import { Browser, Page, expect, test } from '@playwright/test';

import { employer, sql } from '../support/env';
import { createInvite, finishAssessment, session, signIn, solveTask, startAssessment } from '../support/flows';

// P16 end-to-end scenarios against the docker compose stack. They run in order and share the invite they create:
// the employer invites, the candidate passes the assessment with the reference solutions, the employer reads the
// report and replays a task; then a used link, and a session whose time runs out.

test.describe.configure({ mode: 'serial' });

const label = `E2E ${new Date().toISOString().slice(0, 19)}`;
let link = '';
let sessionId = '';

async function employerPage(browser: Browser): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await signIn(page, employer());
  return page;
}

async function candidatePage(browser: Browser): Promise<Page> {
  // a separate browser context: the candidate's cookie never mixes with the employer's session
  return (await browser.newContext()).newPage();
}

test('работодатель создаёт приглашение', async ({ browser }) => {
  const page = await employerPage(browser);
  link = await createInvite(page, label);
  await page.context().close();
});

test('кандидат проходит оценку: разминка и три задачи с эталонными решениями', async ({ browser }) => {
  const page = await candidatePage(browser);
  await startAssessment(page, link);
  const view = await session(page);
  sessionId = view.id;
  expect(view.tasks.map((task) => task.kind)).toEqual(['CALIBRATION', 'TASK', 'TASK', 'TASK']);
  await solveTask(page, 0);
  for (const index of [1, 2, 3]) {
    const result = await solveTask(page, index);
    // the reference solution passes every hidden test
    const [, passed, total] = result.match(/пройдено (\d+) из (\d+)/) ?? [];
    expect(Number(total)).toBeGreaterThan(0);
    expect(passed).toBe(total);
  }
  await finishAssessment(page);
  await page.context().close();
});

test('работодатель открывает отчёт и воспроизведение', async ({ browser }) => {
  const page = await employerPage(browser);
  const row = page.getByTestId('invite-row').filter({ hasText: label });
  await expect(row.getByTestId('invite-status')).toHaveText('Завершено');
  await row.getByTestId('invite-report').click();
  // scored once every submit is checked: the report reloads itself until then
  await expect(page.getByTestId('score')).toHaveText('100', { timeout: 2 * 60_000 });
  await expect(page.getByTestId('score-note')).toContainText('Предварительный балл');
  const cards = page.getByTestId('task-card');
  await expect(cards).toHaveCount(4);
  await expect(cards.nth(1).getByTestId('hidden-tests')).toHaveText(/^(\d+) из \1$/);
  await expect(cards.nth(1).getByTestId('indicators')).toBeVisible();

  await cards.nth(1).getByTestId('replay').click();
  await expect(page.getByTestId('replay-check')).toContainText('совпадает с итоговым');
  // the test put the code in at once, so the recording is short: its content is what is checked here
  await expect(page.getByTestId('replay-time')).toContainText('0:00 /');
  await expect(page.getByTestId('replay-tab').first()).toBeVisible();
  await expect(page.getByTestId('live-figures')).toBeVisible();
  await expect(page.getByTestId('timeline')).toBeVisible();
  await page.context().close();
});

test('повторный вход по использованной ссылке', async ({ browser }) => {
  const page = await candidatePage(browser);
  await page.goto(link);
  await expect(page.getByTestId('enter-error')).toContainText('Оценка по этой ссылке уже пройдена');
  await page.context().close();
});

test('истечение времени', async ({ browser }) => {
  const employerTab = await employerPage(browser);
  const expiring = await createInvite(employerTab, `${label} — время`);
  const page = await candidatePage(browser);
  await startAssessment(page, expiring);
  const { id } = await session(page);
  // 90 minutes pass: the session is moved back in time rather than waited for
  sql(`UPDATE assessment_session SET started_at = started_at - interval '91 minutes' WHERE id = '${id}'`);
  await page.reload();
  await expect(page.getByRole('alertdialog')).toContainText('Время вышло');
  await page.getByRole('alertdialog').getByRole('button', { name: 'Завершить' }).click();
  // the server closes the session (its expiry check runs every 30 s) and submits the tasks as they were
  await expect
    .poll(() => sql(`SELECT status FROM assessment_session WHERE id = '${id}'`), { timeout: 90_000 })
    .toBe('EXPIRED');
  await employerTab.goto('/employer');
  await expect(employerTab.getByTestId('invite-row').filter({ hasText: `${label} — время` }).getByTestId('invite-status'))
    .toHaveText('Завершено: время вышло', { timeout: 60_000 });
  expect(sessionId).not.toBe(id);
  await page.context().close();
  await employerTab.context().close();
});
