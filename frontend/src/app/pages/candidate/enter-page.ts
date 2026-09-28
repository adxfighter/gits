import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CandidateApi } from '../../core/candidate/candidate-api.service';
import { messageOf, statusOf } from '../../core/http/http-errors';

/** /c/:token — entry by the invite link. The token leaves the address bar as soon as it is used. */
@Component({
  selector: 'app-enter-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card">
      @if (error(); as message) {
        <h1>Не удалось открыть оценку</h1>
        <p class="error" data-testid="enter-error">{{ message }}</p>
        <p class="muted">Если вы считаете, что это ошибка, свяжитесь с работодателем, который прислал ссылку.</p>
      } @else {
        <h1>Открываем оценку…</h1>
        <p class="muted">Проверяем ссылку-приглашение.</p>
      }
    </section>
  `,
})
export class EnterPage implements OnInit {
  readonly token = input.required<string>();

  private readonly api = inject(CandidateApi);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  protected readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const me = await firstValueFrom(this.api.enter(this.token()));
      await this.router.navigateByUrl(me.consentGiven ? '/c/intro' : '/c/consent', { replaceUrl: true });
    } catch (error) {
      // the token does not stay in the address bar and history, whatever the answer
      this.location.replaceState('/c');
      this.error.set(
        statusOf(error) === 404
          ? 'Ссылка недействительна. Проверьте, что она скопирована целиком.'
          : statusOf(error) === 429
            ? 'Слишком много попыток входа. Подождите минуту и обновите страницу.'
            : messageOf(error),
      );
    }
  }
}
