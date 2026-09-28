import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RetypingResult, RunView } from '../../../core/candidate/candidate.models';

/** Result of the last run of the task: compiler output, tests with statuses, time. */
@Component({
  selector: 'app-results-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="results" data-testid="results">
      @let current = run();
      @if (retypingMode()) {
        @if (retyping(); as check) {
          <p
            class="state"
            [class.state--ok]="check.passed"
            [class.state--bad]="!check.passed"
            data-testid="results-state"
          >
            {{ check.message }}
          </p>
        } @else {
          <p class="muted">Перепечатайте фрагмент и нажмите «Проверить перепечатку» (Ctrl+Enter). Код не запускается.</p>
        }
      } @else if (!current) {
        <p class="muted">Запустите тесты, чтобы увидеть результат (Ctrl+Enter).</p>
      } @else {
        @switch (current.status) {
          @case ('QUEUED') {
            <p class="state" data-testid="results-state">В очереди на запуск…</p>
          }
          @case ('RUNNING') {
            <p class="state" data-testid="results-state">Выполняется…</p>
          }
          @case ('TIMEOUT') {
            <p class="state state--bad" data-testid="results-state">
              Превышено время выполнения. Проверьте бесконечные циклы и ожидания.
            </p>
          }
          @case ('ERROR') {
            <p class="state state--bad" data-testid="results-state">
              Не удалось выполнить запуск. Попробуйте ещё раз.
            </p>
          }
          @default {
            @if (current.compiled === false) {
              <p class="state state--bad" data-testid="results-state">Ошибка компиляции</p>
              @if (current.compileOutput) {
                <pre class="output" data-testid="compile-output">{{ current.compileOutput }}</pre>
              }
            } @else {
              <p class="state" [class.state--ok]="allPassed()" [class.state--bad]="!allPassed()" data-testid="results-state">
                @if (current.mode === 'SUBMIT' && calibration()) {
                  Разминка отправлена. В балл она входит с небольшим весом.
                } @else if (current.mode === 'SUBMIT') {
                  Решение отправлено. Скрытые тесты: пройдено {{ current.testsPassed }} из {{ current.testsTotal }}
                } @else {
                  Пройдено {{ current.testsPassed }} из {{ current.testsTotal }}
                }
                @if (duration(); as time) {
                  <span class="muted"> · {{ time }}</span>
                }
              </p>
              @if (current.tests.length) {
                <ul class="tests">
                  @for (test of current.tests; track test.name) {
                    <li [class]="'test test--' + test.status.toLowerCase()" data-testid="test">
                      <span class="test__mark" aria-hidden="true">{{ mark(test.status) }}</span>
                      <span class="test__name">{{ test.name }}</span>
                      @if (test.message) {
                        <pre class="test__message">{{ test.message }}</pre>
                      }
                    </li>
                  }
                </ul>
              }
              @if (current.output) {
                <details class="stdout">
                  <summary>Вывод программы</summary>
                  <pre class="output">{{ current.output }}</pre>
                </details>
              }
            }
          }
        }
      }
    </div>
  `,
})
export class ResultsPanel {
  readonly run = input<RunView | null>(null);
  /** Warm-up part 1: the panel shows the retyping check instead of test runs. */
  readonly retypingMode = input(false);
  readonly retyping = input<RetypingResult | null>(null);
  /** The warm-up: its submit has no hidden tests, so no counts are shown for it. */
  readonly calibration = input(false);

  protected readonly allPassed = computed(() => {
    const current = this.run();
    if (current?.mode === 'SUBMIT' && this.calibration()) {
      return true;
    }
    return !!current && current.testsTotal !== null && current.testsPassed === current.testsTotal;
  });

  protected readonly duration = computed(() => {
    const ms = this.run()?.durationMs;
    if (ms === null || ms === undefined) {
      return null;
    }
    return ms < 1000 ? `${ms} мс` : `${(ms / 1000).toFixed(1).replace('.', ',')} с`;
  });

  protected mark(status: string): string {
    return status === 'PASSED' ? '✓' : status === 'SKIPPED' ? '–' : '✗';
  }
}
