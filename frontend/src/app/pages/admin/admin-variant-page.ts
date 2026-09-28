import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { AdminApi } from '../../core/admin/admin-api.service';
import { FILE_KIND_LABELS, SECRET_KINDS, VARIANT_STATUS_LABELS, checkLabel } from '../../core/admin/admin-labels';
import { VariantDetails } from '../../core/admin/admin.models';
import { LEVEL_LABELS } from '../../core/employer/employer-labels';
import { messageOf, statusOf } from '../../core/http/http-errors';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';
import { CodeViewer } from '../employer/code-viewer';

/** One variant of the bank for the administrator: statement, validation and every file, the secret ones too. */
@Component({
  selector: 'app-admin-variant-page',
  imports: [RouterLink, CodeViewer, MarkdownPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="page">
      <a class="back" routerLink="/admin/tasks">← Банк задач</a>
      @if (variant(); as v) {
        <header class="page__header">
          <div>
            <h1 data-testid="variant-code">{{ v.code }}</h1>
            <p class="muted meta">
              {{ v.templateCode }} · {{ v.templateTitle }} · {{ levelLabels[v.level] }} · {{ statusLabels[v.status] }}
              · {{ v.timeLimitMin }} мин · выдач: {{ v.issued }}
              @if (v.kind === 'CALIBRATION') {
                · разминка
              }
            </p>
          </div>
        </header>
        <div class="variant-grid">
          <article class="variant-card">
            <h2 class="variant-card__title">Условие</h2>
            <div class="prose" data-testid="statement" [innerHTML]="v.statementMd | markdown"></div>
          </article>
          <article class="variant-card">
            <h2 class="variant-card__title">Валидация</h2>
            @if (v.validationReport?.checks; as checks) {
              <ul class="checks" data-testid="checks">
                @for (check of checks; track check.id) {
                  <li [class.error]="!check.passed">
                    {{ check.passed ? '✓' : '✗' }} <strong>{{ label(check.id) }}.</strong> {{ check.details }}
                  </li>
                }
              </ul>
            } @else {
              <p class="muted">Отчёта валидации нет.</p>
            }
          </article>
        </div>
        <article class="variant-card">
          <h2 class="variant-card__title">Файлы</h2>
          <p class="notice" data-testid="secret-notice">
            Решение и скрытые тесты видит только администратор: кандидату и работодателю они не показываются.
          </p>
          <app-code-viewer [files]="files()" [labels]="labels()" />
        </article>
      } @else if (notFound()) {
        <div class="state" data-testid="variant-not-found"><p>Вариант не найден.</p></div>
      } @else if (error(); as text) {
        <div class="state" role="alert"><p class="error">{{ text }}</p></div>
      } @else {
        <p class="muted" role="status">Загружаем вариант…</p>
      }
    </section>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 12px; color: var(--accent); text-decoration: none; }
    .meta { margin: 4px 0 0; }
    .variant-grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 16px; margin-bottom: 16px; }
    .variant-card { padding: 16px 20px; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; min-width: 0; }
    .variant-card__title { margin: 0 0 8px; font-size: 16px; }
    .checks { margin: 0; padding-left: 0; list-style: none; font-size: 14px; display: grid; gap: 4px; }
  `,
})
export class AdminVariantPage {
  /** Route parameter: the variant. */
  readonly variantId = input.required<string>();

  private readonly api = inject(AdminApi);

  protected readonly levelLabels = LEVEL_LABELS;
  protected readonly statusLabels = VARIANT_STATUS_LABELS;
  protected readonly variant = signal<VariantDetails | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);

  /** Files keyed by kind and path: the solution often has the path of the starter. */
  protected readonly files = computed(() =>
    Object.fromEntries((this.variant()?.files ?? []).map((file) => [`${file.kind}:${file.path}`, file.content])),
  );
  protected readonly labels = computed(() =>
    Object.fromEntries(
      (this.variant()?.files ?? []).map((file) => [
        `${file.kind}:${file.path}`,
        `${SECRET_KINDS.includes(file.kind) ? '🔒 ' : ''}${file.path.slice(file.path.lastIndexOf('/') + 1)} · ${FILE_KIND_LABELS[file.kind]}`,
      ]),
    ),
  );

  constructor() {
    toObservable(this.variantId)
      .pipe(
        switchMap((id) => {
          this.variant.set(null);
          this.error.set(null);
          this.notFound.set(false);
          return this.api.variant(id).pipe(
            map((variant) => ({ variant, error: null as unknown })),
            catchError((error: unknown) => of({ variant: null, error })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ variant, error }) => {
        if (variant) {
          this.variant.set(variant);
        } else {
          this.notFound.set(statusOf(error) === 404);
          this.error.set(messageOf(error, 'Не удалось загрузить вариант.'));
        }
      });
  }

  protected label(id: string): string {
    return checkLabel(id);
  }
}
