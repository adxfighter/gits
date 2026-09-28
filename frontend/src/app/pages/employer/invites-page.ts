import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, filter, interval, map, merge, of, switchMap } from 'rxjs';

import { EmployerApi } from '../../core/employer/employer-api.service';
import {
  INVITE_STATUSES,
  INVITE_STATUS_LABELS,
  LEVEL_LABELS,
  formatDateTime,
  formatScore,
  inviteStatusText,
} from '../../core/employer/employer-labels';
import { InviteRow, InviteStatus } from '../../core/employer/employer.models';
import { messageOf } from '../../core/http/http-errors';
import { ConfirmDialog } from '../candidate/session/confirm-dialog';
import { InviteDialog } from './invite-dialog';
import { TrustBadge } from './trust-badge';

/** How often the list refreshes itself while the tab is visible: a candidate may be finishing right now. */
export const INVITES_REFRESH_MS = 20_000;

/** The employer's invites with results (GET /api/employer/invites), filter by status, new invite, revoke. */
@Component({
  selector: 'app-invites-page',
  imports: [RouterLink, InviteDialog, ConfirmDialog, TrustBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <h1>Приглашения</h1>
        <div class="page__actions">
          <label class="filter">
            <span class="filter__label">Статус</span>
            <select class="input" [value]="status() ?? ''" (change)="setStatus($event)" data-testid="status-filter">
              <option value="">Все</option>
              @for (option of statuses; track option) {
                <option [value]="option">{{ statusLabels[option] }}</option>
              }
            </select>
          </label>
          <button class="btn btn--primary" type="button" (click)="inviting.set(true)" data-testid="invite-open">
            Пригласить кандидата
          </button>
        </div>
      </header>

      @if (loading()) {
        <p class="muted" data-testid="invites-loading">Загружаем приглашения…</p>
      } @else if (rows() === null) {
        <div class="state" role="alert" data-testid="invites-error">
          <p class="error">{{ error() }}</p>
          <button class="btn" type="button" (click)="refresh()">Повторить</button>
        </div>
      } @else if (rows()!.length === 0) {
        <div class="state" data-testid="invites-empty">
          @if (status(); as current) {
            <p>Нет приглашений со статусом «{{ statusLabels[current] }}».</p>
            <button class="btn" type="button" (click)="status.set(null)">Показать все</button>
          } @else {
            <p>Приглашений пока нет.</p>
            <p class="muted">Нажмите «Пригласить кандидата», чтобы получить ссылку на оценку.</p>
          }
        </div>
      } @else {
        @if (actionError(); as text) {
          <p class="error" role="alert" data-testid="invites-action-error">{{ text }}</p>
        }
        @if (error(); as text) {
          <p class="error" role="status" data-testid="invites-stale">{{ text }} Показан последний загруженный список.</p>
        }
        <div class="table-wrap">
          <table class="table" data-testid="invites-table">
            <thead>
              <tr>
                <th scope="col">Кандидат</th>
                <th scope="col">Уровень</th>
                <th scope="col">Статус</th>
                <th scope="col">Дата</th>
                <th scope="col" class="num">Балл</th>
                <th scope="col">Доверие</th>
                <th scope="col"><span class="visually-hidden">Действия</span></th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.id) {
                <tr data-testid="invite-row">
                  <td class="table__label">
                    @if (row.sessionId) {
                      <a [routerLink]="['/employer/sessions', row.sessionId]">{{ row.candidateLabel }}</a>
                    } @else {
                      {{ row.candidateLabel }}
                    }
                  </td>
                  <td>{{ levelLabels[row.targetLevel] }}</td>
                  <td data-testid="invite-status">{{ statusText(row) }}</td>
                  <td>
                    <div>{{ date(row.createdAt) }}</div>
                    @if (row.finishedAt) {
                      <div class="muted small">завершено {{ date(row.finishedAt) }}</div>
                    }
                  </td>
                  <td class="num" data-testid="invite-score">{{ score(row) }}</td>
                  <td><app-trust-badge [level]="row.trustLevel" [empty]="row.status === 'COMPLETED' ? 'считается…' : '—'" /></td>
                  <td class="table__actions">
                    @if (row.sessionId) {
                      <a class="btn" [routerLink]="['/employer/sessions', row.sessionId]" data-testid="invite-report">Отчёт</a>
                    }
                    @if (row.status === 'CREATED') {
                      <button class="btn btn--ghost" type="button" (click)="revoking.set(row)" data-testid="invite-revoke">
                        Отозвать
                      </button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <p class="muted small">
          Балл предварительный, до психометрической калибровки. Доверие — подсказка, где посмотреть запись сессии;
          выводы о кандидате делает человек.
        </p>
      }
    </section>

    <app-invite-dialog [open]="inviting()" (closed)="invited($event.created)" />
    <app-confirm-dialog
      [open]="revoking() !== null"
      title="Отозвать приглашение?"
      [text]="'Ссылка для «' + (revoking()?.candidateLabel ?? '') + '» перестанет работать. Это нельзя отменить.'"
      confirmLabel="Отозвать"
      (confirmed)="revoke()"
      (cancelled)="revoking.set(null)"
    />
  `,
  styles: `
    .filter { display: flex; align-items: center; gap: 8px; }
    .filter__label { color: var(--text-muted); font-size: 14px; }
    .small { font-size: 13px; }
  `,
})
export class InvitesPage {
  private readonly api = inject(EmployerApi);
  private readonly reload = new Subject<void>();

  protected readonly statuses = INVITE_STATUSES;
  protected readonly statusLabels = INVITE_STATUS_LABELS;
  protected readonly levelLabels = LEVEL_LABELS;

  protected readonly status = signal<InviteStatus | null>(null);
  /** null until the first list arrives (or when it could not be loaded at all). */
  protected readonly rows = signal<InviteRow[] | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  /** A failed revoke; it stays until the next action, while refreshes come and go. */
  protected readonly actionError = signal<string | null>(null);
  protected readonly inviting = signal(false);
  protected readonly revoking = signal<InviteRow | null>(null);

  constructor() {
    // a new filter shows the loading state; a refresh keeps the list on the screen
    merge(
      toObservable(this.status).pipe(map(() => true)),
      this.reload.pipe(map(() => false)),
      interval(INVITES_REFRESH_MS).pipe(
        filter(() => document.visibilityState === 'visible'),
        map(() => false),
      ),
    )
      .pipe(
        switchMap((reset) => {
          if (reset) {
            this.loading.set(true);
            this.rows.set(null);
          }
          return this.api.invites(this.status()).pipe(
            map((rows) => ({ rows, error: null as unknown })),
            catchError((error: unknown) => of({ rows: null, error })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ rows, error }) => {
        this.loading.set(false);
        if (rows) {
          this.rows.set(rows);
          this.error.set(null);
        } else {
          this.error.set(messageOf(error, 'Не удалось загрузить приглашения.'));
        }
      });
  }

  protected setStatus(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.status.set(value ? (value as InviteStatus) : null);
  }

  protected refresh(): void {
    this.reload.next();
  }

  protected invited(created: boolean): void {
    this.inviting.set(false);
    this.actionError.set(null);
    if (created) {
      this.refresh();
    }
  }

  protected revoke(): void {
    const row = this.revoking();
    this.revoking.set(null);
    this.actionError.set(null);
    if (!row) {
      return;
    }
    this.api.revokeInvite(row.id).subscribe({
      next: () => this.refresh(),
      error: (error: unknown) => {
        this.actionError.set(messageOf(error, 'Не удалось отозвать приглашение.'));
        this.refresh();
      },
    });
  }

  protected statusText(row: InviteRow): string {
    return inviteStatusText(row);
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  protected score(row: InviteRow): string {
    if (row.preliminaryScore === null) {
      return row.status === 'COMPLETED' ? 'считается…' : '—';
    }
    return formatScore(row.preliminaryScore);
  }
}
