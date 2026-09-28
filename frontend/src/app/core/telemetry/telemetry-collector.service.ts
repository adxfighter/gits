import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import type * as Monaco from 'monaco-editor';
import { firstValueFrom } from 'rxjs';

import { CandidateApi } from '../candidate/candidate-api.service';
import { TaskView } from '../candidate/candidate.models';
import { MonacoLoader } from '../editor/monaco-loader.service';
import { EditSourceTracker } from './edit-source';
import { classifyKey } from './key-class';
import { TelemetryStream } from './telemetry-stream';
import { TelemetryEvent, TelemetryEventBody, TelemetryEventType } from './telemetry.models';

const FLUSH_INTERVAL_MS = 2000;
/** Cursor and selection events are thinned out to at most 10 per second. */
const CURSOR_INTERVAL_MS = 100;

export interface TelemetryDebug {
  counts: Partial<Record<TelemetryEventType, number>>;
  buffered: number;
  lastSeq: Record<string, number>;
  dropped: number;
  failures: number;
  /** Average time of an event handler, milliseconds. */
  handlerMs: number;
}

interface Throttled {
  last: number;
  timer?: ReturnType<typeof setTimeout>;
  body?: TelemetryEventBody;
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
  private handlerTotal = 0;
  private handlerCount = 0;
  private readonly counts: Partial<Record<TelemetryEventType, number>> = {};
  readonly debug = signal<TelemetryDebug>({
    counts: {},
    buffered: 0,
    lastSeq: {},
    dropped: 0,
    failures: 0,
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
    }
    this.current = stream;
  }

  /** Run and submit buttons (and Ctrl+Enter). */
  action(type: 'run' | 'submit'): void {
    this.measure(() => this.record({ type }));
  }

  /** Uploads everything buffered; awaited before a submit and before the session is finished. */
  async flushAll(): Promise<void> {
    await Promise.all([...this.streams.values()].map((stream) => stream.flush()));
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
          if (model && !e.selection.isEmpty()) {
            const start = model.getOffsetAt(e.selection.getStartPosition());
            const end = model.getOffsetAt(e.selection.getEndPosition());
            this.throttle(this.selection, { type: 'select', file: fileOf(model), offset: start, length: end - start });
          }
        }),
      ),
      editor.onDidLayoutChange((layout) =>
        this.measure(() => this.record({ type: 'resize', width: layout.width, height: layout.height })),
      ),
    ];
    const node = editor.getDomNode();
    const onPaste = (e: ClipboardEvent): void =>
      this.measure(() => {
        this.sources.paste(performance.now());
        const model = editor.getModel();
        if (model) {
          this.record({ type: 'paste', file: fileOf(model), length: e.clipboardData?.getData('text/plain').length ?? 0 });
        }
      });
    const onCopy = (): void =>
      this.measure(() => {
        const model = editor.getModel();
        const selection = editor.getSelection();
        if (model && selection) {
          this.record({ type: 'copy', file: fileOf(model), length: model.getValueInRange(selection).length });
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
      });
      if (event) {
        this.lastEdits.push(event);
      }
    }
  }

  /** Monaco runs the item's command right after inserting it: the edits just recorded came from the completion. */
  private completionAccepted(): void {
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

  private record(body: TelemetryEventBody): TelemetryEvent | null {
    const stream = this.current;
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
      clearTimeout(state.timer);
      state.timer = undefined;
      state.body = undefined;
      this.record(body);
      return;
    }
    state.body = body;
    state.timer ??= setTimeout(() => {
      state.timer = undefined;
      state.last = performance.now();
      if (state.body) {
        this.record(state.body);
        state.body = undefined;
      }
    }, CURSOR_INTERVAL_MS - (now - state.last));
  }

  /** The page is hidden or closing: whatever is buffered goes with sendBeacon, authorized by the task's token. */
  private beacon(): void {
    if (typeof navigator.sendBeacon !== 'function') {
      return;
    }
    for (const stream of this.streams.values()) {
      const body = stream.beaconBody();
      if (body) {
        navigator.sendBeacon(this.api.telemetryUrl(stream.taskId), new Blob([body], { type: 'text/plain;charset=UTF-8' }));
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
      handlerMs: this.handlerCount ? this.handlerTotal / this.handlerCount : 0,
    });
  }
}

/** Path of a task file from its model URI (inmemory://task/{taskId}/{path}). */
function fileOf(model: Monaco.editor.ITextModel): string {
  return model.uri.path.split('/').slice(2).join('/');
}
