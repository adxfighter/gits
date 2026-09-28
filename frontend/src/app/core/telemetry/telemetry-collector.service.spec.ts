import { TestBed } from '@angular/core/testing';
import type * as Monaco from 'monaco-editor';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CandidateApi } from '../candidate/candidate-api.service';
import { TaskView } from '../candidate/candidate.models';
import { MonacoLoader } from '../editor/monaco-loader.service';
import { TelemetryCollector } from './telemetry-collector.service';
import { TelemetryBatch, TelemetryEvent } from './telemetry.models';

/** A Monaco event: listeners and a way to fire it. */
function emitter<T>() {
  const listeners: ((value: T) => void)[] = [];
  return {
    event: (listener: (value: T) => void) => {
      listeners.push(listener);
      return { dispose: () => listeners.splice(listeners.indexOf(listener), 1) };
    },
    fire: (value: T) => listeners.forEach((listener) => listener(value)),
    count: () => listeners.length,
  };
}

function fakeEditor(taskId: string) {
  const keyDown = emitter<unknown>();
  const keyUp = emitter<unknown>();
  const content = emitter<unknown>();
  const cursor = emitter<unknown>();
  const selection = emitter<unknown>();
  const paste = emitter<unknown>();
  const layout = emitter<unknown>();
  const node = document.createElement('div');
  let path = `/${taskId}/src/Main.java`;
  const model = {
    get uri() {
      return { path };
    },
    getOffsetAt: (position: { lineNumber: number; column: number }) => position.column - 1,
    getValueInRange: () => 'copied',
  };
  const editor = {
    onKeyDown: keyDown.event,
    onKeyUp: keyUp.event,
    onDidChangeModelContent: content.event,
    onDidChangeCursorPosition: cursor.event,
    onDidChangeCursorSelection: selection.event,
    onDidPaste: paste.event,
    onDidLayoutChange: layout.event,
    getModel: () => model,
    getDomNode: () => node,
    getSelection: () => ({}),
  } as unknown as Monaco.editor.IStandaloneCodeEditor;
  return {
    editor,
    node,
    switchTo: (id: string) => (path = `/${id}/src/Main.java`),
    key: (code: string, key: string, repeat = false) => {
      const browserEvent = { code, key, repeat };
      keyDown.fire({ browserEvent });
      keyUp.fire({ browserEvent });
    },
    type: (text: string, change: Partial<{ isUndoing: boolean; isRedoing: boolean }> = {}, offsets = [0]) =>
      content.fire({
        changes: offsets.map((rangeOffset) => ({ rangeOffset, rangeLength: 0, text })),
        isUndoing: !!change.isUndoing,
        isRedoing: !!change.isRedoing,
      }),
    moveCursor: (column: number) => cursor.fire({ position: { lineNumber: 1, column } }),
    menuPaste: () => paste.fire({}),
    listeners: () => keyDown.count() + content.count() + cursor.count(),
  };
}

function task(id: string): TaskView {
  return {
    id, orderNo: 2, kind: 'TASK', title: 'T', status: 'IN_PROGRESS', statementMd: '', timeLimitMin: 20, files: [],
    code: {}, codeSavedAt: null, runsUsed: 0, runsLimit: 60, beaconToken: `token-${id}`, telemetryNextSeq: 0,
    telemetryLastT: 0,
  };
}

describe('TelemetryCollector', () => {
  let sent: { taskId: string; batch: TelemetryBatch }[];
  let completion: () => void;
  let collector: TelemetryCollector;

  beforeEach(() => {
    vi.useFakeTimers();
    sent = [];
    TestBed.configureTestingModule({
      providers: [
        TelemetryCollector,
        {
          provide: CandidateApi,
          useValue: {
            telemetry: (taskId: string, batch: TelemetryBatch) => {
              sent.push({ taskId, batch: structuredClone(batch) });
              return of({ seq: batch.seq, duplicate: false, flags: {}, beaconToken: null });
            },
            telemetryUrl: (taskId: string) => `/api/candidate/tasks/${taskId}/telemetry`,
          },
        },
        {
          provide: MonacoLoader,
          useValue: {
            onCompletionAccepted: (listener: () => void) => {
              completion = listener;
              return () => undefined;
            },
          },
        },
      ],
    });
    collector = TestBed.inject(TelemetryCollector);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  async function events(taskId = 'a'): Promise<TelemetryEvent[]> {
    await collector.flushAll();
    return sent.filter((s) => s.taskId === taskId).flatMap((s) => s.batch.events);
  }

  function edits(list: TelemetryEvent[]) {
    return list.filter((e) => e.type === 'edit') as Extract<TelemetryEvent, { type: 'edit' }>[];
  }

  it('records key classes and never the keys', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));

    fake.key('KeyQ', 'q');
    fake.key('Digit5', '%');
    fake.key('Space', ' ', true);

    const list = await events();
    expect(list.filter((e) => e.type === 'kd').map((e) => ('keyClass' in e ? e.keyClass : ''))).toEqual([
      'letter', 'punct', 'space',
    ]);
    expect(list.find((e) => e.type === 'kd' && e.keyClass === 'space')).toMatchObject({ repeat: true });
    expect(JSON.stringify(sent)).not.toMatch(/"key"|"q"|"%"/);
  });

  it('marks edits by their source: typing, paste, completion, undo', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));

    fake.type('x');
    const pasteEvent = new Event('paste') as ClipboardEvent;
    Object.defineProperty(pasteEvent, 'clipboardData', { value: { getData: () => 'pasted text' } });
    fake.node.dispatchEvent(pasteEvent);
    fake.type('pasted text');
    fake.type('System.out.println();');
    completion();
    fake.type('', { isUndoing: true });

    const list = await events();
    expect(edits(list).map((e) => e.source)).toEqual(['typing', 'paste', 'completion', 'other']);
    expect(edits(list)[3].isUndo).toBe(true);
    expect(list.find((e) => e.type === 'paste')).toMatchObject({ file: 'src/Main.java', length: 11 });
    expect(list.find((e) => e.type === 'completion')).toMatchObject({ accepted: true, insertedLength: 21 });
  });

  it('recognizes a paste from the context menu, which has no DOM paste event', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));

    fake.type('from the menu');
    fake.menuPaste();

    const list = await events();
    expect(edits(list)[0].source).toBe('paste');
    expect(list.find((e) => e.type === 'paste')).toMatchObject({ length: 13 });
  });

  it('gives all changes of a multi-cursor edit the same source', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));

    fake.type(';', {}, [3, 10, 20]);

    const list = edits(await events());
    expect(list.map((e) => [e.rangeOffset, e.source])).toEqual([
      [3, 'typing'], [10, 'typing'], [20, 'typing'],
    ]);
    // one change event, one time
    expect(new Set(list.map((e) => e.t)).size).toBe(1);
  });

  it('thins cursor moves to 10 per second and keeps the last position of a burst', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));

    for (let column = 1; column <= 20; column++) {
      fake.moveCursor(column);
      vi.advanceTimersByTime(10); // 200 ms of fast movement
    }
    vi.advanceTimersByTime(200);

    const cursors = (await events()).filter((e) => e.type === 'cursor');
    expect(cursors.length).toBeLessThanOrEqual(4);
    expect(cursors.at(-1)).toMatchObject({ offset: 19 });
  });

  it('keeps separate streams per task and routes a pending cursor event to its own task', async () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));
    fake.moveCursor(1);
    vi.advanceTimersByTime(20);
    fake.moveCursor(5); // pending in the throttle

    collector.startTask(task('b'));
    fake.switchTo('b');
    fake.moveCursor(2);
    fake.key('KeyB', 'b');
    vi.advanceTimersByTime(200);

    const a = await events('a');
    const b = await events('b');
    expect(a.filter((e) => e.type === 'cursor').map((e) => ('offset' in e ? e.offset : -1))).toEqual([0, 4]);
    expect(b.some((e) => e.type === 'kd')).toBe(true);
    expect(sent.find((s) => s.taskId === 'b')!.batch.seq).toBe(0);
  });

  it('sends buffered events with sendBeacon when the page is hidden', () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    collector.startTask(task('a'));
    fake.key('KeyA', 'a');
    const beacons: { url: string; body: Blob }[] = [];
    Object.defineProperty(navigator, 'sendBeacon', {
      configurable: true,
      value: (url: string, body: Blob) => {
        beacons.push({ url, body });
        return true;
      },
    });

    window.dispatchEvent(new Event('pagehide'));

    expect(beacons).toHaveLength(1);
    expect(beacons[0].url).toBe('/api/candidate/tasks/a/telemetry');
    expect(beacons[0].body.type).toBe('text/plain;charset=utf-8');
  });

  it('removes its listeners when the page is left', () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    expect(fake.listeners()).toBeGreaterThan(0);

    TestBed.resetTestingModule();

    expect(fake.listeners()).toBe(0);
  });
});
