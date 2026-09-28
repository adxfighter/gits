import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import type * as Monaco from 'monaco-editor';
import { firstValueFrom } from 'rxjs';

import { CandidateApi } from '../candidate/candidate-api.service';
import { TaskView } from '../candidate/candidate.models';
import { MonacoLoader } from '../editor/monaco-loader.service';
import { EditSourceTracker } from './edit-source';
import { EXTERNAL_PASTE_MIN, PasteVerdict, judgePaste, withoutSpace } from './own-code';
import { classifyKey } from './key-class';
import { TelemetryStream } from './telemetry-stream';
import { TelemetryEvent, TelemetryEventBody, TelemetryEventType } from './telemetry.models';

const FLUSH_INTERVAL_MS = 2000;
/** Cursor and selection events are thinned out to at most 10 per second. */
const CURSOR_INTERVAL_MS = 100;
/** Texts copied or cut in the editor that a later paste may bring back (the candidate's own). */
const COPIED_KEPT = 20;

/** The texts a paste may legitimately come from; {@code exclude} is the part just inserted into a file. */
export type SourceLookup = (exclude?: { file: string; offset: number; length: number }) => string[];

/** A paste of text found neither in the task's code nor in its statement, shown to the candidate at once. */
export interface CopySuspicion {
  at: number;
  length: number;
}

export interface TelemetryDebug {
  counts: Partial<Record<TelemetryEventType, number>>;
  buffered: number;
  lastSeq: Record<string, number>;
  dropped: number;
  failures: number;
  /** Beacons the browser refused (e.g. over its size limit). */
  beaconFailed: number;
  /** Average time of an event handler, milliseconds. */
  handlerMs: number;
}

interface Throttled {
  last: number;
  timer?: ReturnType<typeof setTimeout>;
  body?: TelemetryEventBody;
  /** The stream of the task the pending event belongs to, even if the candidate switches tasks meanwhile. */
  stream?: TelemetryStream | null;
}

/**
 * Collects the candidate's input telemetry on the workspace page (docs/telemetry.md) and uploads it: one stream
 * per task, batches every 2 s or at 1 000 events, sendBeacon when the page is hidden or closed. Provided by the
 * session page only, so nothing is collected before the consent or outside /c/session. Key events carry only the
 * key class, never the key.
 */
@Injectable()
export class TelemetryCollector {
  private readonly api = inject(CandidateApi);
  private readonly loader = inject(MonacoLoader);
  private readonly streams = new Map<string, TelemetryStream>();
  private current: TelemetryStream | null = null;
  private readonly sources = new EditSourceTracker();
  private readonly cleanups: (() => void)[] = [];
  private readonly cursor: Throttled = { last: -Infinity };
  private readonly selection: Throttled = { last: -Infinity };
  /** Edits of the content change being handled, re-marked if a completion command follows. */
  private lastEdits: TelemetryEvent[] = [];
  /** A DOM paste event preceded the current paste (Monaco's context menu pastes without one). */
  private domPaste = false;
  /** Verdict of the DOM paste in progress, for the edits it produces. */
  private pasteOwn = false;
  private beaconFailed = 0;
  private sourceLookup: SourceLookup = () => [];
  private readonly copied: string[] = [];
  /** A large insertion without a paste event, judged after Monaco has said whether it was a completion. */
  private pendingInsert: { events: TelemetryEvent[]; file: string; offset: number; text: string } | null = null;
  private calibration = false;
  /** The last suspicious paste in a regular task; the warm-up has its own retyping check. */
  readonly copySuspicion = signal<CopySuspicion | null>(null);
  private handlerTotal = 0;
  private handlerCount = 0;
  private readonly counts: Partial<Record<TelemetryEventType, number>> = {};
  readonly debug = signal<TelemetryDebug>({
    counts: {},
    buffered: 0,
    lastSeq: {},
    dropped: 0,
    failures: 0,
    beaconFailed: 0,
    handlerMs: 0,
  });

  constructor() {
    const timer = setInterval(() => void this.flushAll(), FLUSH_INTERVAL_MS);
    const onFocus = (): void => this.measure(() => this.record({ type: 'focus' }));
    const onBlur = (): void => this.measure(() => this.record({ type: 'blur' }));
    const onVisibility = (): void => {
      const state = document.visibilityState === 'hidden' ? 'hidden' : 'visible';
      this.measure(() => this.record({ type: 'visibility', state }));
      if (state === 'hidden') {
        this.beacon();
      }
    };
    const onPageHide = (): void => this.beacon();
    window.addEventListener('focus', onFocus);
    window.addEventListener('blur', onBlur);
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pagehide', onPageHide);
    this.cleanups.push(
      () => clearInterval(timer),
      () => window.removeEventListener('focus', onFocus),
      () => window.removeEventListener('blur', onBlur),
      () => document.removeEventListener('visibilitychange', onVisibility),
      () => window.removeEventListener('pagehide', onPageHide),
      this.loader.onCompletionAccepted(() => this.measure(() => this.completionAccepted())),
    );
    inject(DestroyRef).onDestroy(() => {
      this.beacon();
      this.cleanups.forEach((cleanup) => cleanup());
      clearTimeout(this.cursor.timer);
      clearTimeout(this.selection.timer);
    });
  }

  /** The candidate opened a task: its events go to the task's stream, which continues where the server left off. */
  startTask(task: TaskView): void {
    let stream = this.streams.get(task.id);
    if (!stream) {
      stream = new TelemetryStream({
        taskId: task.id,
        nextSeq: task.telemetryNextSeq ?? 0,
        lastT: task.telemetryLastT ?? 0,
        beaconToken: task.beaconToken,
        send: (taskId, batch) => firstValueFrom(this.api.telemetry(taskId, batch)),
        now: () => performance.now(),
      });
      this.streams.set(task.id, stream);
      this.current = stream;
      // the state at the start: a reloaded page is not left "away" by the hidden state its predecessor reported
      this.record({ type: 'visibility', state: document.visibilityState === 'hidden' ? 'hidden' : 'visible' });
      this.record({ type: document.hasFocus() ? 'focus' : 'blur' });
    }
    this.current = stream;
    this.calibration = task.kind === 'CALIBRATION';
  }

  /** Where the page takes the texts a paste is checked against: the task's files and its statement. */
  setSourceLookup(lookup: SourceLookup): void {
    this.sourceLookup = lookup;
  }

  /** Run and submit buttons (and Ctrl+Enter). */
  action(type: 'run' | 'submit'): void {
    this.measure(() => this.record({ type }));
  }

  /**
   * Uploads everything buffered. {@code force} (before a submit, the end of the session or the deadline) also
   * skips a running retry delay.
   */
  async flushAll(force = false): Promise<void> {
    await Promise.all([...this.streams.values()].map((stream) => stream.flush(force)));
    this.publish();
  }

  /** Subscribes to the editor: keys, content changes, cursor, selection, clipboard and size. */
  attachEditor(editor: Monaco.editor.IStandaloneCodeEditor): void {
    const disposables = [
      editor.onKeyDown((e) =>
        this.measure(() =>
          this.record({
            type: 'kd',
            keyClass: classifyKey(e.browserEvent.code, e.browserEvent.key),
            repeat: e.browserEvent.repeat,
          }),
        ),
      ),
      editor.onKeyUp((e) =>
        this.measure(() =>
          this.record({ type: 'ku', keyClass: classifyKey(e.browserEvent.code, e.browserEvent.key) }),
        ),
      ),
      editor.onDidChangeModelContent((e) => this.measure(() => this.edited(editor, e))),
      editor.onDidChangeCursorPosition((e) =>
        this.measure(() => {
          const model = editor.getModel();
          if (model) {
            this.throttle(this.cursor, { type: 'cursor', file: fileOf(model), offset: model.getOffsetAt(e.position) });
          }
        }),
      ),
      editor.onDidChangeCursorSelection((e) =>
        this.measure(() => {
          const model = editor.getModel();
          if (model && e.selection.isEmpty()) {
            // a collapsed selection is not sent later as if it were still there
            this.cancel(this.selection);
          } else if (model) {
            const start = model.getOffsetAt(e.selection.getStartPosition());
            const end = model.getOffsetAt(e.selection.getEndPosition());
            this.throttle(this.selection, { type: 'select', file: fileOf(model), offset: start, length: end - start });
          }
        }),
      ),
      editor.onDidPaste(() =>
        this.measure(() => {
          const model = editor.getModel();
          if (!model) {
            return;
          }
          if (!this.domPaste) {
            // pasted from the context menu: no DOM event came first, so the edits were taken for typing
            this.pendingInsert = null;
            const edits = this.lastEdits.filter((event) => event.type === 'edit');
            const text = edits.map((event) => ('text' in event ? event.text : '')).join('');
            const first = edits[0];
            const verdict = this.judge(text, first && 'rangeOffset' in first
              ? { file: fileOf(model), offset: first.rangeOffset, length: text.length } : undefined);
            for (const event of edits) {
              if (event.type === 'edit') {
                event.source = 'paste';
                event.ownCode = verdict.own;
              }
            }
            this.record({ type: 'paste', file: fileOf(model), length: text.length, ownCode: verdict.own });
            this.warn(verdict);
          }
          this.domPaste = false;
        }),
      ),
      editor.onDidLayoutChange((layout) =>
        this.measure(() => this.record({ type: 'resize', width: layout.width, height: layout.height })),
      ),
    ];
    // the container, not getDomNode(): that one is null while the editor has no model yet (at attach time)
    const node = editor.getContainerDomNode();
    const onPaste = (e: ClipboardEvent): void =>
      this.measure(() => {
        const text = e.clipboardData?.getData('text/plain') ?? '';
        const model = editor.getModel();
        if (text.length === 0 || !model) {
          // nothing textual is pasted (e.g. an image): no change follows
          return;
        }
        this.sources.paste(performance.now());
        this.domPaste = true;
        // checked before Monaco inserts it: the files are still as they were
        const verdict = this.judge(text);
        this.pasteOwn = verdict.own;
        this.record({ type: 'paste', file: fileOf(model), length: text.length, ownCode: verdict.own });
        this.warn(verdict);
      });
    const onCopy = (e: Event): void =>
      this.measure(() => {
        if (e.type === 'cut') {
          this.sources.cut(performance.now());
        }
        const model = editor.getModel();
        const selection = editor.getSelection();
        if (model && selection) {
          const text = model.getValueInRange(selection);
          this.remember(text);
          this.record({ type: 'copy', file: fileOf(model), length: text.length });
        }
      });
    // capture: runs before Monaco applies the paste, so the change that follows is known to be a paste
    node?.addEventListener('paste', onPaste, true);
    node?.addEventListener('copy', onCopy, true);
    node?.addEventListener('cut', onCopy, true);
    this.cleanups.push(
      () => disposables.forEach((d) => d.dispose()),
      () => node?.removeEventListener('paste', onPaste, true),
      () => node?.removeEventListener('copy', onCopy, true),
      () => node?.removeEventListener('cut', onCopy, true),
    );
  }

  private edited(editor: Monaco.editor.IStandaloneCodeEditor, e: Monaco.editor.IModelContentChangedEvent): void {
    const model = editor.getModel();
    if (!model) {
      return;
    }
    const source = e.isUndoing || e.isRedoing ? 'other' : this.sources.sourceOf(performance.now());
    const file = fileOf(model);
    this.lastEdits = [];
    for (const change of e.changes) {
      const event = this.record({
        type: 'edit',
        file,
        rangeOffset: change.rangeOffset,
        rangeLength: change.rangeLength,
        textLength: change.text.length,
        text: change.text,
        isUndo: e.isUndoing,
        isRedo: e.isRedoing,
        source,
        ...(source === 'paste' ? { ownCode: this.pasteOwn } : {}),
      });
      if (event) {
        this.lastEdits.push(event);
      }
    }
    const change = e.changes[0];
    if (source === 'typing' && e.changes.length === 1 && withoutSpace(change.text).length >= EXTERNAL_PASTE_MIN) {
      // text that arrived without a paste event (dragged in, inserted by an extension): judged once Monaco has
      // run a completion's command, if it was one
      this.pendingInsert = { events: [...this.lastEdits], file, offset: change.rangeOffset, text: change.text };
      queueMicrotask(() => this.measure(() => this.judgeInsert()));
    }
  }

  private judgeInsert(): void {
    const insert = this.pendingInsert;
    this.pendingInsert = null;
    if (!insert) {
      return;
    }
    const verdict = this.judge(insert.text, { file: insert.file, offset: insert.offset, length: insert.text.length });
    for (const event of insert.events) {
      if (event.type === 'edit') {
        event.source = 'paste';
        event.ownCode = verdict.own;
      }
    }
    this.record({ type: 'paste', file: insert.file, length: insert.text.length, ownCode: verdict.own });
    this.warn(verdict);
  }

  /**
   * Whether a pasted text is the candidate's own: in the task's files or statement, or copied in the editor
   * before. In the warm-up nothing is own: retyping means typing (its check is on the server).
   */
  private judge(text: string, exclude?: { file: string; offset: number; length: number }): PasteVerdict {
    if (this.calibration) {
      const meaningful = withoutSpace(text).length;
      return { meaningful, own: false, suspicious: false };
    }
    return judgePaste(text, [...this.sourceLookup(exclude), ...this.copied]);
  }

  private warn(verdict: PasteVerdict): void {
    if (verdict.suspicious) {
      this.copySuspicion.set({ at: Date.now(), length: verdict.meaningful });
    }
  }

  private remember(text: string): void {
    if (withoutSpace(text).length >= EXTERNAL_PASTE_MIN) {
      this.copied.push(text);
      if (this.copied.length > COPIED_KEPT) {
        this.copied.shift();
      }
    }
  }

  /** Monaco runs the item's command right after inserting it: the edits just recorded came from the completion. */
  private completionAccepted(): void {
    // the insertion came from a completion, not from outside
    this.pendingInsert = null;
    let inserted = 0;
    for (const event of this.lastEdits) {
      if (event.type === 'edit') {
        event.source = 'completion';
        inserted += event.textLength;
      }
    }
    this.lastEdits = [];
    this.record({ type: 'completion', accepted: true, insertedLength: inserted });
  }

  private record(body: TelemetryEventBody, stream: TelemetryStream | null = this.current): TelemetryEvent | null {
    if (!stream) {
      return null;
    }
    const event = stream.record(body);
    if (event) {
      this.counts[body.type] = (this.counts[body.type] ?? 0) + 1;
      if (stream.full()) {
        void stream.flush().then(() => this.publish());
      }
    }
    return event;
  }

  /** At most one event per interval; the latest position is sent at the end of a burst. */
  private throttle(state: Throttled, body: TelemetryEventBody): void {
    const now = performance.now();
    if (now - state.last >= CURSOR_INTERVAL_MS) {
      state.last = now;
      this.cancel(state);
      this.record(body);
      return;
    }
    if (state.stream !== undefined && state.stream !== this.current) {
      // the pending event belongs to the task left a moment ago: it goes there now
      this.flushThrottled(state);
    }
    state.body = body;
    state.stream = this.current;
    state.timer ??= setTimeout(() => this.flushThrottled(state), CURSOR_INTERVAL_MS - (now - state.last));
  }

  private flushThrottled(state: Throttled): void {
    clearTimeout(state.timer);
    state.timer = undefined;
    state.last = performance.now();
    if (state.body) {
      this.record(state.body, state.stream ?? null);
    }
    state.body = undefined;
    state.stream = undefined;
  }

  private cancel(state: Throttled): void {
    clearTimeout(state.timer);
    state.timer = undefined;
    state.body = undefined;
    state.stream = undefined;
  }

  /** The page is hidden or closing: whatever is buffered goes with sendBeacon, authorized by the task's token. */
  private beacon(): void {
    if (typeof navigator.sendBeacon !== 'function') {
      return;
    }
    for (const stream of this.streams.values()) {
      const body = stream.beaconBody();
      if (body) {
        const queued = navigator.sendBeacon(
          this.api.telemetryUrl(stream.taskId),
          new Blob([body], { type: 'text/plain;charset=UTF-8' }),
        );
        if (!queued) {
          this.beaconFailed++;
        }
      }
    }
  }

  private measure(handler: () => void): void {
    const start = performance.now();
    handler();
    this.handlerTotal += performance.now() - start;
    this.handlerCount++;
  }

  private publish(): void {
    const streams = [...this.streams.values()];
    this.debug.set({
      counts: { ...this.counts },
      buffered: streams.reduce((sum, s) => sum + s.buffered(), 0),
      lastSeq: Object.fromEntries(streams.map((s) => [s.taskId, s.lastSeq()])),
      dropped: streams.reduce((sum, s) => sum + s.stats.dropped, 0),
      failures: streams.reduce((sum, s) => sum + s.stats.failures, 0),
      beaconFailed: this.beaconFailed,
      handlerMs: this.handlerCount ? this.handlerTotal / this.handlerCount : 0,
    });
  }
}

/** Path of a task file from its model URI (inmemory://task/{taskId}/{path}). */
function fileOf(model: Monaco.editor.ITextModel): string {
  return model.uri.path.split('/').slice(2).join('/');
}
