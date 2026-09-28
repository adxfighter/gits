import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { InsertMark, ReplayTimeline, SPEED_WINDOW_MS, clock } from '../../../core/replay/replay-timeline';

/**
 * The timeline of a replay: time away from the page as shaded intervals, pastes (size by their length), runs
 * (green — all tests passed, red — not), the submit; under it, typing speed with pastes and completion marked.
 * A click on the timeline or the slider moves to that moment.
 */
@Component({
  selector: 'app-replay-timeline-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- a mouse shortcut: the slider below is the keyboard and screen reader way to the same moments -->
    <div class="tl" aria-hidden="true" (pointerdown)="seekAt($event)" data-testid="timeline">
      @for (interval of timeline().away; track interval.from) {
        <div
          class="tl__away"
          [style.left.%]="percent(interval.from)"
          [style.width.%]="percent(interval.to) - percent(interval.from)"
          [title]="'Уход со страницы: ' + clock(interval.from) + '–' + clock(interval.to)"
          data-testid="away"
        ></div>
      }
      @for (mark of timeline().completions; track $index) {
        <div class="tl__completion" [style.left.%]="percent(mark.t)" title="Автодополнение"></div>
      }
      @for (mark of timeline().pastes; track $index) {
        <div
          class="tl__paste"
          [class.tl__paste--foreign]="!mark.own"
          [style.left.%]="percent(mark.t)"
          [style.width.px]="size(mark)"
          [style.height.px]="size(mark)"
          [title]="(mark.own ? 'Вставка своего кода: ' : 'Вставка не из задачи: ') + mark.length + ' симв., ' + clock(mark.t)"
          data-testid="paste-mark"
        ></div>
      }
      @for (mark of timeline().runs; track $index) {
        <div
          [class]="'tl__run tl__run--' + (mark.mode === 'SUBMIT' ? 'submit' : 'run') + ' tl__run--' + result(mark.passed)"
          [style.left.%]="percent(mark.t)"
          [title]="mark.label + ', ' + clock(mark.t)"
          data-testid="run-mark"
        >
          {{ mark.mode === 'SUBMIT' ? '⚑' : '' }}
        </div>
      }
      <div class="tl__head" [style.left.%]="percent(time())"></div>
    </div>
    <input
      class="tl__slider"
      type="range"
      min="0"
      [max]="duration()"
      step="100"
      [value]="time()"
      (input)="seekTo($event)"
      aria-label="Момент записи"
      [attr.aria-valuetext]="clock(time())"
      data-testid="timeline-slider"
    />
    <div class="speed" data-testid="speed-chart">
      <svg [attr.viewBox]="'0 0 ' + speedWidth() + ' 100'" preserveAspectRatio="none" aria-hidden="true">
        <polyline class="speed__line" [attr.points]="speedPoints()" vector-effect="non-scaling-stroke" />
        @for (mark of timeline().pastes; track $index) {
          <line class="speed__paste" [attr.x1]="mark.t / 1000" [attr.x2]="mark.t / 1000" y1="0" y2="100" vector-effect="non-scaling-stroke" />
        }
        @for (mark of timeline().completions; track $index) {
          <line class="speed__completion" [attr.x1]="mark.t / 1000" [attr.x2]="mark.t / 1000" y1="70" y2="100" vector-effect="non-scaling-stroke" />
        }
        <line class="speed__head" [attr.x1]="time() / 1000" [attr.x2]="time() / 1000" y1="0" y2="100" vector-effect="non-scaling-stroke" />
      </svg>
      <span class="speed__max muted">{{ speedMax() }} симв./с</span>
    </div>
    <p class="legend muted">
      <span><i class="legend__away"></i>уход со страницы</span>
      <span><i class="legend__paste"></i>вставка своего кода</span>
      <span><i class="legend__paste legend__paste--foreign"></i>вставка не из задачи</span>
      <span><i class="legend__run legend__run--ok"></i>тесты пройдены</span>
      <span><i class="legend__run legend__run--fail"></i>не пройдены</span>
      <span>⚑ отправка</span>
      <span><i class="legend__speed"></i>скорость набора за {{ window }} с</span>
    </p>
  `,
  styles: `
    :host { display: block; }
    .tl { position: relative; height: 40px; background: var(--bg); border: 1px solid var(--border); border-radius: 6px; cursor: pointer; overflow: hidden; }
    .tl__away { position: absolute; top: 0; bottom: 0; background: rgb(128 128 128 / 35%); }
    .tl__completion { position: absolute; bottom: 0; width: 2px; height: 8px; margin-left: -1px; background: var(--text-muted); }
    .tl__paste {
      position: absolute; top: 50%; transform: translate(-50%, -50%);
      border-radius: 50%; background: var(--accent); opacity: 0.8;
    }
    .tl__paste--foreign { background: var(--danger); outline: 2px solid var(--surface); }
    .tl__run { position: absolute; top: 2px; width: 8px; height: 8px; margin-left: -4px; border-radius: 2px; font-size: 12px; line-height: 8px; }
    .tl__run--ok { background: var(--ok); }
    .tl__run--fail { background: var(--danger); }
    .tl__run--unknown { background: var(--text-muted); }
    .tl__run--submit { top: auto; bottom: 2px; width: 14px; height: 14px; margin-left: -7px; line-height: 14px; text-align: center; color: var(--surface); }
    .tl__head { position: absolute; top: 0; bottom: 0; width: 2px; margin-left: -1px; background: var(--text); pointer-events: none; }
    .tl__slider { width: 100%; margin: 4px 0 0; }
    .speed { position: relative; height: 56px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); }
    .speed svg { width: 100%; height: 100%; display: block; }
    .speed__line { fill: none; stroke: var(--accent); stroke-width: 1.5; }
    .speed__paste { stroke: var(--danger); stroke-width: 1; opacity: 0.7; }
    .speed__completion { stroke: var(--text-muted); stroke-width: 1; }
    .speed__head { stroke: var(--text); stroke-width: 1; }
    .speed__max { position: absolute; top: 2px; right: 6px; font-size: 11px; }
    .legend { display: flex; flex-wrap: wrap; gap: 4px 16px; margin: 6px 0 0; font-size: 12px; }
    .legend i { display: inline-block; width: 10px; height: 10px; margin-right: 4px; vertical-align: -1px; border-radius: 2px; }
    .legend__away { background: rgb(128 128 128 / 35%); }
    .legend__paste { border-radius: 50% !important; background: var(--accent); }
    .legend__paste--foreign { background: var(--danger); }
    .legend__run--ok { background: var(--ok); }
    .legend__run--fail { background: var(--danger); }
    .legend__speed { height: 2px !important; background: var(--accent); vertical-align: 3px !important; }
  `,
})
export class ReplayTimelineView {
  readonly timeline = input.required<ReplayTimeline>();
  readonly time = input.required<number>();
  readonly seek = output<number>();

  protected readonly clock = clock;
  protected readonly window = SPEED_WINDOW_MS / 1000;
  protected readonly duration = computed(() => Math.max(1, this.timeline().duration));
  protected readonly speedWidth = computed(() => Math.max(1, this.timeline().duration / 1000));
  protected readonly speedMaxValue = computed(() => Math.max(1, ...this.timeline().speed));
  protected readonly speedMax = computed(() => this.speedMaxValue().toFixed(1).replace('.', ','));
  protected readonly speedPoints = computed(() => {
    const max = this.speedMaxValue();
    return this.timeline()
      .speed.map((value, second) => `${second},${(100 - (value / max) * 96).toFixed(1)}`)
      .join(' ');
  });

  protected percent(t: number): number {
    return (Math.min(Math.max(t, 0), this.duration()) / this.duration()) * 100;
  }

  /** 8 px for a short paste, up to 24 px for a large one. */
  protected size(mark: InsertMark): number {
    return Math.round(Math.min(24, 8 + Math.sqrt(mark.length)));
  }

  protected result(passed: boolean | null): string {
    return passed === null ? 'unknown' : passed ? 'ok' : 'fail';
  }

  protected seekAt(event: PointerEvent): void {
    const bar = event.currentTarget as HTMLElement;
    const box = bar.getBoundingClientRect();
    if (box.width > 0) {
      this.seek.emit(((event.clientX - box.left) / box.width) * this.duration());
    }
  }

  protected seekTo(event: Event): void {
    this.seek.emit(Number((event.target as HTMLInputElement).value));
  }
}
