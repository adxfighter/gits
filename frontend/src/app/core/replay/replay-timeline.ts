import { ReplayEvent, ReplayRun } from './replay.models';

// What the timeline of a replay shows: pastes, time away from the page, runs, the submit, typing speed, and the
// notable moments of the event list. Pure functions over the recorded events (times already non-decreasing).

/** Window of the typing speed, as for burstMax in docs/indicators.md. */
export const SPEED_WINDOW_MS = 5000;

export interface InsertMark {
  t: number;
  /** Characters inserted by this action (all changes of one editor event together). */
  length: number;
  file: string;
  /**
   * Pastes only: not a paste from elsewhere. As the externalPastes indicator counts it (docs/indicators.md): the text
   * was found in the task, its statement or copied in the editor, or it is shorter than 10 non-whitespace characters,
   * or it was undone at once.
   */
  own: boolean;
}

export interface AwayInterval {
  from: number;
  to: number;
}

export interface RunMark {
  t: number;
  mode: 'RUN' | 'SUBMIT';
  /** Compiled and every test passed; null while unknown (not checked, error). */
  passed: boolean | null;
  label: string;
}

export type MomentKind = 'paste' | 'completion' | 'away' | 'run' | 'submit' | 'file';

export interface Moment {
  t: number;
  kind: MomentKind;
  text: string;
  /** A paste of text from elsewhere, a failed run: worth a look. */
  alert: boolean;
}

export interface ReplayTimeline {
  duration: number;
  pastes: InsertMark[];
  completions: InsertMark[];
  away: AwayInterval[];
  runs: RunMark[];
  /** Typed characters per second over the last {@link SPEED_WINDOW_MS}, one value per second from 0. */
  speed: number[];
  moments: Moment[];
  /** Characters typed by hand after the first n events, for every n from 0 to the number of events. */
  typedAfter: number[];
}

export function buildTimeline(events: readonly ReplayEvent[], runs: readonly ReplayRun[]): ReplayTimeline {
  const duration = events.length ? events[events.length - 1].t : 0;
  const pastes = inserts(events, 'paste');
  const completions = inserts(events, 'completion');
  const away = awayIntervals(events);
  const runMarks = runMarksOf(events, runs, duration);
  const moments: Moment[] = [
    ...pastes.map((mark) => ({
      t: mark.t,
      kind: 'paste' as const,
      text: mark.own
        ? `Вставка своего кода или условия: ${mark.length} симв.`
        : `Вставка не из задачи: ${mark.length} симв.`,
      alert: !mark.own,
    })),
    ...completions.map((mark) => ({
      t: mark.t,
      kind: 'completion' as const,
      text: `Автодополнение: ${mark.length} симв.`,
      alert: false,
    })),
    ...away.map((interval) => ({
      t: interval.from,
      kind: 'away' as const,
      text: `Уход со страницы на ${seconds(interval.to - interval.from)}`,
      alert: interval.to - interval.from > 60_000,
    })),
    ...runMarks.map((mark) => ({
      t: mark.t,
      kind: mark.mode === 'SUBMIT' ? ('submit' as const) : ('run' as const),
      text: mark.label,
      alert: mark.passed === false && mark.mode === 'SUBMIT',
    })),
    ...fileSwitches(events),
  ].sort((a, b) => a.t - b.t);
  return {
    duration,
    pastes,
    completions,
    away,
    runs: runMarks,
    speed: speedSeries(events, duration),
    moments,
    typedAfter: typedAfter(events),
  };
}

function typedAfter(events: readonly ReplayEvent[]): number[] {
  const sums = [0];
  for (const event of events) {
    sums.push(sums[sums.length - 1] + (isTyping(event) ? (event.textLength ?? 0) : 0));
  }
  return sums;
}

function isTyping(event: ReplayEvent): boolean {
  return event.type === 'edit' && event.source === 'typing' && !event.isUndo && !event.isRedo;
}

/** The shortest paste from elsewhere that is a suspicion (docs/telemetry.md, «Откуда вставка»). */
export const FOREIGN_PASTE_MIN_CHARS = 10;

/** Edits of one source grouped by editor event (the same t and file): one paste with several cursors is one mark. */
function inserts(events: readonly ReplayEvent[], source: 'paste' | 'completion'): InsertMark[] {
  const marks: (InsertMark & { foreignChars: number; index: number })[] = [];
  events.forEach((event, index) => {
    if (event.type !== 'edit' || event.source !== source || !event.file) {
      return;
    }
    const length = event.textLength ?? event.text?.length ?? 0;
    const foreignChars = source === 'paste' && event.ownCode === false ? nonWhitespace(event.text ?? '') : 0;
    const last = marks[marks.length - 1];
    if (last && last.t === event.t && last.file === event.file) {
      last.length += length;
      last.foreignChars += foreignChars;
      last.index = index;
    } else {
      marks.push({ t: event.t, length, file: event.file, own: true, foreignChars, index });
    }
  });
  return marks
    .filter((mark) => mark.length > 0)
    .map(({ foreignChars, index, ...mark }) => ({
      ...mark,
      own: foreignChars < FOREIGN_PASTE_MIN_CHARS || undoneAtOnce(events, index),
    }));
}

/** The next edit of the paste's file is its undo: nothing was done with the pasted text. */
function undoneAtOnce(events: readonly ReplayEvent[], index: number): boolean {
  const file = events[index].file;
  for (let i = index + 1; i < events.length; i++) {
    if (events[i].type === 'edit' && events[i].file === file) {
      return events[i].isUndo === true;
    }
  }
  return false;
}

function nonWhitespace(text: string): number {
  return text.replace(/\s/g, '').length;
}

const ACTIVITY = new Set(['edit', 'cursor', 'select', 'paste', 'copy', 'run', 'submit', 'completion']);

/**
 * Time away from the page, as the focusLoss indicator counts it (docs/indicators.md): from blur or a hidden tab until
 * the window has the focus and the tab is visible again, or until any activity on the page.
 */
export function awayIntervals(events: readonly ReplayEvent[]): AwayInterval[] {
  const intervals: AwayInterval[] = [];
  let blurred = false;
  let hidden = false;
  let since: number | null = null;
  for (const event of events) {
    if (event.type === 'blur') {
      blurred = true;
    } else if (event.type === 'focus') {
      blurred = false;
    } else if (event.type === 'visibility') {
      hidden = event.state === 'hidden';
    } else if (ACTIVITY.has(event.type)) {
      blurred = false;
      hidden = false;
    }
    const isAway = blurred || hidden;
    if (isAway && since === null) {
      since = event.t;
    } else if (!isAway && since !== null) {
      intervals.push({ from: since, to: event.t });
      since = null;
    }
  }
  if (since !== null && events.length) {
    intervals.push({ from: since, to: events[events.length - 1].t });
  }
  return intervals;
}

/**
 * Runs on the timeline. The run and submit presses are in the telemetry, on the timeline's own clock; their results
 * are the runs of the server, in the same order: the n-th press is the n-th run of its mode. A run without a press —
 * the submit the platform makes when the session ends, or a press lost with its telemetry — is placed by the server
 * time after the task was opened, but never after the end of the recording: that time also counts the minutes the
 * page was closed and the time spent on other tasks.
 */
function runMarksOf(events: readonly ReplayEvent[], runs: readonly ReplayRun[], duration: number): RunMark[] {
  const marks: RunMark[] = [];
  for (const mode of ['RUN', 'SUBMIT'] as const) {
    const presses = events.filter((event) => event.type === (mode === 'RUN' ? 'run' : 'submit'));
    runs
      .filter((run) => run.mode === mode)
      .forEach((run, index) => {
        const press = presses[index];
        if (press) {
          marks.push({ t: press.t, mode, passed: passed(run), label: runLabel(run, false) });
        } else if (run.offsetMs !== null) {
          marks.push({ t: Math.min(run.offsetMs, duration), mode, passed: passed(run), label: runLabel(run, true) });
        }
      });
  }
  return marks.sort((a, b) => a.t - b.t);
}

function passed(run: ReplayRun): boolean | null {
  if (run.status !== 'DONE' && run.status !== 'TIMEOUT') {
    return null;
  }
  return run.status === 'DONE' && run.compiled === true && run.testsTotal !== null && run.testsTotal > 0
    && run.testsPassed === run.testsTotal;
}

function runLabel(run: ReplayRun, withoutPress: boolean): string {
  const what =
    run.mode === 'SUBMIT'
      ? withoutPress
        ? 'Отправлено при завершении сессии'
        : 'Отправка решения'
      : 'Запуск тестов';
  if (run.status === 'QUEUED' || run.status === 'RUNNING') {
    return `${what}: проверяется`;
  }
  if (run.status === 'ERROR') {
    return `${what}: сбой проверки`;
  }
  if (run.status === 'TIMEOUT') {
    return `${what}: превышено время`;
  }
  if (run.compiled === false) {
    return `${what}: ошибка компиляции`;
  }
  // the submit result counts every test, hidden ones included: only the numbers, no test names
  return `${what}: пройдено ${run.testsPassed ?? 0} из ${run.testsTotal ?? 0}`;
}

function fileSwitches(events: readonly ReplayEvent[]): Moment[] {
  const moments: Moment[] = [];
  let current: string | null = null;
  for (const event of events) {
    if ((event.type === 'edit' || event.type === 'cursor' || event.type === 'select') && event.file) {
      if (current !== null && event.file !== current) {
        moments.push({ t: event.t, kind: 'file', text: `Переход в файл ${fileName(event.file)}`, alert: false });
      }
      current = event.file;
    }
  }
  return moments;
}

/**
 * Typing speed: characters typed by hand (edits with source typing, undo and redo left out) in the last 5 s, per
 * second; pastes and completion are marked separately. One value per whole second of the recording.
 */
export function speedSeries(events: readonly ReplayEvent[], duration: number): number[] {
  const perSecond = new Array<number>(Math.floor(duration / 1000) + 1).fill(0);
  for (const event of events) {
    if (isTyping(event)) {
      perSecond[Math.min(perSecond.length - 1, Math.floor(event.t / 1000))] += event.textLength ?? 0;
    }
  }
  const window = SPEED_WINDOW_MS / 1000;
  const speed: number[] = [];
  let sum = 0;
  perSecond.forEach((chars, second) => {
    sum += chars - (second >= window ? perSecond[second - window] : 0);
    speed.push(sum / window);
  });
  return speed;
}

/** Figures of the side panel at the moment {@code t}: what has happened so far. */
export interface LiveFigures {
  typedChars: number;
  pastes: number;
  foreignPastes: number;
  pastedChars: number;
  awayCount: number;
  awaySeconds: number;
  runs: number;
  speedNow: number;
}

/** {@code count} — events at or before {@code t}. */
export function liveFigures(timeline: ReplayTimeline, count: number, t: number): LiveFigures {
  const pastes = timeline.pastes.filter((mark) => mark.t <= t);
  const away = timeline.away.filter((interval) => interval.from <= t);
  return {
    typedChars: timeline.typedAfter[Math.min(count, timeline.typedAfter.length - 1)],
    pastes: pastes.length,
    foreignPastes: pastes.filter((mark) => !mark.own).length,
    pastedChars: pastes.reduce((sum, mark) => sum + mark.length, 0),
    awayCount: away.length,
    awaySeconds: away.reduce((sum, interval) => sum + (Math.min(interval.to, t) - interval.from), 0) / 1000,
    runs: timeline.runs.filter((mark) => mark.t <= t && mark.mode === 'RUN').length,
    speedNow: timeline.speed[Math.min(timeline.speed.length - 1, Math.max(0, Math.floor(t / 1000)))] ?? 0,
  };
}

/** «1:05», «12:34», «1:02:03» — a moment of the recording. */
export function clock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${rest}` : `${minutes}:${rest}`;
}

function seconds(ms: number): string {
  const total = Math.round(ms / 1000);
  return total >= 60 ? `${Math.floor(total / 60)} мин ${total % 60} с` : `${total} с`;
}

export function fileName(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1);
}
