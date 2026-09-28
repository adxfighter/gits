import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

import { APIRequestContext, Page, expect, request, test } from '@playwright/test';

import { ROOT, admin, employer } from '../support/env';
import { createInvite, finishAssessment, session, signIn, startAssessment } from '../support/flows';
import { solutionFor } from '../support/task-bank';

// Records the three demo sessions of P16 through the real candidate UI, so that their telemetry is what the
// platform really collects: an honest solution, a solution from large pastes, and «retyping from a second screen».
// Each is exported by the admin API into seed/demo-sessions. Run: npm run record-demo (takes 20–30 minutes).

test.describe.configure({ mode: 'serial' });

type Manner = 'honest' | 'pastes' | 'second-screen';

const DEMOS: { file: string; label: string; manner: Manner }[] = [
  { file: '01-honest.json', label: 'Демо: честное решение', manner: 'honest' },
  { file: '02-large-pastes.json', label: 'Демо: решение крупными вставками', manner: 'pastes' },
  { file: '03-second-screen.json', label: 'Демо: перепечатывание со второго экрана', manner: 'second-screen' },
];

/** The candidate's own pace: the warm-up and the honest work are typed at it (ms per key). */
const OWN_PACE_MS = 140;
/** Retyping a solution shown elsewhere: fast, steady, top to bottom. */
const RETYPING_PACE_MS = 35;

for (const demo of DEMOS) {
  test(demo.label, async ({ browser }) => {
    const employerPage = await (await browser.newContext()).newPage();
    await signIn(employerPage, employer());
    // the recording itself is not a demo: a seed into the same stand must not take it for one
    const link = await createInvite(employerPage, `Запись: ${demo.label}`);
    await employerPage.context().close();

    const context = await browser.newContext({ permissions: ['clipboard-read', 'clipboard-write'] });
    const page = await context.newPage();
    await startAssessment(page, link);
    const { id: sessionId, tasks } = await session(page);

    await warmUp(page, tasks[0].id);
    for (const index of [1, 2, 3]) {
      await solve(page, index, tasks[index].id, demo.manner);
    }
    await finishAssessment(page);
    await context.close();

    const api = await adminApi();
    await waitForScore(api, sessionId);
    const response = await api.get(`/api/admin/sessions/${sessionId}/demo-export`, { params: { label: demo.label } });
    expect(response.ok()).toBeTruthy();
    const dir = join(ROOT, 'seed', 'demo-sessions');
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, demo.file), JSON.stringify(await response.json()) + '\n');
    await api.dispose();
  });
}

/** Part 1 retyped by hand at the candidate's pace; part 2 left as it is; the warm-up submitted. */
async function warmUp(page: Page, taskId: string): Promise<void> {
  await page.getByTestId('task-tab').nth(0).click();
  await page.getByTestId('calibration-part-1').click();
  const view = await (await page.request.get(`/api/candidate/tasks/${taskId}`)).json();
  const sample: string = view.files.find((f: { path: string }) => f.path.endsWith('Sample.txt')).content;
  await focusEditor(page);
  await typeLikeAPerson(page, withoutIndentation(sample), OWN_PACE_MS, true);
  await page.getByRole('button', { name: 'Проверить перепечатку' }).click();
  await expect(page.getByTestId('results')).toContainText('Перепечатка', { timeout: 60_000 });
  await submit(page, 0);
}

async function solve(page: Page, index: number, taskId: string, manner: Manner): Promise<void> {
  await page.getByTestId('task-tab').nth(index).click();
  const view = await (await page.request.get(`/api/candidate/tasks/${taskId}`)).json();
  const solution = solutionFor(view.files);
  for (const [path, target] of Object.entries(solution)) {
    const entry = page.locator(`.ws__file[title="${path}"]`);
    if (await entry.count()) {
      await entry.click();
    }
    await focusEditor(page);
    await exactTyping(page);
    const current = await editorText(page);
    if (current === target) {
      continue;
    }
    if (manner === 'honest') {
      // reads the task, then changes only what has to change, at the own pace and with typos
      await page.waitForTimeout(8_000);
      await replaceMiddle(page, current, target, (text) => typeWithLookingBack(page, text));
      await page.getByRole('button', { name: 'Запустить тесты' }).click();
      await expect(page.getByTestId('results-state').first()).toContainText(/Пройдено|Ошибка/, { timeout: 120_000 });
    } else if (manner === 'pastes') {
      await page.waitForTimeout(3_000);
      await page.keyboard.press('Control+A');
      await page.evaluate((text) => navigator.clipboard.writeText(text), target);
      await page.keyboard.press('Control+V');
    } else {
      // starts at the own pace, then twice: away from the page (the solution is open elsewhere) and back to a fast,
      // straight retyping of the next part
      await page.keyboard.press('Control+A');
      await page.keyboard.press('Delete');
      const first = Math.min(60, target.length);
      const second = first + Math.floor((target.length - first) / 2);
      await typeLikeAPerson(page, target.slice(0, first), OWN_PACE_MS, false);
      for (const part of [target.slice(first, second), target.slice(second)]) {
        await page.evaluate(() => window.dispatchEvent(new Event('blur')));
        await page.waitForTimeout(40_000);
        await page.evaluate(() => window.dispatchEvent(new Event('focus')));
        await focusEditor(page);
        await typeLikeAPerson(page, part, RETYPING_PACE_MS, false);
      }
    }
    await expect.poll(() => editorText(page)).toBe(target);
  }
  await submit(page, index);
}

async function submit(page: Page, index: number): Promise<void> {
  await page.getByRole('button', { name: 'Отправить решение' }).click();
  await page.getByTestId('confirm').click();
  await expect(page.getByTestId('task-tab').nth(index)).toContainText('✓', { timeout: 60_000 });
}

/** Focuses the candidate's editor: the one showing a task file (the warm-up sample above it has no task model). */
async function focusEditor(page: Page): Promise<void> {
  await page.waitForFunction(() => {
    const monaco = (window as unknown as { monaco?: MonacoLike }).monaco;
    return !!monaco?.editor.getEditors().some((e) => e.getModel()?.uri.toString().startsWith('inmemory://task/'));
  });
  await page.evaluate(() => {
    const monaco = (window as unknown as { monaco: MonacoLike }).monaco;
    monaco.editor.getEditors().find((e) => e.getModel()?.uri.toString().startsWith('inmemory://task/'))!.focus();
  });
  await page.waitForTimeout(200);
}

/** No automatic brackets, quotes or indents: what is typed is exactly the text (the warm-up has them off anyway). */
async function exactTyping(page: Page): Promise<void> {
  await page.evaluate(() => {
    const monaco = (window as unknown as { monaco: { editor: { getEditors(): { hasTextFocus(): boolean; updateOptions(o: object): void }[] } } }).monaco;
    for (const editor of monaco.editor.getEditors()) {
      editor.updateOptions({
        autoClosingBrackets: 'never',
        autoClosingQuotes: 'never',
        autoClosingOvertype: 'never',
        autoIndent: 'none',
        autoSurround: 'never',
        quickSuggestions: false,
        suggestOnTriggerCharacters: false,
        acceptSuggestionOnEnter: 'off',
        formatOnType: false,
      });
    }
  });
}

async function editorText(page: Page): Promise<string> {
  return page.evaluate(() => {
    const monaco = (window as unknown as { monaco: MonacoLike }).monaco;
    return monaco.editor.getEditors().find((e) => e.getModel()?.uri.toString().startsWith('inmemory://task/'))!.getValue();
  });
}

/** The part of Monaco's API the recording uses. */
interface MonacoLike {
  editor: { getEditors(): { getModel(): { uri: { toString(): string } } | null; focus(): void; getValue(): string }[] };
}

/** Selects what differs between the current text and the target (common start and end kept) and types over it. */
async function replaceMiddle(page: Page, current: string, target: string, type: (text: string) => Promise<void>): Promise<void> {
  let start = 0;
  while (start < current.length && start < target.length && current[start] === target[start]) {
    start++;
  }
  let end = 0;
  while (end < current.length - start && end < target.length - start
    && current[current.length - 1 - end] === target[target.length - 1 - end]) {
    end++;
  }
  await page.evaluate(({ from, to }) => {
    const monaco = (window as unknown as { monaco: { Selection: new (a: number, b: number, c: number, d: number) => unknown; editor: { getEditors(): { hasTextFocus(): boolean; getModel(): { getPositionAt(o: number): { lineNumber: number; column: number } }; setSelection(s: unknown): void }[] } } }).monaco;
    const editor = monaco.editor.getEditors().find((e) => e.hasTextFocus())!;
    const model = editor.getModel();
    const a = model.getPositionAt(from);
    const b = model.getPositionAt(to);
    editor.setSelection(new monaco.Selection(a.lineNumber, a.column, b.lineNumber, b.column));
  }, { from: start, to: current.length - end });
  const middle = target.slice(start, target.length - end);
  if (middle.length === 0) {
    await page.keyboard.press('Delete');
  } else {
    await type(middle);
  }
}

/**
 * Types key by key at about {@code paceMs} per key, with a little jitter; with {@code typos}, now and then a wrong
 * letter is typed and taken back, as people do.
 */
async function typeLikeAPerson(page: Page, text: string, paceMs: number, typos: boolean): Promise<void> {
  let i = 0;
  for (const char of text) {
    if (typos && i > 0 && i % 97 === 0 && /[a-z]/.test(char)) {
      await page.keyboard.type('q', { delay: 0 });
      await page.waitForTimeout(paceMs * 2);
      await page.keyboard.press('Backspace');
    }
    if (char === '\n') {
      await page.keyboard.press('Enter');
    } else {
      await page.keyboard.type(char, { delay: 0 });
    }
    await page.waitForTimeout(Math.round(paceMs * (0.6 + ((i * 37) % 80) / 100)));
    i++;
  }
}

/**
 * The own pace, but not straight from top to bottom: every two lines the candidate goes back to the line above,
 * adds a note and takes it away again, as people re-read and touch up what they wrote.
 */
async function typeWithLookingBack(page: Page, text: string): Promise<void> {
  const lines = text.split('\n');
  for (let i = 0; i < lines.length; i++) {
    await typeLikeAPerson(page, lines[i] + (i < lines.length - 1 ? '\n' : ''), OWN_PACE_MS, true);
    if (i % 2 === 1 && i < lines.length - 1) {
      const position = await page.evaluate(() => {
        const monaco = (window as unknown as { monaco: MonacoWithPosition }).monaco;
        return monaco.editor.getEditors().find((e) => e.hasTextFocus())!.getPosition();
      });
      await page.keyboard.press('ArrowUp');
      await page.keyboard.press('End');
      const note = ' // проверить';
      await typeLikeAPerson(page, note, OWN_PACE_MS, false);
      for (let k = 0; k < note.length; k++) {
        await page.keyboard.press('Backspace');
        await page.waitForTimeout(OWN_PACE_MS / 2);
      }
      await page.evaluate((back) => {
        const monaco = (window as unknown as { monaco: MonacoWithPosition }).monaco;
        monaco.editor.getEditors().find((e) => e.hasTextFocus())!.setPosition(back);
      }, position);
    }
  }
}

interface MonacoWithPosition {
  editor: {
    getEditors(): {
      hasTextFocus(): boolean;
      getPosition(): { lineNumber: number; column: number };
      setPosition(p: { lineNumber: number; column: number }): void;
    }[];
  };
}

/** The warm-up is compared without whitespace: its indentation need not be typed. */
function withoutIndentation(text: string): string {
  return text
    .split('\n')
    .map((line) => line.trimStart())
    .join('\n');
}

async function adminApi(): Promise<APIRequestContext> {
  const api = await request.newContext({ baseURL: process.env.GITS_URL ?? 'http://localhost:8080' });
  await api.get('/api/auth/csrf');
  const token = (await api.storageState()).cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  const login = await api.post('/api/auth/login', { data: admin(), headers: { 'X-XSRF-TOKEN': token } });
  expect(login.ok()).toBeTruthy();
  return api;
}

/** Scored means every submit was checked: the export then has final runs only. */
async function waitForScore(api: APIRequestContext, sessionId: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const rows = (await (await api.get('/api/admin/sessions')).json()) as { sessionId: string; preliminaryScore: number | null }[];
        return rows.find((row) => row.sessionId === sessionId)?.preliminaryScore ?? null;
      },
      { timeout: 5 * 60_000, intervals: [5_000] },
    )
    .not.toBeNull();
}
