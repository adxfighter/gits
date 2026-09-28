import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MonacoLoader } from '../../core/editor/monaco-loader.service';
import { SessionReport } from '../../core/employer/employer.models';
import { calibrationReport, sessionReport, taskReport } from './employer-fixtures';
import { REPORT_REFRESH_MS, ReportPage } from './report-page';

describe('ReportPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        // the editor itself is not part of these tests
        { provide: MonacoLoader, useValue: { load: () => new Promise(() => undefined) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function create(): ComponentFixture<ReportPage> {
    const fixture = TestBed.createComponent(ReportPage);
    fixture.componentRef.setInput('id', 'session-1');
    fixture.detectChanges();
    return fixture;
  }

  async function render(report: SessionReport): Promise<{ fixture: ComponentFixture<ReportPage>; root: HTMLElement }> {
    const fixture = create();
    http.expectOne('/api/employer/sessions/session-1/report').flush(report);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function text(element: Element | null | undefined): string {
    return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('shows the preliminary score with its note and the session trust level', async () => {
    const { root } = await render(sessionReport());
    expect(text(root.querySelector('[data-testid="report-candidate"]'))).toBe('Иван П.');
    expect(text(root.querySelector('[data-testid="score"]'))).toBe('60');
    expect(text(root.querySelector('[data-testid="score-note"]'))).toContain('Предварительный балл');
    expect(text(root.querySelector('[data-testid="score-card"] [data-testid="trust"]'))).toBe('Есть замечания');
  });

  it('shows every task: competencies, level, hidden tests N/M, time, runs, indicators with explanations', async () => {
    const { root } = await render(sessionReport());
    const cards = root.querySelectorAll('[data-testid="task-card"]');
    expect(cards).toHaveLength(2);
    const task = cards[1];
    expect(text(task)).toContain('Задача 1');
    expect(text(task.querySelector('[data-testid="competency"]'))).toBe('Блокировки и взаимные блокировки');
    expect(text(task.querySelector('[data-testid="hidden-tests"]'))).toBe('3 из 5');
    expect(text(task.querySelector('[data-testid="duration"]'))).toBe('22 мин 5 с');
    expect(text(task.querySelector('[data-testid="runs"]'))).toBe('4');
    expect(text(task.querySelector('[data-testid="trust-reasons"]'))).toContain('крупная вставка');
    const indicators = task.querySelectorAll('[data-testid="indicators"] tr');
    // known indicators in the fixed order, trustReasons is not a row
    expect([...indicators].map((row) => row.getAttribute('data-indicator'))).toEqual(['pasteRatio', 'largestPaste']);
    expect(text(indicators[0])).toContain('Доля вставок');
    expect(text(indicators[0])).toContain('Доля итогового кода, пришедшая вставкой: 30%');
    expect(task.querySelector('app-code-viewer')).not.toBeNull();
    expect(task.querySelector('a[data-testid="replay"]')?.getAttribute('href')).toBe('/employer/replay/task-2');
  });

  it('marks the warm-up as not scored and shows its retyping result', async () => {
    const { root } = await render(sessionReport());
    const warmUp = root.querySelectorAll('[data-testid="task-card"]')[0];
    expect(text(warmUp)).toContain('Разминка · вес 0,5');
    // no counted tests in the fixture: its part 2 gave nothing
    expect(text(warmUp.querySelector('[data-testid="hidden-tests"]'))).toBe('0 (тесты части 2)');
    expect(text(warmUp)).toContain('не входит в оценку');
    expect(text(warmUp)).toContain('Перепечатка засчитана');
    // the warm-up rules do not count, so they are not listed as reasons
    expect(warmUp.querySelector('[data-testid="trust-reasons"]')).toBeNull();
  });

  it('shows what the score counts: the fixed tests, the «nothing broken» ones, and highlights the lines behind the remarks', async () => {
    const counted = { counted: 3, countedPassed: 1, guards: 2, guardsBroken: 0, unchanged: false, share: 0.3333 };
    const { root } = await render(
      sessionReport({
        tasks: [
          calibrationReport({ counted: { counted: 3, countedPassed: 2, guards: null, guardsBroken: 0, unchanged: false, share: 0.6667 } }),
          taskReport({
            counted,
            indicators: {
              focusLoss: { value: { count: 10, seconds: 540 }, explanation: 'Уход со вкладки или из окна: 10 раз, всего 9 мин.' },
              pasteRatio: { value: 0, explanation: 'Доля вставок 0%.' },
              trustReasons: { value: ['Вкладка надолго покидалась.'], explanation: '' },
              trustRules: { value: [{ level: 'YELLOW', reason: 'Вкладка надолго покидалась.', indicators: ['focusLoss'] }], explanation: '' },
            },
          }),
          taskReport({ id: 'u', counted: { ...counted, countedPassed: 0, unchanged: true, share: 0 } }),
          taskReport({ id: 'g', counted: { ...counted, guardsBroken: 1, share: 0 } }),
        ],
      }),
    );
    const hidden = [...root.querySelectorAll('[data-testid="hidden-tests"]')].map(text);
    expect(hidden).toEqual(['2 из 3 (тесты части 2)', 'исправлено 1 из 3', '0: код не изменён', '0: сломано проверок «ничего не сломано» — 1']);
    expect(text(root.querySelector('[data-testid="guards"]'))).toBe('Ещё 2 скрытых тестов проверяют, что ничего не сломано: прошли');
    const card = root.querySelectorAll('[data-testid="task-card"]')[1];
    expect(text(card.querySelector('[data-testid="trust-reasons"] li'))).toBe('Есть замечания: Вкладка надолго покидалась.');
    expect(card.querySelector('tr[data-indicator="focusLoss"]')?.getAttribute('data-flag')).toBe('YELLOW');
    expect(card.querySelector('tr[data-indicator="pasteRatio"]')?.hasAttribute('data-flag')).toBe(false);
  });

  it('explains why hidden tests have no result', async () => {
    const { root } = await render(
      sessionReport({
        tasks: [
          taskReport({ id: 'a', submitStatus: 'DONE', submitCompiled: false, hiddenTestsPassed: 0, hiddenTestsTotal: null }),
          taskReport({ id: 'b', submitStatus: 'TIMEOUT', hiddenTestsPassed: 0, hiddenTestsTotal: null }),
          taskReport({ id: 'c', status: 'NOT_STARTED', startedAt: null, submitStatus: null, durationSeconds: null, hiddenTestsPassed: null, hiddenTestsTotal: null }),
          // never opened, sent as it was when the session finished
          taskReport({ id: 'd', startedAt: null, submittedAt: '2026-09-28T09:10:00Z', durationSeconds: null, hiddenTestsPassed: 0 }),
        ],
      }),
    );
    const hidden = [...root.querySelectorAll('[data-testid="hidden-tests"]')].map(text);
    expect(hidden).toEqual(['0: код не скомпилировался', '0: превышено время выполнения', 'Задача не открыта', '0 из 5']);
    const cards = root.querySelectorAll('[data-testid="task-card"]');
    for (const notOpened of [cards[2], cards[3]]) {
      expect(notOpened.querySelector('button[data-testid="replay"]')?.hasAttribute('disabled')).toBe(true);
      expect(notOpened.querySelector('[data-testid="duration"]')?.textContent?.trim()).toBe('не открыта');
    }
  });

  it('shows a task left out of the score', async () => {
    const { root } = await render(
      sessionReport({
        scorePerTask: { tasks: [{ sessionTaskId: 'task-2', level: 'MIDDLE', excluded: 'проверка не выполнена из-за сбоя' }] },
      }),
    );
    expect(text(root.querySelector('[data-testid="excluded"]'))).toContain('проверка не выполнена из-за сбоя');
  });

  it('reloads a report whose score is not computed yet, until it is', async () => {
    vi.useFakeTimers();
    const fixture = create();
    http.expectOne('/api/employer/sessions/session-1/report').flush(
      sessionReport({
        preliminaryScore: null,
        trustLevel: null,
        tasks: [calibrationReport({ indicators: null }), taskReport({ indicators: null, trustLevel: null })],
      }),
    );
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(text(root.querySelector('[data-testid="score-pending"]'))).toContain('Балл считается');
    expect(root.querySelectorAll('[data-testid="indicators-pending"]')).toHaveLength(2);
    vi.advanceTimersByTime(REPORT_REFRESH_MS);
    http.expectOne('/api/employer/sessions/session-1/report').flush(sessionReport());
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="score"]'))).toBe('60');
    vi.advanceTimersByTime(REPORT_REFRESH_MS * 2);
    http.expectNone('/api/employer/sessions/session-1/report');
  });

  it('keeps the report on a failed reload and keeps trying', () => {
    vi.useFakeTimers();
    const fixture = create();
    const url = '/api/employer/sessions/session-1/report';
    http.expectOne(url).flush(sessionReport({ status: 'IN_PROGRESS', preliminaryScore: null, trustLevel: null }));
    fixture.detectChanges();
    vi.advanceTimersByTime(REPORT_REFRESH_MS);
    http.expectOne(url).flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="score-pending"]')?.textContent).toContain('Оценка ещё идёт');
    vi.advanceTimersByTime(REPORT_REFRESH_MS);
    http.expectOne(url).flush(sessionReport());
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="score"]')?.textContent?.trim()).toBe('60');
  });

  it('shows a load error with a retry', async () => {
    const fixture = create();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="report-loading"]')).not.toBeNull();
    http.expectOne('/api/employer/sessions/session-1/report').flush({ detail: 'Сбой' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="report-error"]')?.textContent).toContain('Сбой');
    root.querySelector<HTMLButtonElement>('[data-testid="report-error"] button')!.click();
    fixture.detectChanges();
    http.expectOne('/api/employer/sessions/session-1/report').flush(sessionReport());
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="score"]')).not.toBeNull();
  });

  it('tells a missing session from other failures', async () => {
    const fixture = create();
    http.expectOne('/api/employer/sessions/session-1/report').flush({ detail: 'Сессия не найдена' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="report-not-found"]')).not.toBeNull();
  });
});
