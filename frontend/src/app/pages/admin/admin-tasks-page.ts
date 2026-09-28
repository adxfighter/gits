import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { AdminApi } from '../../core/admin/admin-api.service';
import { VARIANT_STATUS_LABELS, checkLabel } from '../../core/admin/admin-labels';
import { TemplateRow } from '../../core/admin/admin.models';
import { LEVEL_LABELS } from '../../core/employer/employer-labels';
import { messageOf } from '../../core/http/http-errors';

/** The task bank for the administrator (GET /api/admin/tasks): templates, variants, validation, issues. */
@Component({
  selector: 'app-admin-tasks-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <header class="page__header">
        <div>
          <h1>Банк задач</h1>
          @if (templates(); as list) {
            <p class="muted summary" data-testid="bank-summary">
              Шаблонов: {{ list.length }} · вариантов: {{ variantCount() }} · проверено: {{ validatedCount() }}
            </p>
          }
        </div>
      </header>
      @if (templates(); as list) {
        @if (list.length === 0) {
          <div class="state" data-testid="bank-empty">
            <p>Банк задач пуст.</p>
            <p class="muted">Он загружается при старте api из каталога TASKBANK_PATH.</p>
          </div>
        }
        @for (template of list; track template.code) {
          <article class="bank-template" data-testid="template">
            <h2 class="bank-template__title">
              <span class="bank-template__code">{{ template.code }}</span> {{ template.title }}
            </h2>
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">Вариант</th>
                    <th scope="col">Уровень</th>
                    <th scope="col">Статус</th>
                    <th scope="col">Валидация</th>
                    <th scope="col" class="num">Решение, мс</th>
                    <th scope="col" class="num">Выдач</th>
                  </tr>
                </thead>
                <tbody>
                  @for (variant of template.variants; track variant.id) {
                    <tr data-testid="variant-row">
                      <td class="table__label">
                        <a [routerLink]="['/admin/tasks', variant.id]">{{ variant.code }}</a>
                        @if (variant.kind === 'CALIBRATION') {
                          <span class="muted"> · разминка</span>
                        }
                      </td>
                      <td>{{ levelLabels[variant.level] }}</td>
                      <td>{{ statusLabels[variant.status] }}</td>
                      <td data-testid="validation">
                        @if (variant.validationPassed) {
                          <span class="ok">✓ все проверки пройдены</span>
                        } @else if (variant.failedChecks.length > 0) {
                          <span class="error">✗ {{ failed(variant.failedChecks) }}</span>
                        } @else {
                          <span class="muted">нет отчёта</span>
                        }
                      </td>
                      <td class="num">{{ variant.referenceMaxMs ?? '—' }}</td>
                      <td class="num" data-testid="issued">{{ variant.issued }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </article>
        }
      } @else if (error(); as text) {
        <div class="state" role="alert" data-testid="bank-error">
          <p class="error">{{ text }}</p>
          <button class="btn" type="button" (click)="retry()">Повторить</button>
        </div>
      } @else {
        <p class="muted" role="status" data-testid="bank-loading">Загружаем банк задач…</p>
      }
    </section>
  `,
  styles: `
    .summary { margin: 4px 0 0; }
    .bank-template { margin-bottom: 20px; }
    .bank-template__title { margin: 0 0 8px; font-size: 17px; }
    .bank-template__code { font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace; color: var(--text-muted); }
    .ok { color: var(--ok); }
  `,
})
export class AdminTasksPage {
  private readonly api = inject(AdminApi);
  private readonly attempt = signal(0);

  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly statusLabels = VARIANT_STATUS_LABELS;
  protected readonly templates = signal<TemplateRow[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly variantCount = computed(() =>
    (this.templates() ?? []).reduce((sum, template) => sum + template.variants.length, 0),
  );
  protected readonly validatedCount = computed(() =>
    (this.templates() ?? []).reduce(
      (sum, template) => sum + template.variants.filter((variant) => variant.status === 'VALIDATED').length,
      0,
    ),
  );

  constructor() {
    toObservable(this.attempt)
      .pipe(
        switchMap(() => {
          this.templates.set(null);
          this.error.set(null);
          return this.api.tasks().pipe(
            map((list) => ({ list, error: null as unknown })),
            catchError((error: unknown) => of({ list: null, error })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ list, error }) => {
        if (list) {
          this.templates.set(list);
        } else {
          this.error.set(messageOf(error, 'Не удалось загрузить банк задач.'));
        }
      });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected failed(checks: string[]): string {
    return checks.map(checkLabel).join(', ');
  }
}
