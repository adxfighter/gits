import { describe, expect, it } from 'vitest';

import { awayIntervals, buildTimeline, clock, liveFigures, speedSeries } from './replay-timeline';
import { ReplayEvent, ReplayRun } from './replay.models';

const MAIN = 'src/main/java/Main.java';

function typing(t: number, text = 'a'): ReplayEvent {
  return { t, type: 'edit', file: MAIN, rangeOffset: 0, rangeLength: 0, text, textLength: text.length, source: 'typing' };
}

function run(change: Partial<ReplayRun>): ReplayRun {
  return {
    id: 'r',
    mode: 'RUN',
    status: 'DONE',
    createdAt: '2026-09-28T08:00:00Z',
    finishedAt: '2026-09-28T08:00:05Z',
    offsetMs: 0,
    compiled: true,
    testsTotal: 3,
    testsPassed: 3,
    ...change,
  };
}

describe('replay timeline', () => {
  it('groups the changes of one paste and tells own code from text from elsewhere', () => {
    const timeline = buildTimeline(
      [
        typing(100),
        { ...typing(200, 'foo'), source: 'paste', ownCode: true },
        { ...typing(300, 'bar'), source: 'paste', ownCode: false },
        { ...typing(300, 'baz'), source: 'paste', ownCode: false },
        { ...typing(400, 'println'), source: 'completion' },
      ],
      [],
    );
    expect(timeline.pastes).toEqual([
      { t: 200, length: 3, file: MAIN, own: true },
      { t: 300, length: 6, file: MAIN, own: false },
    ]);
    expect(timeline.completions).toEqual([{ t: 400, length: 7, file: MAIN, own: true }]);
    const moments = timeline.moments.map((moment) => [moment.kind, moment.alert]);
    expect(moments).toEqual([['paste', false], ['paste', true], ['completion', false]]);
    expect(timeline.moments[1].text).toBe('Вставка не из задачи: 6 симв.');
  });

  it('shades the time away from the page: blur or hidden tab until focus and visible again, or any input', () => {
    const away = awayIntervals([
      { t: 1000, type: 'blur' },
      { t: 5000, type: 'focus' },
      { t: 6000, type: 'visibility', state: 'hidden' },
      { t: 7000, type: 'blur' },
      { t: 8000, type: 'visibility', state: 'visible' },
      { t: 9000, type: 'focus' },
      { t: 10_000, type: 'visibility', state: 'hidden' },
      typing(12_000),
      { t: 13_000, type: 'blur' },
      { t: 20_000, type: 'focus' },
      { t: 30_000, type: 'blur' },
      typing(31_000),
    ]);
    expect(away).toEqual([
      { from: 1000, to: 5000 },
      { from: 6000, to: 9000 },
      { from: 10_000, to: 12_000 },
      { from: 13_000, to: 20_000 },
      { from: 30_000, to: 31_000 },
    ]);
  });

  it('puts runs at their presses in the telemetry and colours them by the result', () => {
    const timeline = buildTimeline(
      [typing(100), { t: 2000, type: 'run' }, { t: 9000, type: 'run' }, { t: 15_000, type: 'submit' }],
      [
        run({ offsetMs: 2500, compiled: false, testsTotal: null, testsPassed: null }),
        run({ offsetMs: 9400, testsPassed: 3 }),
        run({ mode: 'SUBMIT', offsetMs: 15_300, testsTotal: 8, testsPassed: 6 }),
      ],
    );
    expect(timeline.runs.map((mark) => [mark.t, mark.mode, mark.passed])).toEqual([
      [2000, 'RUN', false],
      [9000, 'RUN', true],
      [15_000, 'SUBMIT', false],
    ]);
    expect(timeline.runs.map((mark) => mark.label)).toEqual([
      'Запуск тестов: ошибка компиляции',
      'Запуск тестов: пройдено 3 из 3',
      'Отправка решения: пройдено 6 из 8',
    ]);
  });

  it('falls back to the server time of a run when the presses do not match the runs', () => {
    const timeline = buildTimeline([typing(100)], [run({ offsetMs: 4200 }), run({ offsetMs: null })]);
    expect(timeline.runs.map((mark) => mark.t)).toEqual([4200]);
    expect(timeline.duration).toBe(4200);
  });

  it('measures typing speed over 5 seconds, without pastes and undo', () => {
    const speed = speedSeries(
      [
        typing(0),
        typing(500),
        typing(1500),
        { ...typing(2000, 'long paste'), source: 'paste' },
        { ...typing(2500, 'xx'), source: 'other', isUndo: true },
        typing(7200),
      ],
      8000,
    );
    // 3 characters in the first seconds, gone from the window after 5 s
    expect(speed).toEqual([0.4, 0.6, 0.6, 0.6, 0.6, 0.2, 0, 0.2, 0.2]);
  });

  it('gives the figures of the side panel up to the current moment', () => {
    const events: ReplayEvent[] = [
      typing(1000, 'ab'),
      { t: 2000, type: 'blur' },
      { t: 12_000, type: 'focus' },
      { ...typing(13_000, 'pasted'), source: 'paste', ownCode: false },
      { t: 14_000, type: 'run' },
      typing(20_000, 'c'),
    ];
    const timeline = buildTimeline(events, [run({ offsetMs: 14_000 })]);
    expect(liveFigures(timeline, 1, 1500)).toMatchObject({ typedChars: 2, pastes: 0, awayCount: 0, runs: 0 });
    expect(liveFigures(timeline, 2, 7000)).toMatchObject({ awayCount: 1, awaySeconds: 5 });
    expect(liveFigures(timeline, 6, 20_000)).toMatchObject({
      typedChars: 3,
      pastes: 1,
      foreignPastes: 1,
      pastedChars: 6,
      awayCount: 1,
      awaySeconds: 10,
      runs: 1,
    });
  });

  it('writes moments of the recording as a clock', () => {
    expect(clock(0)).toBe('0:00');
    expect(clock(65_400)).toBe('1:05');
    expect(clock(3_723_000)).toBe('1:02:03');
  });
});
