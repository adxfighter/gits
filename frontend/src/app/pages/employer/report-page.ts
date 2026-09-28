import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Observable, catchError, expand, filter, fromEvent, map, of, switchMap, take, timer } from 'rxjs';

import { EmployerApi } from '../../core/employer/employer-api.service';
import {
  LEVEL_LABELS,
  formatDateTime,
  formatDuration,
  formatScore,
  hiddenTestsText,
  indicatorLines,
  trustReasons,
} from '../../core/employer/employer-labels';
import { SessionReport, SessionStatus, TaskReport } from '../../core/employer/employer.models';
import { messageOf, statusOf } from '../../core/http/http-errors';
import { CodeViewer } from './code-viewer';
import { TrustBadge } from './trust-badge';

/** While the session runs or its solutions are being checked, the report reloads itself this often. */
export const REPORT_REFRESH_MS = 15_000;

const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  IN_PROGRESS: 'Идёт оценка',
  FINISHED: 'Завершена',
  EXPIRED: 'Завершена: время вышло',
};

interface TaskCard {
  task: TaskReport;
  heading: string;
  hiddenTests: string;
  indicators: ReturnType<typeof indicatorLines>;
  reasons: string[];
  excluded: string | null;
}

/** Report of one session (GET /api/employer/sessions/{id}/report): the score, then every task in detail. */
@Component({
  selector: 'app-report-page',
  imports: [RouterLink, CodeViewer, TrustBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <a class="back" routerLink="/employer">← Приглашения</a>
      @if (report(); as r) {
        <header class="page__header">
          <div>
            <h1 data-testid="report-candidate">{{ r.candidateLabel }}</h1>
            <p class="muted report__meta">
              Уровень {{ levelLabels[r.targetLevel] }} · {{ sessionStatus(r.status) }} · начало {{ date(r.startedAt) }}
              @if (r.finishedAt) {
                · окончание {{ date(r.finishedAt) }}
              }
            </p>
          </div>
        </header>

        <div class="score-card" data-testid="score-card">
          @if (r.preliminaryScore !== null) {
            <div class="score-card__value">
              <span class="score-card__number" data-testid="score">{{ score(r.preliminaryScore) }}</span>
              <span class="muted">из 100</span>
            </div>
            <div class="score-card__about">
              <p class="score-card__tag" data-testid="score-note">
                Предварительный балл, до психометрической калибровки
              </p>
              <p class="muted small">
                Доля пройденных скрытых тестов по задачам, взвешенная по их уровню; разминка не входит.
                Посчитан {{ date(r.scoreComputedAt) }}.
              </p>
            </div>
          } @else {
            <p data-testid="score-pending">
              @if (r.status === 'IN_PROGRESS') {
                Оценка ещё идёт. Балл и индикаторы появятся после её завершения.
              } @else {
                Балл считается: решения ещё проверяются. Страница обновится сама.
              }
            </p>
          }
          <div class="score-card__trust">
            <span class="muted small">Доверие</span>
            <app-trust-badge [level]="r.trustLevel" [empty]="r.preliminaryScore === null ? 'после расчёта' : '—'" />
          </div>
        </div>
        <p class="muted small">
          Индикаторы достоверности — экспериментальные правила v1.0: подсказка, где посмотреть запись сессии.
          Выводы о кандидате делает человек.
        </p>

        @for (card of cards(); track card.task.id) {
          <article class="task-card" data-testid="task-card">
            <header class="task-card__header">
              <div>
                <p class="task-card__kicker muted">{{ card.heading }}</p>
                <h2 class="task-card__title">{{ card.task.title }}</h2>
                <p class="muted small">{{ card.task.templateTitle }}</p>
              </div>
              @if (card.task.startedAt === null) {
                <button class="btn" type="button" disabled data-testid="replay">Воспроизвести сессию</button>
              } @else {
                <a class="btn btn--primary" [routerLink]="['/employer/replay', card.task.id]" data-testid="replay">
                  Воспроизвести сессию
                </a>
              }
            </header>

            <dl class="facts">
              <div><dt>Уровень</dt><dd>{{ levelLabels[card.task.level] }}</dd></div>
              <div><dt>Скрытые тесты</dt><dd data-testid="hidden-tests">{{ card.hiddenTests }}</dd></div>
              <div><dt>Время</dt><dd data-testid="duration">{{ duration(card.task) }}</dd></div>
              <div><dt>Запуски тестов</dt><dd data-testid="runs">{{ card.task.runs }}</dd></div>
              <div>
                <dt>Доверие</dt>
                <dd>
                  @if (card.task.kind === 'CALIBRATION') {
                    <span class="muted">не входит в оценку</span>
                  } @else {
                    <app-trust-badge [level]="card.task.trustLevel" />
                  }
                </dd>
              </div>
            </dl>
            <div class="competencies">
              <span class="muted small">Компетенции:</span>
              @for (title of card.task.competencyTitles; track $index) {
                <span class="chip" [title]="card.task.competencies[$index]" data-testid="competency">{{ title }}</span>
              }
            </div>
            @if (card.excluded) {
              <p class="notice" data-testid="excluded">Не вошла в балл: {{ card.excluded }}</p>
            }

            <h3 class="task-card__section">Индикаторы достоверности</h3>
            @if (card.task.indicators === null) {
              <p class="muted" data-testid="indicators-pending">
                Появятся после завершения сессии и проверки решений.
              </p>
            } @else {
              @if (card.reasons.length > 0) {
                <ul class="reasons" data-testid="trust-reasons">
                  @for (reason of card.reasons; track $index) {
                    <li>{{ reason }}</li>
                  }
                </ul>
              } @else if (card.task.kind === 'TASK') {
                <p class="muted" data-testid="trust-reasons">Ни одно правило не сработало.</p>
              }
              <table class="table indicators" data-testid="indicators">
                <tbody>
                  @for (line of card.indicators; track line.name) {
                    <tr [attr.data-indicator]="line.name">
                      <th scope="row">{{ line.label }}</th>
                      <td>{{ line.explanation }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }

            <details class="code" open>
              <summary class="task-card__section">Итоговый код</summary>
              <p class="muted small">
                {{ card.task.submitStatus ? 'Код, отправленный на проверку.' : 'Последний сохранённый код: решение не отправлено.' }}
              </p>
              <app-code-viewer [files]="card.task.finalCode" />
            </details>
          </article>
        }
      } @else if (notFound()) {
        <div class="state" data-testid="report-not-found">
          <p>Сессия не найдена.</p>
          <a class="btn" routerLink="/employer">К приглашениям</a>
        </div>
      } @else if (error(); as text) {
        <div class="state" role="alert" data-testid="report-error">
          <p class="error">{{ text }}</p>
          <button class="btn" type="button" (click)="retry()">Повторить</button>
        </div>
      } @else {
        <p class="muted" role="status" data-testid="report-loading">Загружаем отчёт…</p>
      }
    </section>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 12px; color: var(--accent); text-decoration: none; }
    .back:hover { text-decoration: underline; }
    .report__meta { margin: 4px 0 0; }
    .small { font-size: 13px; }
    .score-card {
      display: flex; align-items: center; gap: 32px; flex-wrap: wrap;
      padding: 20px 24px; background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
    }
    .score-card__value { display: flex; align-items: baseline; gap: 8px; }
    .score-card__number { font-size: 44px; font-weight: 700; line-height: 1; }
    .score-card__about { flex: 1; min-width: 280px; }
    .score-card__about p { margin: 0 0 4px; }
    .score-card__tag { font-weight: 600; color: var(--warn); }
    .score-card__trust { display: flex; flex-direction: column; gap: 4px; }
    .task-card {
      margin-top: 20px; padding: 20px 24px;
      background: var(--surface); border: 1px solid var(--border); border-radius: 8px;
    }
    .task-card__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
    .task-card__kicker { margin: 0; font-size: 13px; text-transform: uppercase; letter-spacing: 0.04em; }
    .task-card__title { margin: 2px 0; font-size: 20px; }
    .task-card__header p { margin: 0; }
    .task-card__section { margin: 20px 0 8px; font-size: 16px; font-weight: 600; }
    summary.task-card__section { cursor: pointer; }
    .facts { display: flex; flex-wrap: wrap; gap: 8px 32px; margin: 16px 0 12px; }
    .facts dt { color: var(--text-muted); font-size: 13px; }
    .facts dd { margin: 0; font-weight: 600; }
    .competencies { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .chip { padding: 2px 8px; font-size: 13px; background: var(--bg); border: 1px solid var(--border); border-radius: 999px; }
    .reasons { margin: 0 0 12px; padding-left: 20px; }
    .indicators th { width: 240px; text-align: left; font-weight: 600; vertical-align: top; }
  `,
})
export class ReportPage {
  /** Route parameter :id, the session. */
  readonly id = input.required<string>();

  private readonly api = inject(EmployerApi);

  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly report = signal<SessionReport | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);
  private readonly attempt = signal(0);

  protected readonly cards = computed<TaskCard[]>(() => {
    const report = this.report();
    if (!report) {
      return [];
    }
    let number = 0;
    const excluded = new Map<string, string>();
    for (const score of report.scorePerTask?.tasks ?? []) {
      if (score.excluded) {
        excluded.set(score.sessionTaskId, score.excluded);
      }
    }
    return report.tasks.map((task) => ({
      task,
      heading: task.kind === 'CALIBRATION' ? 'Разминка · не оценивается' : `Задача ${++number}`,
      hiddenTests: hiddenTestsText(task, excluded.has(task.id)),
      indicators: indicatorLines(task.indicators),
      reasons: task.kind === 'CALIBRATION' ? [] : trustReasons(task.indicators),
      excluded: excluded.get(task.id) ?? null,
    }));
  });

  constructor() {
    // load, then reload while the result is not final: the session runs or its solutions are being checked
    toObservable(computed(() => ({ id: this.id(), attempt: this.attempt() })))
      .pipe(
        switchMap(({ id }) => {
          this.report.set(null);
          this.error.set(null);
          this.notFound.set(false);
          const load = () =>
            this.api.report(id).pipe(
              map((report) => ({ report, error: null as unknown })),
              catchError((error: unknown) => of({ report: null, error })),
            );
          return load().pipe(
            // a failed reload keeps the last report and keeps trying
            expand((result) =>
              (result.report ?? this.report())?.preliminaryScore === null
                ? timer(REPORT_REFRESH_MS).pipe(switchMap(whenVisible), switchMap(load))
                : EMPTY,
            ),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ report, error }) => {
        if (report) {
          this.report.set(report);
        } else if (this.report() === null) {
          // a failed reload keeps the report on the screen; only the first load shows the error
          this.notFound.set(statusOf(error) === 404);
          this.error.set(messageOf(error, 'Не удалось загрузить отчёт.'));
        }
      });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected sessionStatus(status: SessionStatus): string {
    return SESSION_STATUS_LABELS[status];
  }

  protected date(iso: string | null): string {
    return formatDateTime(iso);
  }

  protected score(value: number): string {
    return formatScore(value);
  }

  protected duration(task: TaskReport): string {
    if (task.durationSeconds !== null) {
      return formatDuration(task.durationSeconds);
    }
    // a task never opened may still be submitted: the finish of the session sends it as it is
    return task.startedAt === null ? 'не открыта' : 'не отправлена';
  }
}

/** Emits at once when the tab is visible, otherwise as soon as it becomes visible. */
function whenVisible(): Observable<unknown> {
  return document.visibilityState === 'visible'
    ? of(true)
    : fromEvent(document, 'visibilitychange').pipe(
        filter(() => document.visibilityState === 'visible'),
        take(1),
      );
}
