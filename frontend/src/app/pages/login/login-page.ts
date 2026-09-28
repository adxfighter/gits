import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService, UserRole } from '../../core/auth/auth.service';
import { HOME_OF } from '../../core/auth/employer.guard';
import { messageOf, statusOf } from '../../core/http/http-errors';

/** Sign-in of an employer: email and password (docs/api.md, POST /auth/login). */
@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card login">
      <h1>Вход</h1>
      <p class="muted">Для работодателей и администратора платформы.</p>
      @if (notice(); as text) {
        <p class="login__notice" data-testid="login-notice">{{ text }}</p>
      }
      <form (submit)="submit($event)" novalidate>
        <label class="field">
          <span class="field__label">Email</span>
          <input
            class="input"
            type="email"
            name="email"
            autocomplete="username"
            required
            [value]="email()"
            (input)="email.set(value($event))"
            data-testid="login-email"
          />
        </label>
        <label class="field">
          <span class="field__label">Пароль</span>
          <input
            class="input"
            type="password"
            name="password"
            autocomplete="current-password"
            required
            [value]="password()"
            (input)="password.set(value($event))"
            data-testid="login-password"
          />
        </label>
        @if (error(); as text) {
          <p class="error" role="alert" data-testid="login-error">{{ text }}</p>
        }
        <button class="btn btn--primary" type="submit" [disabled]="busy()" data-testid="login-submit">
          {{ busy() ? 'Входим…' : 'Войти' }}
        </button>
      </form>
    </section>
  `,
  styles: `
    .login { max-width: 400px; }
    .login__notice { padding: 8px 12px; border: 1px solid var(--border); border-radius: 6px; background: var(--bg); }
    form { display: flex; flex-direction: column; gap: 14px; align-items: stretch; }
    form .btn { align-self: flex-start; }
  `,
})
export class LoginPage {
  /** Where to go after signing in (query parameter set by the guard). */
  readonly returnUrl = input<string>();
  /** Why the visitor is here: `expired` (the session ended) or `offline`. */
  readonly reason = input<string>();

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly notice = computed(() => {
    switch (this.reason()) {
      case 'expired':
        return 'Сессия закончилась. Войдите снова.';
      case 'offline':
        return 'Не удалось связаться с сервером. Проверьте подключение и войдите ещё раз.';
      default:
        return null;
    }
  });

  protected value(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const email = this.email().trim();
    if (!email || !this.password()) {
      this.error.set('Введите email и пароль.');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(email, this.password()).subscribe({
      next: (user) => {
        this.busy.set(false);
        void this.router.navigateByUrl(safeReturnUrl(this.returnUrl(), user.role));
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(
          statusOf(error) === 429
            ? 'Слишком много попыток входа. Подождите минуту и попробуйте снова.'
            : messageOf(error, 'Не удалось войти. Попробуйте ещё раз.'),
        );
      },
    });
  }
}

/**
 * Only a page of the user's own section: an address from the query string must not lead to another site, nor to the
 * section of the other role.
 */
export function safeReturnUrl(url: string | undefined, role: UserRole): string {
  const home = HOME_OF[role];
  const section = role === 'ADMIN' ? '/admin' : '/employer';
  return url && (url === section || url.startsWith(section + '/') || url.startsWith(section + '?')) ? url : home;
}
