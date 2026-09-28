import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Placeholder of the session replay (/employer/replay/:sessionTaskId); the player itself comes with P14. */
@Component({
  selector: 'app-replay-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" data-testid="replay-placeholder">
      <h1>Воспроизведение сессии</h1>
      <p>Проигрыватель записи сессии появится в следующем обновлении.</p>
      <button class="btn" type="button" (click)="back()">← Назад к отчёту</button>
    </section>
  `,
})
export class ReplayPage {
  /** Route parameter: the session task to replay. */
  readonly sessionTaskId = input.required<string>();

  protected back(): void {
    history.back();
  }
}
