import { describe, expect, it } from 'vitest';

import taskPastesAndCut from './recordings/task-pastes-and-cut.json';
import taskSmall from './recordings/task-small.json';
import warmUpTwoFiles from './recordings/warm-up-two-files.json';
import { ReplayEvent, ReplayFile } from './replay.models';
import { SessionRecording } from './session-recording';

const MAIN = 'src/main/java/Main.java';
const OTHER = 'src/main/java/Other.java';

function file(path: string, content: string, editable = true): ReplayFile {
  return { path, kind: editable ? 'STARTER' : 'READONLY', editable, content };
}

function edit(t: number, rangeOffset: number, rangeLength: number, text: string, change: Partial<ReplayEvent> = {}): ReplayEvent {
  return { t, type: 'edit', file: MAIN, rangeOffset, rangeLength, text, textLength: text.length, source: 'typing', ...change };
}

describe('SessionRecording', () => {
  it('rebuilds typing, deleting and replacing', () => {
    const recording = new SessionRecording(
      [file(MAIN, 'class A {}')],
      [
        edit(10, 9, 0, 'int x;'), // class A {int x;}
        edit(20, 13, 1, 'y'), // replace x with y
        edit(30, 14, 1, ''), // delete ';'
        edit(40, 0, 0, '// a\n'),
      ],
    );
    expect(recording.frameAfter(0).files[MAIN]).toBe('class A {}');
    expect(recording.frameAfter(2).files[MAIN]).toBe('class A {int y;}');
    expect(recording.finalFiles()[MAIN]).toBe('// a\nclass A {int y}');
    expect(recording.brokenEdits).toBe(0);
  });

  it('takes undo and redo as the edits the editor recorded for them', () => {
    const recording = new SessionRecording(
      [file(MAIN, '')],
      [
        edit(10, 0, 0, 'hello world', { source: 'paste', ownCode: false }),
        edit(20, 0, 11, '', { source: 'other', isUndo: true }),
        edit(30, 0, 0, 'hello world', { source: 'other', isRedo: true }),
        edit(40, 5, 6, '', { source: 'other', isUndo: false }),
      ],
    );
    expect(recording.frameAt(25).files[MAIN]).toBe('');
    expect(recording.frameAt(35).files[MAIN]).toBe('hello world');
    expect(recording.finalFiles()[MAIN]).toBe('hello');
  });

  it('applies several changes of one editor event (several cursors) in their recorded order', () => {
    // Monaco lists the changes of one event from the end of the document, each against the text before the event
    const recording = new SessionRecording(
      [file(MAIN, 'a\nb\nc')],
      [edit(10, 4, 0, '// '), edit(10, 2, 0, '// '), edit(10, 0, 0, '// ')],
    );
    expect(recording.finalFiles()[MAIN]).toBe('// a\n// b\n// c');
  });

  it('keeps several files apart and follows the active file', () => {
    const recording = new SessionRecording(
      [file('src/test/java/MainTest.java', 'test', false), file(MAIN, 'm'), file(OTHER, 'o')],
      [
        edit(10, 1, 0, '1'),
        { t: 20, type: 'cursor', file: OTHER, offset: 0 },
        edit(30, 1, 0, '2', { file: OTHER }),
        { t: 40, type: 'select', file: 'src/test/java/MainTest.java', offset: 0, length: 4 },
      ],
    );
    expect(recording.paths).toEqual([MAIN, OTHER, 'src/test/java/MainTest.java']);
    expect(recording.frameAfter(0).activeFile).toBe(MAIN);
    expect(recording.frameAt(20).activeFile).toBe(OTHER);
    // the start is in the file of the first action, not always the first file
    const warmUp = new SessionRecording([file(MAIN, ''), file(OTHER, '')], [{ t: 5, type: 'cursor', file: OTHER, offset: 0 }]);
    expect(warmUp.frameAfter(0).activeFile).toBe(OTHER);
    expect(recording.frameAt(30)).toMatchObject({ activeFile: OTHER, cursor: { file: OTHER, offset: 2 } });
    expect(recording.finalFiles()).toEqual({ [MAIN]: 'm1', [OTHER]: 'o2', 'src/test/java/MainTest.java': 'test' });
    expect(recording.frameAfter(4).activeFile).toBe('src/test/java/MainTest.java');
  });

  it('gives a tab to a file edited outside the task files', () => {
    const recording = new SessionRecording([file(MAIN, '')], [edit(10, 0, 0, 'x', { file: OTHER })]);
    expect(recording.paths).toEqual([MAIN, OTHER]);
    expect(recording.finalFiles()[OTHER]).toBe('x');
  });

  it('counts an edit that does not fit the text and still shows the rest', () => {
    const recording = new SessionRecording([file(MAIN, 'abc')], [edit(10, 10, 2, 'x'), edit(20, 0, 0, '>')]);
    expect(recording.brokenEdits).toBe(1);
    expect(recording.finalFiles()[MAIN]).toBe('>abcx');
  });

  it('never lets the time go back', () => {
    const recording = new SessionRecording([file(MAIN, '')], [edit(100, 0, 0, 'a'), edit(90, 1, 0, 'b'), edit(120, 2, 0, 'c')]);
    expect(recording.events.map((event) => event.t)).toEqual([100, 100, 120]);
    expect(recording.duration).toBe(120);
    expect(recording.countAt(99)).toBe(0);
    expect(recording.countAt(100)).toBe(2);
    expect(recording.frameAt(110).files[MAIN]).toBe('ab');
  });

  it('builds every frame from checkpoints exactly as applying all the events from the start', () => {
    const events = randomSession(2_345, 7);
    const recording = new SessionRecording([file(MAIN, 'start'), file(OTHER, '')], events);
    const naive = { [MAIN]: 'start', [OTHER]: '' } as Record<string, string>;
    for (let n = 0; n <= events.length; n++) {
      if (n > 0) {
        const e = events[n - 1];
        const text = naive[e.file!];
        naive[e.file!] = text.slice(0, e.rangeOffset) + e.text + text.slice(e.rangeOffset! + e.rangeLength!);
      }
      if (n % 97 === 0 || n % SessionRecording.CHECKPOINT_EVERY <= 1 || n === events.length) {
        expect(recording.frameAfter(n).files).toEqual(naive);
      }
    }
  });

  it('seeks a 30-minute session of 20 000 events without delay', () => {
    const events = randomSession(20_000, 11, 1_800_000);
    const recording = new SessionRecording([file(MAIN, ''), file(OTHER, '')], events);
    const started = performance.now();
    for (let i = 0; i < 200; i++) {
      recording.frameAt(((i * 7919) % 1000) * 1800);
    }
    // 200 seeks; each applies at most 500 events after its checkpoint
    expect((performance.now() - started) / 200).toBeLessThan(5);
  });

  describe('on recordings of real sessions', () => {
    const recordings = {
      'warm-up: retyping, the second part, a paste, undo': warmUpTwoFiles,
      'task: pastes of own code, cut and paste': taskPastesAndCut,
      'task: typing and a paste': taskSmall,
    };
    for (const [name, data] of Object.entries(recordings)) {
      it(`gives exactly the final code: ${name}`, () => {
        const recording = new SessionRecording(data.initialFiles as ReplayFile[], data.events as ReplayEvent[]);
        expect(recording.brokenEdits).toBe(0);
        for (const [path, content] of Object.entries(data.finalCode as Record<string, string>)) {
          expect(recording.finalFiles()[path]).toBe(content);
        }
        // and the same after seeking back and forth
        recording.frameAt(recording.duration / 3);
        expect(recording.frameAt(recording.duration)).toEqual(recording.frameAfter(recording.events.length));
      });
    }
  });
});

/** Random typing, deleting and pasting in two files, with undo-like removals; offsets always fit. */
function randomSession(count: number, seed: number, duration = 600_000): ReplayEvent[] {
  let state = seed;
  const random = () => ((state = (state * 1103515245 + 12345) % 2147483648) / 2147483648);
  const lengths: Record<string, number> = { [MAIN]: 5, [OTHER]: 0 };
  const events: ReplayEvent[] = [];
  for (let i = 0; i < count; i++) {
    const path = random() < 0.8 ? MAIN : OTHER;
    const length = lengths[path];
    const offset = Math.floor(random() * (length + 1));
    const remove = random() < 0.25 ? Math.min(length - offset, Math.floor(random() * 4)) : 0;
    const text = random() < 0.05 ? 'pasted text\n' : String.fromCharCode(97 + Math.floor(random() * 26));
    lengths[path] = length - remove + text.length;
    events.push(edit((i * duration) / count, offset, remove, text, { file: path }));
  }
  return events;
}
