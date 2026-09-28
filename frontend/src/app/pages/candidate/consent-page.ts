import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CandidateApi } from '../../core/candidate/candidate-api.service';
import { messageOf } from '../../core/http/http-errors';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';

/** /c/consent — the consent text; nothing further opens until it is accepted. */
@Component({
  selector: 'app-consent-page',
  imports: [FormsModule, MarkdownPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card card--wide">
      @if (consent(); as view) {
        <article class="prose" [innerHTML]="view.text | markdown"></article>
        <label class="check">
          <input type="checkbox" [(ngModel)]="agreed" data-testid="consent-checkbox" />
          Я прочитал(а) текст и согласен(на) на обработку данных на этих условиях
        </label>
        @if (error(); as message) {
          <p class="error">{{ message }}</p>
        }
        <button class="btn btn--primary" type="button" [disabled]="!agreed() || sending()" (click)="accept()">
          Согласен и начинаю
        </button>
      } @else {
        <p class="muted">Загружаем текст согласия…</p>
      }
    </section>
  `,
})
export class ConsentPage {
  private readonly api = inject(CandidateApi);
  private readonly router = inject(Router);

  protected readonly consent = toSignal(this.api.consent());
  protected readonly agreed = signal(false);
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);

  protected async accept(): Promise<void> {
    this.sending.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.acceptConsent());
      await this.router.navigateByUrl('/c/intro');
    } catch (error) {
      this.error.set(messageOf(error));
    } finally {
      this.sending.set(false);
    }
  }
}
