import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { MonacoLoader } from '../../../core/editor/monaco-loader.service';
import { ReplayEvent, ReplayPage as ReplayData } from '../../../core/replay/replay.models';
import { SessionRecording } from '../../../core/replay/session-recording';
import { sessionReport, taskReport } from '../employer-fixtures';
import { FRAME_SCHEDULER, ReplayPage, SKIP_LEAD_MS, nextTime } from './replay-page';

const MAIN = 'src/main/java/ru/gits/task/Transfers.java';
const URL = '/api/employer/session-tasks/task-2/replay';

function edit(t: number, rangeOffset: number, text: string, change: Partial<ReplayEvent> = {}): ReplayEvent {
  return { t, type: 'edit', file: MAIN, rangeOffset, rangeLength: 0, text, textLength: text.length, source: 'typing', seq: 0, ...change };
}

function page(change: Partial<ReplayData>): ReplayData {
  return {
    sessionTaskId: 'task-2',
    sessionId: 'session-1',
    status: 'SUBMITTED',
    startedAt: '2026-09-28T08:10:00Z',
    submittedAt: '2026-09-28T08:32:05Z',
    initialFiles: [
      { path: MAIN, kind: 'STARTER', editable: true, content: 'class Transfers ' },
      { path: 'src/test/java/TransfersTest.java', kind: 'VISIBLE_TEST', editable: false, content: 'test' },
    ],
    runs: [],
    events: [],
    fromSeq: 0,
    nextSeq: null,
    ...change,
  };
}

describe('ReplayPage', () => {
  let http: HttpTestingController;
  let frames: FrameRequestCallback[];

  beforeEach(() => {
    frames = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: MonacoLoader, useValue: { load: () => new Promise(() => undefined) } },
        {
          provide: FRAME_SCHEDULER,
          useValue: { request: (callback: FrameRequestCallback) => frames.push(callback), cancel: () => (frames = []) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  /** Two pages of events, then the report; the final code of the report is «class Transfers {}». */
  const runs: ReplayData['runs'] = [
    { id: 'r1', mode: 'RUN', status: 'DONE', createdAt: '', finishedAt: '', offsetMs: 63_500, compiled: true, testsTotal: 2, testsPassed: 1 },
  ];

  async function render(finalCode = 'class Transfers {} // copied here'): Promise<{ fixture: ComponentFixture<ReplayPage>; root: HTMLElement }> {
    const fixture = TestBed.createComponent(ReplayPage);
    fixture.componentRef.setInput('sessionTaskId', 'task-2');
    fixture.detectChanges();
    http.expectOne(`${URL}?fromSeq=0&limit=200`).flush(
      page({
        events: [
          edit(1000, 16, '{'),
          { t: 2000, type: 'blur', seq: 0 },
          { t: 62_000, type: 'focus', seq: 0 },
          { t: 63_000, type: 'run', seq: 0 },
        ],
        runs,
        nextSeq: 1,
      }),
    );
    http.expectOne(`${URL}?fromSeq=1&limit=200`).flush(
      // runs come on every page
      page({ events: [edit(90_000, 17, '} // copied here', { seq: 1, source: 'paste', ownCode: false })], runs, fromSeq: 1 }),
    );
    http.expectOne('/api/employer/sessions/session-1/report').flush(
      sessionReport({ tasks: [taskReport({ finalCode: { [MAIN]: finalCode } })] }),
    );
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, root: fixture.nativeElement as HTMLElement };
  }

  function text(element: Element | null | undefined): string {
    return element?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('loads every page of the recording and checks it against the final code', async () => {
    const { root } = await render();
    expect(text(root.querySelector('[data-testid="replay-title"]'))).toBe('Зависание взаиморасчётов');
    expect(text(root.querySelector('[data-testid="replay-check"]'))).toContain('совпадает с итоговым');
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('0:00 / 1:30');
    expect(root.querySelector('a.back')?.getAttribute('href')).toBe('/employer/sessions/session-1');
    const moments = [...root.querySelectorAll('[data-testid="moment"]')].map(text);
    expect(moments).toEqual([
      '0:02↗ Уход со страницы на 1 мин 0 с',
      '1:03▶ Запуск тестов: пройдено 1 из 2',
      '1:30⎘ Вставка не из задачи: 16 симв.',
    ]);
    expect(root.querySelectorAll('[data-testid="paste-mark"]')).toHaveLength(1);
    expect(root.querySelector('[data-testid="run-mark"]')?.className).toContain('tl__run--fail');
    expect(root.querySelectorAll('[data-testid="away"]')).toHaveLength(1);
  });

  it('warns when the rebuilt code differs from the final code', async () => {
    const { root } = await render('class Transfers { int x; }');
    expect(text(root.querySelector('[data-testid="replay-check"]'))).toContain('отличается от итогового');
  });

  it('moves to a moment from the event list and updates the figures', async () => {
    const { fixture, root } = await render();
    const moments = root.querySelectorAll<HTMLButtonElement>('[data-testid="moment"]');
    moments[2].click();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('1:30 / 1:30');
    const figures = [...root.querySelectorAll('[data-testid="live-figures"] div')].map(
      (row) => `${text(row.querySelector('dt'))}: ${text(row.querySelector('dd'))}`,
    );
    expect(figures).toEqual([
      'Набрано вручную: 1 симв.',
      'Скорость набора: 0,0 симв./с',
      'Вставки: 1 (16 симв.) · не из задачи: 1',
      'Уходы со страницы: 1, 1 мин 0 с',
      'Запуски тестов: 1',
    ]);
    expect(moments[2].classList).toContain('moment--current');
  });

  it('plays at the chosen speed, skips long pauses and stops at the end', async () => {
    const { fixture, root } = await render();
    root.querySelectorAll<HTMLButtonElement>('[data-testid="speed"]')[4].click(); // 20×
    root.querySelector<HTMLButtonElement>('[data-testid="play"]')!.click();
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="play"]'))).toContain('Пауза');
    const step = (now: number) => {
      const callback = frames.shift();
      callback?.(now);
      fixture.detectChanges();
    };
    const start = performance.now();
    step(start + 50); // 50 ms × 20 = 1 s of the recording
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('0:01 / 1:30');
    step(start + 100);
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('0:02 / 1:30');
    // the minute away is a pause without events: skipped to 1 s before the focus comes back
    step(start + 150);
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('1:01 / 1:30');
    for (let i = 4; i < 40 && frames.length; i++) {
      step(start + i * 250);
    }
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('1:30 / 1:30');
    expect(text(root.querySelector('[data-testid="play"]'))).toContain('Пуск');
  });

  it('lets the employer look at another file and follow the candidate again', async () => {
    const { fixture, root } = await render();
    const tabs = root.querySelectorAll<HTMLButtonElement>('[data-testid="replay-tab"]');
    expect([...tabs].map(text)).toEqual(['Transfers.java ●', 'TransfersTest.java']);
    tabs[1].click();
    fixture.detectChanges();
    expect(tabs[1].getAttribute('aria-pressed')).toBe('true');
    root.querySelector<HTMLButtonElement>('[data-testid="replay-follow"]')!.click();
    fixture.detectChanges();
    expect(tabs[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('stops asking for frames when paused, and a second press does not start a second loop', async () => {
    const { fixture, root } = await render();
    const play = root.querySelector<HTMLButtonElement>('[data-testid="play"]')!;
    play.click();
    expect(frames).toHaveLength(1);
    play.click(); // pause
    expect(frames).toHaveLength(0);
    play.click();
    play.click();
    play.click();
    expect(frames).toHaveLength(1);
    // the first frame may start before the click: the time never goes back
    frames.shift()!(performance.now() - 16);
    fixture.detectChanges();
    expect(text(root.querySelector('[data-testid="replay-time"]'))).toBe('0:00 / 1:30');
    fixture.destroy();
    expect(frames).toHaveLength(0);
  });

  it('says so when a task has no recording', async () => {
    const fixture = TestBed.createComponent(ReplayPage);
    fixture.componentRef.setInput('sessionTaskId', 'task-2');
    fixture.detectChanges();
    http.expectOne(`${URL}?fromSeq=0&limit=200`).flush(page({}));
    http.expectOne('/api/employer/sessions/session-1/report').flush(sessionReport());
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text((fixture.nativeElement as HTMLElement).querySelector('[data-testid="replay-empty"]'))).toContain('нет записи');
  });

  it('tells a missing task from other failures', async () => {
    const fixture = TestBed.createComponent(ReplayPage);
    fixture.componentRef.setInput('sessionTaskId', 'task-2');
    fixture.detectChanges();
    http.expectOne(`${URL}?fromSeq=0&limit=200`).flush({}, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text((fixture.nativeElement as HTMLElement).querySelector('[data-testid="replay-error"]'))).toContain('Задание не найдено');
  });

  it('computes the next moment of the playback', () => {
    const recording = new SessionRecording([], [edit(1000, 0, 'a'), edit(40_000, 0, 'b')]);
    expect(nextTime(recording, 0, 500, null, 40_000)).toBe(500);
    expect(nextTime(recording, 1000, 500, 10_000, 40_000)).toBe(40_000 - SKIP_LEAD_MS);
    // a short pause is played as it was
    expect(nextTime(recording, 35_000, 500, 10_000, 40_000)).toBe(35_500);
    expect(nextTime(recording, 39_900, 500, null, 40_000)).toBe(40_000);
  });
});
