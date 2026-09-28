import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { ApiHealthService, ApiStatus } from '../../core/api-health.service';

const STATUS_LABELS: Record<ApiStatus, string> = {
  UP: 'работает',
  DOWN: 'сообщает о неисправности',
  UNREACHABLE: 'недоступен',
};

@Component({
  selector: 'app-home-page',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="home">
      <h1>GITS v1.0</h1>
      <p>Локальная демо-версия платформы оценки Java-разработчиков.</p>
      <p>
        <a class="btn btn--primary" routerLink="/employer" data-testid="employer-entry">Кабинет работодателя</a>
      </p>
      <p class="muted">Кандидат приходит по своей ссылке-приглашению.</p>
      <p class="status" data-testid="api-status">
        Сервер API:
        @if (status(); as current) {
          <span [class]="'status__value status__value--' + current.toLowerCase()">{{ label() }}</span>
        } @else {
          <span class="status__value">проверяется…</span>
        }
      </p>
    </section>
  `,
  styles: `
    .home { max-width: 720px; }
    h1 { margin: 0 0 8px; }
    .status__value { font-weight: 600; }
    .status__value--up { color: var(--ok); }
    .status__value--down, .status__value--unreachable { color: var(--danger); }
  `,
})
export class HomePage {
  protected readonly status = toSignal(inject(ApiHealthService).status());
  protected readonly label = computed(() => {
    const current = this.status();
    return current ? STATUS_LABELS[current] : '';
  });
}
