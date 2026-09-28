import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AuthService } from './core/auth/auth.service';

interface PageState {
  fullscreen: boolean;
  employer: boolean;
}

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!page().fullscreen) {
      <header class="app-header">
        <span class="app-header__logo">GITS</span>
        <span class="app-header__subtitle">Оценка практических навыков разработчиков</span>
        @if (page().employer && auth.user(); as user) {
          <nav class="app-header__user" aria-label="Кабинет работодателя">
            <a routerLink="/employer" class="app-header__link">Приглашения</a>
            <span class="app-header__who" data-testid="header-user">{{ user.companyName }} · {{ user.email }}</span>
            <button class="btn btn--ghost" type="button" (click)="logout()" data-testid="logout">Выйти</button>
          </nav>
        }
      </header>
    }
    <!-- one outlet for all pages: swapping outlets would create the page twice -->
    <main class="app-main" [class.app-main--full]="page().fullscreen">
      <router-outlet />
    </main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .app-header {
      display: flex; align-items: baseline; gap: 12px;
      padding: 12px 24px; border-bottom: 1px solid var(--border);
      background: var(--surface);
    }
    .app-header__logo { font-weight: 700; font-size: 20px; letter-spacing: 0.04em; }
    .app-header__subtitle { color: var(--text-muted); font-size: 14px; }
    .app-header__user { display: flex; align-items: baseline; gap: 16px; margin-left: auto; font-size: 14px; }
    .app-header__link { color: var(--accent); text-decoration: none; }
    .app-header__link:hover { text-decoration: underline; }
    .app-header__who { color: var(--text-muted); }
    .app-main { flex: 1; padding: 24px; }
    .app-main--full { padding: 0; }
  `,
})
export class App {
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  /**
   * Pages marked {@code data.fullscreen} (the workspace) take the whole window, without the header; employer pages
   * show the signed-in user and the sign-out button in it.
   */
  protected readonly page = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(
        (event): PageState => ({
          fullscreen: App.deepest(this.router.routerState.snapshot.root).data['fullscreen'] === true,
          employer: event.urlAfterRedirects.startsWith('/employer'),
        }),
      ),
    ),
    { initialValue: { fullscreen: false, employer: false } },
  );

  protected logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl('/login'));
  }

  private static deepest(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    return route.firstChild ? App.deepest(route.firstChild) : route;
  }
}
