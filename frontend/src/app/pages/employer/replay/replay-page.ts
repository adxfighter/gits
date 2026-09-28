import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  InjectionToken,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';

import { EmployerApi } from '../../../core/employer/employer-api.service';
import { indicatorLines } from '../../../core/employer/employer-labels';
import { TaskReport } from '../../../core/employer/employer.models';
import { messageOf, statusOf } from '../../../core/http/http-errors';
import { Moment, ReplayTimeline, buildTimeline, clock, fileName, liveFigures } from '../../../core/replay/replay-timeline';
import { ReplayPage as ReplayData } from '../../../core/replay/replay.models';
import { SessionRecording } from '../../../core/replay/session-recording';
import { TrustBadge } from '../trust-badge';
import { ReplayEditor } from './replay-editor';
import { ReplayTimelineView } from './replay-timeline-view';

export const SPEEDS = [1, 2, 5, 10, 20] as const;

/** Animation frames of the player; tests replace them with frames they step by hand. */
export interface FrameScheduler {
  request(callback: (now: number) => void): number;
  cancel(id: number): void;
}

export const FRAME_SCHEDULER = new InjectionToken<FrameScheduler>('FRAME_SCHEDULER', {
  providedIn: 'root',
  factory: () => ({
    request: (callback) => requestAnimationFrame(callback),
    cancel: (id) => cancelAnimationFrame(id),
  }),
});
/** When pauses are skipped, playing resumes this long before the next event. */
export const SKIP_LEAD_MS = 1000;

interface Loaded {
  data: ReplayData;
  recording: SessionRecording;
  timeline: ReplayTimeline;
  /** The task in the session report: title, indicators, final code; null when the report could not be loaded. */
  task: TaskReport | null;
}

/**
 * Replay of one task of a session (/employer/replay/:sessionTaskId): the candidate's editor at any moment, a player
 * with speeds and pause skipping, the timeline and the typing speed, and on the side the current figures and the
 * notable moments. The recording is rebuilt in the browser from the telemetry (SessionRecording).
 */
@Component({
  selector: 'app-replay-page',
  imports: [RouterLink, ReplayEditor, ReplayTimelineView, TrustBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '(document:keydown)': 'key($event)' },
  template: `
    <section class="page replay">
      @if (loaded(); as l) {
        <a class="back" [routerLink]="['/employer/sessions', l.data.sessionId]">← Отчёт по сессии</a>
        <header class="page__header">
          <div>
            <h1 data-testid="replay-title">{{ l.task?.title ?? 'Воспроизведение сессии' }}</h1>
            <p class="muted replay__meta">
              Воспроизведение записи
              @if (l.task; as task) {
                · {{ task.kind === 'CALIBRATION' ? 'разминка' : task.templateTitle }}
              }
            </p>
          </div>
          @if (matches() === true) {
            <p class="replay__check replay__check--ok" data-testid="replay-check">
              ✓ Восстановленный код совпадает с итоговым
            </p>
          } @else if (matches() === false) {
            <p class="replay__check notice" data-testid="replay-check">
              Восстановленный код отличается от итогового: например, страница перезагружалась до автосохранения или
              время вышло раньше, чем код сохранился. Воспроизведение показывает ход работы приблизительно.
              @if (l.recording.brokenEdits > 0) {
                Изменений, не совпавших с текстом: {{ l.recording.brokenEdits }}.
              }
            </p>
          }
        </header>

        @if (l.recording.events.length === 0) {
          <div class="state" data-testid="replay-empty">
            <p>По этой задаче нет записи ввода.</p>
            <p class="muted">Кандидат не открывал задачу или телеметрия не дошла до сервера.</p>
          </div>
        } @else {
          <div class="replay__grid">
            <div class="replay__main">
              <div class="replay__tabs" role="group" aria-label="Файлы">
                @for (path of l.recording.paths; track path) {
                  <button
                    type="button"
                    class="replay__tab"
                    [class.replay__tab--active]="path === shownPath()"
                    [attr.aria-pressed]="path === shownPath()"
                    [title]="path"
                    (click)="viewPath.set(path)"
                    data-testid="replay-tab"
                  >
                    {{ name(path) }}
                    @if (path === frame().activeFile) {
                      <span class="replay__dot" title="Кандидат работает в этом файле">●</span>
                    }
                  </button>
                }
                @if (viewPath() !== null) {
                  <button type="button" class="btn btn--ghost replay__follow" (click)="viewPath.set(null)" data-testid="replay-follow">
                    Следовать за кандидатом
                  </button>
                }
              </div>
              <app-replay-editor class="replay__editor" [files]="frame().files" [path]="shownPath()" [cursor]="frame().cursor" />

              <div class="player" data-testid="player">
                <button class="btn btn--primary player__play" type="button" (click)="toggle()" data-testid="play">
                  <span aria-hidden="true">{{ playing() ? '❚❚' : '▶' }}</span> {{ playing() ? 'Пауза' : 'Пуск' }}
                </button>
                <span class="player__time" data-testid="replay-time">{{ clock(time()) }} / {{ clock(l.timeline.duration) }}</span>
                <span class="player__speeds" role="group" aria-label="Скорость">
                  @for (value of speeds; track value) {
                    <button
                      type="button"
                      class="btn player__speed"
                      [class.btn--primary]="value === speed()"
                      [attr.aria-pressed]="value === speed()"
                      (click)="speed.set(value)"
                      data-testid="speed"
                    >
                      {{ value }}×
                    </button>
                  }
                </span>
                <label class="player__skip">
                  <input type="checkbox" [checked]="skipPauses()" (change)="skipPauses.set(checked($event))" data-testid="skip-pauses" />
                  пропускать паузы длиннее
                  <input
                    class="input player__seconds"
                    type="number"
                    min="1"
                    max="600"
                    [value]="skipSeconds()"
                    (change)="setSkipSeconds($event)"
                    aria-label="Секунд"
                    data-testid="skip-seconds"
                  />
                  с
                </label>
              </div>
              <app-replay-timeline-view [timeline]="l.timeline" [time]="time()" (seek)="seek($event)" />
            </div>

            <aside class="replay__side">
              <section class="side-card">
                <h2 class="side-card__title">Сейчас · {{ clock(time()) }}</h2>
                <dl class="figures" data-testid="live-figures">
                  <div><dt>Набрано вручную</dt><dd>{{ figures().typedChars }} симв.</dd></div>
                  <div><dt>Скорость набора</dt><dd>{{ speedText(figures().speedNow) }} симв./с</dd></div>
                  <div>
                    <dt>Вставки</dt>
                    <dd>
                      {{ figures().pastes }} ({{ figures().pastedChars }} симв.)
                      @if (figures().foreignPastes > 0) {
                        <span class="error"> · не из задачи: {{ figures().foreignPastes }}</span>
                      }
                    </dd>
                  </div>
                  <div><dt>Уходы со страницы</dt><dd>{{ figures().awayCount }}, {{ awayText(figures().awaySeconds) }}</dd></div>
                  <div><dt>Запуски тестов</dt><dd>{{ figures().runs }}</dd></div>
                </dl>
              </section>

              @if (l.task?.indicators; as indicators) {
                <details class="side-card">
                  <summary class="side-card__title">
                    Итоговые индикаторы
                    @if (l.task?.kind === 'TASK') {
                      <app-trust-badge [level]="l.task?.trustLevel ?? null" />
                    }
                  </summary>
                  <ul class="final-indicators">
                    @for (line of finalIndicators(); track line.name) {
                      <li><strong>{{ line.label }}.</strong> {{ line.explanation }}</li>
                    }
                  </ul>
                </details>
              }

              <section class="side-card">
                <h2 class="side-card__title">События</h2>
                @if (l.timeline.moments.length === 0) {
                  <p class="muted">Вставок, уходов со страницы и запусков не было.</p>
                } @else {
                  <ol class="moments" data-testid="moments">
                    @for (moment of l.timeline.moments; track $index) {
                      <li>
                        <button
                          type="button"
                          class="moment"
                          [class.moment--alert]="moment.alert"
                          [class.moment--current]="moment === currentMoment()"
                          (click)="seek(moment.t)"
                          data-testid="moment"
                        >
                          <span class="moment__time">{{ clock(moment.t) }}</span>
                          <span class="moment__icon" aria-hidden="true">{{ icon(moment) }}</span>
                          {{ moment.text }}
                        </button>
                      </li>
                    }
                  </ol>
                }
              </section>
            </aside>
          </div>
        }
      } @else if (error(); as text) {
        <div class="state" role="alert" data-testid="replay-error">
          <p [class.error]="!notFound()">{{ notFound() ? 'Задание не найдено.' : text }}</p>
          @if (notFound()) {
            <a class="btn" routerLink="/employer">К приглашениям</a>
          } @else {
            <button class="btn" type="button" (click)="retry()">Повторить</button>
          }
        </div>
      } @else {
        <p class="muted" role="status" data-testid="replay-loading">Загружаем запись сессии…</p>
      }
    </section>
  `,
  styles: `
    .back { display: inline-block; margin-bottom: 12px; color: var(--accent); text-decoration: none; }
    .back:hover { text-decoration: underline; }
    .replay { max-width: 1400px; }
    .replay__meta { margin: 4px 0 0; }
    .replay__check { margin: 0; max-width: 520px; font-size: 14px; }
    .replay__check--ok { color: var(--ok); font-weight: 600; }
    .replay__grid { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
    .replay__main { display: flex; flex-direction: column; gap: 10px; min-width: 0; }
    .replay__tabs { display: flex; flex-wrap: wrap; align-items: center; gap: 2px; border-bottom: 1px solid var(--border); }
    .replay__tab {
      font: inherit; font-size: 13px; padding: 4px 10px; cursor: pointer;
      background: transparent; color: var(--text-muted);
      border: 1px solid transparent; border-bottom: none; border-radius: 6px 6px 0 0;
    }
    .replay__tab--active { color: var(--text); background: var(--surface); border-color: var(--border); }
    .replay__tab:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
    .replay__dot { color: var(--ok); font-size: 10px; margin-left: 4px; }
    .replay__follow { margin-left: auto; font-size: 13px; }
    /* the player and the timeline stay on a 1280×720 screen under the editor */
    .replay__editor { height: clamp(260px, calc(100vh - 420px), 640px); margin-top: -10px; border: 1px solid var(--border); border-top: none; }
    .player { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
    .player__play { min-width: 96px; }
    .player__time { font-variant-numeric: tabular-nums; font-weight: 600; min-width: 110px; }
    .player__speeds { display: inline-flex; gap: 4px; }
    .player__speed { padding: 4px 8px; }
    .player__skip { display: inline-flex; align-items: center; gap: 6px; font-size: 14px; }
    .player__seconds { width: 64px; padding: 2px 6px; }
    .replay__side { display: flex; flex-direction: column; gap: 12px; }
    .side-card { padding: 12px 14px; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
    .side-card__title { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 0 0 8px; font-size: 15px; font-weight: 600; }
    summary.side-card__title { cursor: pointer; margin: 0; }
    .figures { margin: 0; display: grid; gap: 6px; font-size: 14px; }
    .figures div { display: flex; justify-content: space-between; gap: 8px; }
    .figures dt { color: var(--text-muted); }
    .figures dd { margin: 0; text-align: right; font-variant-numeric: tabular-nums; }
    .final-indicators { margin: 8px 0 0; padding-left: 18px; font-size: 13px; }
    .moments { list-style: none; margin: 0; padding: 0; max-height: 420px; overflow-y: auto; }
    .moment {
      display: flex; gap: 6px; width: 100%; padding: 4px 6px; text-align: left;
      font: inherit; font-size: 13px; color: var(--text); background: transparent; border: none; border-radius: 4px; cursor: pointer;
    }
    .moment:hover { background: var(--bg); }
    .moment:focus-visible { outline: 2px solid var(--accent); }
    .moment--current { background: var(--bg); font-weight: 600; }
    .moment--alert { color: var(--danger); }
    .moment__time { flex: none; width: 44px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
    .moment__icon { flex: none; width: 16px; text-align: center; }
  `,
})
export class ReplayPage {
  /** Route parameter: the session task to replay. */
  readonly sessionTaskId = input.required<string>();

  private readonly api = inject(EmployerApi);
  private readonly attempt = signal(0);
  private readonly frames = inject(FRAME_SCHEDULER);
  private frameRequest: number | null = null;
  private lastTick = 0;

  protected readonly speeds = SPEEDS;
  protected readonly clock = clock;
  protected readonly loaded = signal<Loaded | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notFound = signal(false);

  protected readonly time = signal(0);
  protected readonly playing = signal(false);
  protected readonly speed = signal<number>(5);
  protected readonly skipPauses = signal(true);
  protected readonly skipSeconds = signal(10);
  /** A file the employer chose to look at; null — follow the candidate. */
  protected readonly viewPath = signal<string | null>(null);

  protected readonly count = computed(() => this.loaded()?.recording.countAt(this.time()) ?? 0);
  protected readonly frame = computed(
    () => this.loaded()?.recording.frameAfter(this.count()) ?? { files: {}, activeFile: null, cursor: null },
  );
  protected readonly shownPath = computed(() => this.viewPath() ?? this.frame().activeFile);
  protected readonly figures = computed(() => {
    const loaded = this.loaded();
    return loaded
      ? liveFigures(loaded.timeline, this.count(), this.time())
      : liveFigures(buildTimeline([], []), 0, 0);
  });
  protected readonly currentMoment = computed(() => {
    const moments = this.loaded()?.timeline.moments ?? [];
    let current: Moment | null = null;
    for (const moment of moments) {
      if (moment.t > this.time()) {
        break;
      }
      current = moment;
    }
    return current;
  });
  protected readonly finalIndicators = computed(() => indicatorLines(this.loaded()?.task?.indicators ?? null));
  /** Whether the rebuilt editable files are the final code of the report; null when there is nothing to compare. */
  protected readonly matches = computed(() => {
    const loaded = this.loaded();
    const finalCode = loaded?.task?.finalCode;
    if (!loaded || !finalCode || Object.keys(finalCode).length === 0 || loaded.recording.events.length === 0) {
      return null;
    }
    const files = loaded.recording.finalFiles();
    return Object.entries(finalCode).every(([path, content]) => files[path] === content);
  });

  constructor() {
    toObservable(computed(() => ({ id: this.sessionTaskId(), attempt: this.attempt() })))
      .pipe(
        switchMap(({ id }) => {
          this.pause();
          this.loaded.set(null);
          this.error.set(null);
          this.notFound.set(false);
          return this.api.replay(id).pipe(
            switchMap((data) =>
              this.api.report(data.sessionId).pipe(
                map((report) => report.tasks.find((task) => task.id === data.sessionTaskId) ?? null),
                // the replay works without the report: no title, indicators and check then
                catchError(() => of(null)),
                map((task) => ({ data, task })),
              ),
            ),
            map((result) => ({ ...result, error: null as unknown })),
            catchError((error: unknown) => of({ data: null, task: null, error })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ data, task, error }) => {
        if (!data) {
          this.notFound.set(statusOf(error) === 404);
          this.error.set(messageOf(error, 'Не удалось загрузить запись сессии.'));
          return;
        }
        const recording = new SessionRecording(data.initialFiles, data.events);
        this.loaded.set({ data, recording, task, timeline: buildTimeline(recording.events, data.runs) });
        this.time.set(0);
        this.viewPath.set(null);
      });
    inject(DestroyRef).onDestroy(() => this.pause());
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected toggle(): void {
    if (this.playing()) {
      this.pause();
    } else {
      this.play();
    }
  }

  protected seek(t: number): void {
    const duration = this.loaded()?.timeline.duration ?? 0;
    this.time.set(Math.min(Math.max(0, t), duration));
  }

  /** Space plays and pauses, unless the focus is in a field or on a button (they handle it themselves). */
  protected key(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (event.code !== 'Space' || !this.loaded() || target?.closest('input, button, select, textarea, a, summary')) {
      return;
    }
    event.preventDefault();
    this.toggle();
  }

  protected checked(event: Event): boolean {
    return (event.target as HTMLInputElement).checked;
  }

  protected setSkipSeconds(event: Event): void {
    const value = Math.round(Number((event.target as HTMLInputElement).value));
    this.skipSeconds.set(Number.isFinite(value) ? Math.min(600, Math.max(1, value)) : 10);
  }

  protected name(path: string): string {
    return fileName(path);
  }

  protected icon(moment: Moment): string {
    return { paste: '⎘', completion: '✎', away: '↗', run: '▶', submit: '⚑', file: '↦' }[moment.kind];
  }

  protected speedText(value: number): string {
    return value.toFixed(1).replace('.', ',');
  }

  protected awayText(seconds: number): string {
    const total = Math.round(seconds);
    return total >= 60 ? `${Math.floor(total / 60)} мин ${total % 60} с` : `${total} с`;
  }

  private play(): void {
    const loaded = this.loaded();
    if (!loaded || this.playing()) {
      return;
    }
    if (this.time() >= loaded.timeline.duration) {
      this.time.set(0);
    }
    this.playing.set(true);
    this.lastTick = performance.now();
    this.frameRequest = this.frames.request((now) => this.tick(now));
  }

  private pause(): void {
    this.playing.set(false);
    if (this.frameRequest !== null) {
      this.frames.cancel(this.frameRequest);
      this.frameRequest = null;
    }
  }

  /** One animation frame: the recording moves by the real time passed times the speed. */
  private tick(now: number): void {
    const loaded = this.loaded();
    if (!loaded || !this.playing()) {
      return;
    }
    // a frame may start before the click that started the playback: never a step back
    const elapsed = Math.max(0, Math.min(now - this.lastTick, 250));
    this.lastTick = now;
    const skipMs = this.skipPauses() ? this.skipSeconds() * 1000 : null;
    this.time.set(nextTime(loaded.recording, this.time(), elapsed * this.speed(), skipMs, loaded.timeline.duration));
    if (this.time() >= loaded.timeline.duration) {
      this.pause();
      return;
    }
    this.frameRequest = this.frames.request((next) => this.tick(next));
  }
}

/**
 * Where the playback is after {@code step} ms of recording time. With pause skipping on, a pause longer than
 * {@code skipMs} before the next event is jumped over, up to {@link SKIP_LEAD_MS} before that event.
 */
export function nextTime(
  recording: SessionRecording,
  time: number,
  step: number,
  skipMs: number | null,
  duration: number,
): number {
  let next = time + step;
  if (skipMs !== null) {
    const upcoming = recording.events[recording.countAt(time)];
    if (upcoming && upcoming.t - time > skipMs) {
      next = Math.max(next, upcoming.t - SKIP_LEAD_MS);
    }
  }
  return Math.min(next, duration);
}
