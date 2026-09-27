import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="app-header">
      <span class="app-header__logo">GITS</span>
      <span class="app-header__subtitle">Оценка практических навыков разработчиков</span>
    </header>
    <main class="app-main">
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
  `,
})
export class App {}
