import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { RunView } from '../../../core/candidate/candidate.models';
import { ResultsPanel } from './results-panel';

function runView(change: Partial<RunView>): RunView {
  return {
    id: 'run-1',
    mode: 'RUN',
    status: 'DONE',
    createdAt: '2026-09-01T09:00:00Z',
    finishedAt: '2026-09-01T09:00:02Z',
    compiled: true,
    testsTotal: 2,
    testsPassed: 1,
    compileOutput: null,
    tests: [
      { name: 'VisibleTest › sums', status: 'PASSED', message: null },
      { name: 'VisibleTest › empty', status: 'FAILED', message: 'expected: <0> but was: <1>' },
    ],
    output: 'hello',
    durationMs: 1234,
    ...change,
  };
}

describe('ResultsPanel', () => {
  function render(run: RunView | null): { fixture: ComponentFixture<ResultsPanel>; root: HTMLElement } {
    const fixture = TestBed.createComponent(ResultsPanel);
    fixture.componentRef.setInput('run', run);
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function state(root: HTMLElement): string {
    return root.querySelector('[data-testid="results-state"]')?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('invites to run the tests before the first run', () => {
    expect(render(null).root.textContent).toContain('Запустите тесты');
  });

  it('shows the queue and the running state', () => {
    expect(state(render(runView({ status: 'QUEUED' })).root)).toContain('В очереди');
    expect(state(render(runView({ status: 'RUNNING' })).root)).toContain('Выполняется');
  });

  it('lists tests with their statuses, messages, the time and the program output', () => {
    const { root } = render(runView({}));
    expect(state(root)).toContain('Пройдено 1 из 2');
    expect(state(root)).toContain('1,2 с');
    const tests = root.querySelectorAll('[data-testid="test"]');
    expect(tests).toHaveLength(2);
    expect(tests[0].classList).toContain('test--passed');
    expect(tests[1].classList).toContain('test--failed');
    expect(tests[1].textContent).toContain('expected: <0> but was: <1>');
    expect(root.querySelector('.stdout')?.textContent).toContain('hello');
  });

  it('shows the compiler output when the code does not compile', () => {
    const { root } = render(
      runView({ compiled: false, compileOutput: "Main.java:3: error: ';' expected", tests: [], testsTotal: 0, testsPassed: 0 }),
    );
    expect(state(root)).toBe('Ошибка компиляции');
    expect(root.querySelector('[data-testid="compile-output"]')?.textContent).toContain("';' expected");
    expect(root.querySelectorAll('[data-testid="test"]')).toHaveLength(0);
  });

  it('shows only the counts of hidden tests for a submit', () => {
    const { root } = render(runView({ mode: 'SUBMIT', tests: [], output: null, testsTotal: 5, testsPassed: 5 }));
    expect(state(root)).toContain('Решение отправлено. Скрытые тесты: пройдено 5 из 5');
    expect(root.querySelector('.state--ok')).not.toBeNull();
    expect(root.querySelectorAll('[data-testid="test"]')).toHaveLength(0);
  });

  it('shows the retyping check in warm-up part 1 instead of test runs', () => {
    const fixture = TestBed.createComponent(ResultsPanel);
    fixture.componentRef.setInput('retypingMode', true);
    fixture.componentRef.setInput('run', runView({}));
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Проверить перепечатку');
    expect(root.querySelectorAll('[data-testid="test"]')).toHaveLength(0);

    fixture.componentRef.setInput('retyping', {
      similarityPercent: 82.4, passed: false, pasteSuspected: false,
      message: 'Напечатанный текст отличается от первоначального более 5 % (совпадение с образцом — 82,4%).',
    });
    fixture.detectChanges();
    expect(state(root)).toContain('отличается от первоначального более 5 %');
    expect(root.querySelector('.state--bad')).not.toBeNull();

    fixture.componentRef.setInput('retyping', {
      similarityPercent: 98.1, passed: true, pasteSuspected: false,
      message: 'Перепечатка засчитана: текст совпадает с образцом на 98,1%.',
    });
    fixture.detectChanges();
    expect(root.querySelector('.state--ok')).not.toBeNull();
  });

  it('does not show hidden test counts for the warm-up, which has none', () => {
    const fixture = TestBed.createComponent(ResultsPanel);
    fixture.componentRef.setInput('calibration', true);
    fixture.componentRef.setInput('run', runView({ mode: 'SUBMIT', tests: [], testsTotal: 0, testsPassed: 0 }));
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(state(root)).toContain('Разминка отправлена');
    expect(state(root)).not.toContain('0 из 0');
    expect(root.querySelector('.state--ok')).not.toBeNull();
  });

  it('explains a timeout and a runner error', () => {
    expect(state(render(runView({ status: 'TIMEOUT' })).root)).toContain('Превышено время выполнения');
    expect(state(render(runView({ status: 'ERROR' })).root)).toContain('Не удалось выполнить запуск');
  });
});
