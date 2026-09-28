import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';

/**
 * Countdown to the end of the session. The remaining time comes from the server ({@code remainingSeconds} as of
 * {@code syncedAt}, a Date.now() value), so the candidate's clock settings do not matter; the parent re-syncs it
 * periodically. Emits {@code expired} once when the time is over.
 */
@Component({
  selector: 'app-session-timer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="timer"
      [class.timer--warn]="remaining() <= 300 && remaining() > 60"
      [class.timer--danger]="remaining() <= 60"
      role="timer"
      [attr.aria-label]="'Осталось ' + label()"
      data-testid="timer"
      >{{ label() }}</span
    >
  `,
  styles: `
    .timer { font-variant-numeric: tabular-nums; font-weight: 600; padding: 2px 8px; border-radius: 4px; }
    .timer--warn { background: #7a5a00; color: #fff; }
    .timer--danger { background: #b3261e; color: #fff; }
  `,
})
export class SessionTimer {
  readonly remainingSeconds = input.required<number>();
  readonly syncedAt = input.required<number>();
  readonly expired = output<void>();

  private readonly now = signal(Date.now());
  private notified = false;

  protected readonly remaining = computed(() => {
    const deadline = this.syncedAt() + this.remainingSeconds() * 1000;
    return Math.max(0, Math.ceil((deadline - this.now()) / 1000));
  });

  protected readonly label = computed(() => {
    const total = this.remaining();
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    const mmss = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    return hours > 0 ? `${hours}:${mmss}` : mmss;
  });

  constructor() {
    const handle = setInterval(() => this.now.set(Date.now()), 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(handle));
    effect(() => {
      if (this.remaining() === 0 && !this.notified) {
        this.notified = true;
        this.expired.emit();
      }
    });
  }
}
