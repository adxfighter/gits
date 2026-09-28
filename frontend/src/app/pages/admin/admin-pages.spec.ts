import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AdminSessionRow, TemplateRow, VariantDetails } from '../../core/admin/admin.models';
import { MonacoLoader } from '../../core/editor/monaco-loader.service';
import { AdminSessionsPage } from './admin-sessions-page';
import { AdminTasksPage } from './admin-tasks-page';
import { AdminVariantPage } from './admin-variant-page';

function text(element: Element | null | undefined): string {
  return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('admin pages', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=test';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MonacoLoader, useValue: { load: () => new Promise(() => undefined) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the task bank with validation results and issues', async () => {
    const templates: TemplateRow[] = [
      {
        code: 'T02',
        title: 'Взаимная блокировка',
        baseLevel: 'MIDDLE',
        competencies: ['java.concurrency.locking'],
        variants: [
          {
            id: 'v1', code: 'T02-v01', kind: 'TASK', level: 'MIDDLE', status: 'VALIDATED', domain: 'telecom',
            validationPassed: true, failedChecks: [], referenceMaxMs: 3524, issued: 7,
          },
          {
            id: 'v2', code: 'T02-v02', kind: 'TASK', level: 'SENIOR', status: 'DISABLED', domain: 'bank',
            validationPassed: false, failedChecks: ['starter_fails'], referenceMaxMs: null, issued: 0,
          },
        ],
      },
    ];
    const fixture = TestBed.createComponent(AdminTasksPage);
    fixture.detectChanges();
    http.expectOne('/api/admin/tasks').flush(templates);
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(text(root.querySelector('[data-testid="bank-summary"]'))).toBe('Шаблонов: 1 · вариантов: 2 · проверено: 1');
    const rows = root.querySelectorAll('[data-testid="variant-row"]');
    expect(text(rows[0])).toContain('T02-v01');
    expect(text(rows[0].querySelector('[data-testid="validation"]'))).toBe('✓ все проверки пройдены');
    expect(text(rows[0].querySelector('[data-testid="issued"]'))).toBe('7');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/admin/tasks/v1');
    expect(text(rows[1])).toContain('Отключён');
    expect(text(rows[1].querySelector('[data-testid="validation"]'))).toBe('✗ Заготовка не проходит скрытые тесты');
  });

  it('shows a variant with its statement, validation and every file, the secret ones marked', async () => {
    const variant: VariantDetails = {
      id: 'v1', code: 'T02-v01', templateCode: 'T02', templateTitle: 'Взаимная блокировка', kind: 'TASK',
      level: 'MIDDLE', status: 'VALIDATED', timeLimitMin: 20, statementMd: '# Условие\n\nИсправьте **блокировку**.',
      validationReport: { checks: [{ id: 'no_leak', passed: true, details: 'Фрагментов решения нет' }] }, issued: 3,
      files: [
        { path: 'src/main/java/Transfers.java', kind: 'STARTER', editable: true, content: 'class Transfers {}' },
        { path: 'src/main/java/Transfers.java', kind: 'SOLUTION', editable: false, content: 'class Transfers { ok }' },
        { path: 'src/test/java/TransfersHiddenTest.java', kind: 'HIDDEN_TEST', editable: false, content: 'test' },
      ],
    };
    const fixture = TestBed.createComponent(AdminVariantPage);
    fixture.componentRef.setInput('variantId', 'v1');
    fixture.detectChanges();
    http.expectOne('/api/admin/tasks/variants/v1').flush(variant);
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(text(root.querySelector('[data-testid="variant-code"]'))).toBe('T02-v01');
    expect(root.querySelector('[data-testid="statement"] strong')?.textContent).toBe('блокировку');
    expect(text(root.querySelector('[data-testid="checks"]'))).toContain('Нет утечки решения');
    // the starter and the solution share a path: both are there, the secret one marked
    expect([...root.querySelectorAll('[data-testid="code-tab"]')].map(text)).toEqual([
      'Transfers.java · заготовка',
      '🔒 Transfers.java · решение',
      '🔒 TransfersHiddenTest.java · скрытый тест',
    ]);
    expect(text(root.querySelector('[data-testid="secret-notice"]'))).toContain('только администратор');
  });

  function sessionRow(change: Partial<AdminSessionRow> = {}): AdminSessionRow {
    return {
      sessionId: 's1', companyName: 'Демо-компания', candidateLabel: 'Иван П.', targetLevel: 'MIDDLE',
      status: 'FINISHED', startedAt: '2026-09-28T08:00:00Z', finishedAt: '2026-09-28T09:00:00Z',
      preliminaryScore: 40, scoreComputedAt: '2026-09-28T09:00:10Z', trustLevel: 'GREEN', ...change,
    };
  }

  async function sessionsPage(rows: AdminSessionRow[]) {
    const fixture = TestBed.createComponent(AdminSessionsPage);
    fixture.detectChanges();
    http.expectOne('/api/admin/sessions').flush(rows);
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  it('lists sessions of every company and recalculates a finished one', async () => {
    const { fixture, root } = await sessionsPage([sessionRow(), sessionRow({ sessionId: 's2', status: 'IN_PROGRESS', preliminaryScore: null, trustLevel: null })]);
    const rows = root.querySelectorAll('[data-testid="session-row"]');
    expect(text(rows[0])).toContain('Демо-компания');
    expect(text(rows[0].querySelector('[data-testid="session-score"]'))).toBe('40');
    // a running session cannot be recalculated
    expect(rows[1].querySelector('[data-testid="rescore"]')).toBeNull();
    rows[0].querySelector<HTMLButtonElement>('[data-testid="rescore"]')!.click();
    const request = http.expectOne('/api/admin/sessions/s1/scoring');
    expect(request.request.method).toBe('POST');
    request.flush({ sessionId: 's1', scored: true, preliminaryScore: 55.5 });
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne('/api/admin/sessions').flush([sessionRow({ preliminaryScore: 55.5 })]);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="rescore-message"]'))).toBe('Пересчитано: балл 55,5.');
    expect(text(root.querySelector('[data-testid="session-score"]'))).toBe('55,5');
  });

  it('explains why a session cannot be recalculated yet', async () => {
    const { fixture, root } = await sessionsPage([sessionRow()]);
    root.querySelector<HTMLButtonElement>('[data-testid="rescore"]')!.click();
    http.expectOne('/api/admin/sessions/s1/scoring').flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="rescore-message"]'))).toContain('не все решения проверены');
  });

  it('builds the export link for the chosen period and rejects a reversed one', async () => {
    const { fixture, root } = await sessionsPage([]);
    const link = () => root.querySelector<HTMLAnchorElement>('[data-testid="export-download"]');
    expect(link()?.getAttribute('href')).toBe('/api/admin/export');
    const set = (id: string, value: string) => {
      const input = root.querySelector<HTMLInputElement>(`[data-testid="${id}"]`)!;
      input.value = value;
      input.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    };
    set('export-from', '2026-09-01');
    set('export-to', '2026-09-30');
    expect(link()?.getAttribute('href')).toBe('/api/admin/export?from=2026-09-01&to=2026-09-30');
    set('export-from', '2026-10-01');
    expect(link()).toBeNull();
    expect(text(root.querySelector('[data-testid="export-error"]'))).toBe('Начало периода позже его конца.');
    expect(text(root.querySelector('[data-testid="sessions-empty"]'))).toBe('Сессий пока нет.');
  });
});
