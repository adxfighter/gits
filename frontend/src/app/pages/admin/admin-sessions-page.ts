import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { AdminApi } from '../../core/admin/admin-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { AdminSessionRow } from '../../core/admin/admin.models';
import { LEVEL_LABELS, formatDateTime, formatScore } from '../../core/employer/employer-labels';
import { SessionStatus } from '../../core/employer/employer.models';
import { messageOf, statusOf } from '../../core/http/http-errors';
import { TrustBadge } from '../employer/trust-badge';

const STATUS_LABELS: Record<SessionStatus, string> = {
  IN_PROGRESS: 'Идёт',
  FINISHED: 'Завершена',
  EXPIRED: 'Время вышло',
};

/**
 * Sessions of every company (GET /api/admin/sessions) with recalculation of indicators and score, and the research
 * export for a period (GET /api/admin/export).
 */
@Component({
  selector: 'app-admin-sessions-page',
  imports: [TrustBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <h1>Сессии</h1>
      </header>

      <article class="export" data-testid="export">
        <h2 class="export__title">Выгрузка для исследования</h2>
        <p class="muted small">
          Архив ZIP: сессии, задания, запуски, телеметрия и индикаторы в JSONL, описание полей — README внутри.
          Вместо сессий, заданий и компаний — случайные идентификаторы этого архива; меток кандидатов, email, IP и
          user agent в нём нет. Период — дни начала сессий по UTC, включительно; пустое поле — без ограничения.
        </p>
        <div class="export__form">
          <label class="field">
            <span class="field__label">С</span>
            <input class="input" type="date" [value]="from()" (change)="from.set(value($event))" data-testid="export-from" />
          </label>
          <label class="field">
            <span class="field__label">По</span>
            <input class="input" type="date" [value]="to()" (change)="to.set(value($event))" data-testid="export-to" />
          </label>
          @if (periodError()) {
            <p class="error" role="alert" data-testid="export-error">{{ periodError() }}</p>
          } @else {
            <button class="btn btn--primary" type="button" [disabled]="checking()" (click)="download()" data-testid="export-download">
              Скачать архив
            </button>
          }
        </div>
      </article>

      <!-- one live region for the results of recalculation: screen readers announce its changes -->
      <p class="visually-hidden" role="status" data-testid="rescore-status">{{ lastMessage() }}</p>
      @if (staleError(); as text) {
        <p class="error" role="alert" data-testid="sessions-stale">{{ text }} Показан последний загруженный список.</p>
      }
      @if (rows(); as list) {
        @if (list.length === 0) {
          <div class="state" data-testid="sessions-empty"><p>Сессий пока нет.</p></div>
        } @else {
          <div class="table-wrap">
            <table class="table" data-testid="sessions-table">
              <thead>
                <tr>
                  <th scope="col">Компания</th>
                  <th scope="col">Кандидат</th>
                  <th scope="col">Уровень</th>
                  <th scope="col">Статус</th>
                  <th scope="col">Начало</th>
                  <th scope="col" class="num">Балл</th>
                  <th scope="col">Доверие</th>
                  <th scope="col"><span class="visually-hidden">Действия</span></th>
                </tr>
              </thead>
              <tbody>
                @for (row of list; track row.sessionId) {
                  <tr data-testid="session-row">
                    <td>{{ row.companyName }}</td>
                    <td class="table__label">{{ row.candidateLabel }}</td>
                    <td>{{ levelLabels[row.targetLevel] }}</td>
                    <td>{{ statusLabels[row.status] }}</td>
                    <td>{{ date(row.startedAt) }}</td>
                    <td class="num" data-testid="session-score">{{ score(row.preliminaryScore) }}</td>
                    <td><app-trust-badge [level]="row.trustLevel" /></td>
                    <td class="table__actions">
                      @if (row.status !== 'IN_PROGRESS') {
                        <button
                          class="btn"
                          type="button"
                          [disabled]="busy() === row.sessionId"
                          (click)="rescore(row)"
                          data-testid="rescore"
                        >
                          {{ busy() === row.sessionId ? 'Считаем…' : 'Пересчитать' }}
                        </button>
                      }
                    </td>
                  </tr>
                  @if (messages()[row.sessionId]; as message) {
                    <tr class="message-row">
                      <td colspan="8" [class.error]="message.error" data-testid="rescore-message">
                        {{ message.text }}
                      </td>
                    </tr>
                  }
                }
              </tbody>
            </table>
          </div>
        }
      } @else if (error(); as text) {
        <div class="state" role="alert" data-testid="sessions-error">
          <p class="error">{{ text }}</p>
          <button class="btn" type="button" (click)="reload()">Повторить</button>
        </div>
      } @else {
        <p class="muted" role="status" data-testid="sessions-loading">Загружаем сессии…</p>
      }
    </section>
  `,
  styles: `
    .small { font-size: 13px; }
    .export { margin-bottom: 20px; padding: 16px 20px; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
    .export__title { margin: 0 0 4px; font-size: 16px; }
    .export__form { display: flex; align-items: flex-end; gap: 12px; flex-wrap: wrap; }
    .message-row td { padding-top: 0; font-size: 13px; }
  `,
})
export class AdminSessionsPage {
  private readonly api = inject(AdminApi);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly attempt = signal(0);

  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly rows = signal<AdminSessionRow[] | null>(null);
  protected readonly error = signal<string | null>(null);
  /** A reload that failed while a list is on the screen. */
  protected readonly staleError = signal<string | null>(null);
  protected readonly lastMessage = signal('');
  protected readonly checking = signal(false);
  protected readonly busy = signal<string | null>(null);
  protected readonly messages = signal<Record<string, { text: string; error: boolean }>>({});
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly periodError = computed(() =>
    this.from() && this.to() && this.from() > this.to() ? 'Начало периода позже его конца.' : null,
  );
  protected readonly exportUrl = computed(() => this.api.exportUrl(this.from() || null, this.to() || null));

  constructor() {
    toObservable(this.attempt)
      .pipe(
        switchMap(() => {
          this.error.set(null);
          return this.api.sessions().pipe(
            map((rows) => ({ rows, error: null as unknown })),
            catchError((error: unknown) => of({ rows: null, error })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ rows, error }) => {
        if (rows) {
          this.rows.set(rows);
          this.staleError.set(null);
        } else if (this.rows() === null) {
          this.error.set(messageOf(error, 'Не удалось загрузить сессии.'));
        } else {
          this.staleError.set(messageOf(error, 'Не удалось обновить список сессий.'));
        }
      });
  }

  protected reload(): void {
    this.attempt.update((n) => n + 1);
  }

  protected rescore(row: AdminSessionRow): void {
    this.busy.set(row.sessionId);
    this.api.rescore(row.sessionId).subscribe({
      next: (result) => {
        this.busy.set(null);
        this.message(row.sessionId, `Пересчитано: балл ${formatScore(result.preliminaryScore)}.`, false);
        this.reload();
      },
      error: (error: unknown) => {
        this.busy.set(null);
        this.message(
          row.sessionId,
          statusOf(error) === 409
            ? 'Пересчитать нельзя: сессия ещё идёт или не все решения проверены.'
            : messageOf(error, 'Не удалось пересчитать.'),
          true,
        );
      },
    });
  }

  /**
   * The archive is downloaded by the browser itself (a file, not read into the page). A plain link would save an
   * error page as the file when the session has ended: the session is checked first.
   */
  protected download(): void {
    const url = this.exportUrl();
    this.checking.set(true);
    this.auth.me().subscribe({
      next: (user) => {
        this.checking.set(false);
        if (user?.role === 'ADMIN') {
          startDownload(url);
        } else {
          void this.router.navigate(['/login'], { queryParams: { returnUrl: '/admin/sessions', reason: 'expired' } });
        }
      },
      error: () => {
        this.checking.set(false);
        this.staleError.set('Нет связи с сервером: архив не скачан.');
      },
    });
  }

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  protected score(value: number | null): string {
    return formatScore(value);
  }

  private message(sessionId: string, text: string, error: boolean): void {
    this.messages.update((all) => ({ ...all, [sessionId]: { text, error } }));
    this.lastMessage.set(text);
  }
}

/** Opens the export address: the answer is an attachment, so the page stays where it is. */
export function startDownload(url: string): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = '';
  document.body.appendChild(link);
  link.click();
  link.remove();
}
