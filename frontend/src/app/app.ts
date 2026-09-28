import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!fullscreen()) {
      <header class="app-header">
        <span class="app-header__logo">GITS</span>
        <span class="app-header__subtitle">Оценка практических навыков разработчиков</span>
      </header>
    }
    <!-- one outlet for all pages: swapping outlets would create the page twice -->
    <main class="app-main" [class.app-main--full]="fullscreen()">
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
    .app-main { flex: 1; padding: 24px; }
    .app-main--full { padding: 0; }
  `,
})
export class App {
  private readonly router = inject(Router);

  /** Pages marked {@code data.fullscreen} (the workspace) take the whole window, without the header. */
  protected readonly fullscreen = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => App.deepest(this.router.routerState.snapshot.root).data['fullscreen'] === true),
    ),
    { initialValue: false },
  );

  private static deepest(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
    return route.firstChild ? App.deepest(route.firstChild) : route;
  }
}
