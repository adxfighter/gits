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

/** What the fake editor has selected when a copy or cut happens. */
const selectionText = { value: 'copied' };
/** The line under the cursor: what Monaco copies when nothing is selected. */
const lineContent = { value: '' };
const readOnly = { value: false };

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
    getValueInRange: () => selectionText.value,
    getLineContent: () => lineContent.value,
    getEOL: () => '\n',
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
    // like Monaco: no DOM node until a model is set; the container is always there
    getDomNode: () => null,
    getContainerDomNode: () => node,
    getSelection: () => ({}),
    getSelections: () => [{ isEmpty: () => selectionText.value === '', startLineNumber: 1 }],
    getRawOptions: () => ({ readOnly: readOnly.value }),
  } as unknown as Monaco.editor.IStandaloneCodeEditor;
  return {
    editor,
    node,
    switchTo: (id: string) => (path = `/${id}/src/Main.java`),
    openFile: (file: string) => (path = `/${taskId}/${file}`),
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
    domPaste: (text: string) => {
      const event = new Event('paste') as ClipboardEvent;
      Object.defineProperty(event, 'clipboardData', { value: { getData: () => text } });
      node.dispatchEvent(event);
    },
    copy: (text: string, cut = false) => {
      selectionText.value = text;
      node.dispatchEvent(new Event(cut ? 'cut' : 'copy'));
    },
    listeners: () => keyDown.count() + content.count() + cursor.count(),
  };
}

function task(id: string, kind: TaskView['kind'] = 'TASK'): TaskView {
  return {
    id, orderNo: 2, kind, title: 'T', status: 'IN_PROGRESS', statementMd: '', timeLimitMin: 20, files: [],
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

  describe('where a paste comes from', () => {
    const CODE = 'public int total(List<Visit> visits) { return visits.size(); }';
    const STATEMENT = 'Реализуйте метод total, который возвращает число визитов пациента.';

    function start(kind: TaskView['kind'] = 'TASK') {
      const fake = fakeEditor('a');
      collector.attachEditor(fake.editor);
      collector.startTask(task('a', kind));
      collector.setSourceLookup(() => [CODE, STATEMENT]);
      return fake;
    }

    function pastes(list: TelemetryEvent[]) {
      return list.filter((e) => e.type === 'paste') as Extract<TelemetryEvent, { type: 'paste' }>[];
    }

    it('takes the candidate\'s own code and the statement as own, without a warning', async () => {
      const fake = start();
      fake.domPaste('return visits.size();');
      fake.type('return visits.size();');
      fake.domPaste('возвращает число визитов');
      fake.type('возвращает число визитов');

      const list = await events();
      expect(pastes(list).map((e) => e.ownCode)).toEqual([true, true]);
      expect(edits(list).map((e) => e.ownCode)).toEqual([true, true]);
      expect(collector.copySuspicion()).toBeNull();
    });

    it('warns about text found neither in the code nor in the statement', async () => {
      const fake = start();
      fake.domPaste('Collectors.groupingBy(Visit::doctor)');
      fake.type('Collectors.groupingBy(Visit::doctor)');

      const list = await events();
      expect(pastes(list)[0].ownCode).toBe(false);
      expect(edits(list)[0]).toMatchObject({ source: 'paste', ownCode: false });
      expect(collector.copySuspicion()).toMatchObject({ length: 36 });
    });

    it('takes back what the candidate typed and copied in the editor, even if it was cut since', async () => {
      const fake = start();
      const typed = 'int extra = visits.stream().filter(Visit::late).count();';
      fake.copy(typed, true);
      fake.domPaste(typed);
      fake.type(typed);

      expect(pastes(await events())[0].ownCode).toBe(true);
      expect(collector.copySuspicion()).toBeNull();
    });

    it('remembers the whole line copied or cut without a selection', async () => {
      const fake = start();
      lineContent.value = '    int lateVisits = countLate(visits);';
      fake.copy('', true);
      fake.domPaste('int lateVisits = countLate(visits);\n');
      fake.type('int lateVisits = countLate(visits);\n');

      expect(pastes(await events())[0].ownCode).toBe(true);
      expect(collector.copySuspicion()).toBeNull();
    });

    it('does not judge a paste into a read-only file, which the editor rejects', async () => {
      const fake = start();
      readOnly.value = true;
      fake.domPaste('Collectors.groupingBy(Visit::doctor)');
      readOnly.value = false;

      expect(pastes(await events())).toHaveLength(0);
      expect(collector.copySuspicion()).toBeNull();
    });

    it('checks text that arrived without a paste event, but not an accepted completion', async () => {
      const fake = start();
      // dragged in from outside
      fake.type('Collectors.groupingBy(Visit::doctor)');
      await Promise.resolve();
      expect(collector.copySuspicion()).not.toBeNull();

      collector.copySuspicion.set(null);
      fake.type('System.out.println(visits.size());');
      completion();
      await Promise.resolve();
      expect(collector.copySuspicion()).toBeNull();

      const list = edits(await events());
      expect(list.map((e) => e.source)).toEqual(['paste', 'completion']);
    });

    it('leaves the warm-up retyping to its own check, but judges part 2 like a task', async () => {
      const fake = start('CALIBRATION');
      fake.openFile('src/main/java/cal/Typing.txt');
      fake.domPaste('Collectors.groupingBy(Visit::doctor)');
      fake.type('Collectors.groupingBy(Visit::doctor)');
      expect(collector.copySuspicion()).toBeNull();

      fake.openFile('src/main/java/cal/PhoneNumbers.java');
      fake.domPaste('return raw.replaceAll("[^0-9]", "");');
      fake.type('return raw.replaceAll("[^0-9]", "");');
      expect(collector.copySuspicion()).not.toBeNull();

      expect(pastes(await events()).map((e) => e.ownCode)).toEqual([false, false]);
    });
  });

  it('removes its listeners when the page is left', () => {
    const fake = fakeEditor('a');
    collector.attachEditor(fake.editor);
    expect(fake.listeners()).toBeGreaterThan(0);

    TestBed.resetTestingModule();

    expect(fake.listeners()).toBe(0);
  });
});
