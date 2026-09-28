import { Page, expect } from '@playwright/test';

import { CandidateFile, solutionFor } from './task-bank';

// Steps of the scenarios in the UI, as a person does them. Code is put into the editor through Monaco's own API:
// typing whole solutions key by key would only make the tests slow.

export interface Account {
  email: string;
  password: string;
}

export async function signIn(page: Page, account: Account): Promise<void> {
  await page.goto('/login');
  await page.getByTestId('login-email').fill(account.email);
  await page.getByTestId('login-password').fill(account.password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('header-user')).toContainText(account.email);
}

/** «Пригласить кандидата» → label and level → the one-time link. */
export async function createInvite(page: Page, label: string, level = 'MIDDLE'): Promise<string> {
  await page.goto('/employer');
  await page.getByTestId('invite-open').click();
  await page.getByTestId('invite-label').fill(label);
  await page.getByTestId('invite-level').selectOption(level);
  await page.getByTestId('invite-create').click();
  const link = await page.getByTestId('invite-link').inputValue();
  expect(link).toMatch(/\/c\/[A-Za-z0-9_-]+$/);
  await page.getByTestId('invite-done').click();
  // the list is newest first: the invite just made is the first row with its label
  await expect(page.getByTestId('invite-row').filter({ hasText: label }).first()).toContainText('Ожидает кандидата');
  return link;
}

/** From the link to the workspace: consent, rules, «Начать оценку». */
export async function startAssessment(page: Page, link: string): Promise<void> {
  await page.goto(link);
  await page.getByTestId('consent-checkbox').check();
  await page.getByRole('button', { name: 'Согласен и начинаю' }).click();
  await page.getByRole('button', { name: 'Начать оценку' }).click();
  await expect(page.getByTestId('task-tab').first()).toBeVisible();
}

interface SessionView {
  id: string;
  tasks: { id: string; kind: 'TASK' | 'CALIBRATION'; status: string }[];
}

interface TaskView {
  id: string;
  kind: 'TASK' | 'CALIBRATION';
  files: CandidateFile[];
}

export async function session(page: Page): Promise<SessionView> {
  const response = await page.request.get('/api/candidate/session');
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as SessionView;
}

/**
 * Opens the n-th task (0 — the warm-up), puts the reference solution into it, submits and waits for the hidden
 * tests. Returns the result line of the panel.
 */
export async function solveTask(page: Page, index: number): Promise<string> {
  const { tasks } = await session(page);
  const task = tasks[index];
  await page.getByTestId('task-tab').nth(index).click();
  const view = (await (await page.request.get(`/api/candidate/tasks/${task.id}`)).json()) as TaskView;
  const code = solutionFor(view.files);
  if (view.kind === 'CALIBRATION') {
    // warm-up part 1 is the sample retyped: the same text
    const sample = view.files.find((file) => file.path.endsWith('Sample.txt'));
    const typing = view.files.find((file) => file.path.endsWith('Typing.txt'));
    if (sample && typing) {
      code[typing.path] = sample.content;
    }
  }
  await putCode(page, task.id, code, view.kind === 'CALIBRATION');
  await page.getByRole('button', { name: 'Отправить решение' }).click();
  await page.getByTestId('confirm').click();
  await expect(page.getByTestId('task-tab').nth(index)).toContainText('✓');
  if (view.kind === 'CALIBRATION') {
    // part 1 shows the retyping check; the result of the submit is under part 2
    await page.getByTestId('calibration-part-2').click();
  }
  const state = page.getByTestId('results-state').first();
  await expect(state).toContainText(view.kind === 'CALIBRATION' ? 'Разминка отправлена' : 'Скрытые тесты', {
    timeout: 3 * 60_000,
  });
  return (await state.textContent()) ?? '';
}

/**
 * Puts the code into the editor, file by file, as the candidate would have it before «Отправить решение»: each file
 * is opened from the file list (the editor makes its model then) and its text is set through Monaco. In the warm-up
 * the retyping file is in part 1, the class of part 2 in part 2.
 */
async function putCode(page: Page, taskId: string, code: Record<string, string>, calibration: boolean): Promise<void> {
  await page.waitForFunction(() => !!(window as unknown as { monaco?: unknown }).monaco);
  for (const [path, text] of Object.entries(code)) {
    if (calibration) {
      await page.getByTestId(path.endsWith('.txt') ? 'calibration-part-1' : 'calibration-part-2').click();
    }
    const entry = page.locator(`.ws__file[title="${path}"]`);
    if (await entry.count()) {
      await entry.click();
    }
    const uri = `inmemory://task/${taskId}/${path}`;
    await page.waitForFunction((modelUri) => {
      const monaco = (window as unknown as { monaco: MonacoLike }).monaco;
      return !!monaco.editor.getModel(monaco.Uri.parse(modelUri));
    }, uri);
    await page.evaluate(
      ({ modelUri, value }) => {
        const monaco = (window as unknown as { monaco: MonacoLike }).monaco;
        monaco.editor.getModel(monaco.Uri.parse(modelUri))!.setValue(value);
      },
      { modelUri: uri, value: text },
    );
  }
}

/** The part of Monaco's API the tests use (the page loads Monaco itself, the tests have no types of it). */
interface MonacoLike {
  Uri: { parse(value: string): unknown };
  editor: { getModel(uri: unknown): { setValue(value: string): void } | null };
}

/**
 * «Завершить оценку» → «Завершить» → the thank-you page. After the last submit the workspace offers to finish by
 * itself: then its dialog is already open.
 */
export async function finishAssessment(page: Page): Promise<void> {
  const confirm = page.getByTestId('confirm');
  if (!(await confirm.isVisible())) {
    await page.getByRole('button', { name: 'Завершить оценку' }).click();
  }
  await confirm.click();
  await expect(page.getByRole('heading', { name: 'Спасибо!' })).toBeVisible();
}
