import { ReplayEvent, ReplayFile } from './replay.models';

/** The state of the candidate's files at one moment of the recording. */
export interface ReplayFrame {
  /** Text of every file: the files the candidate saw, changed by the edits so far. */
  readonly files: Readonly<Record<string, string>>;
  /** The file the candidate worked in: that of the last edit, cursor or selection. */
  readonly activeFile: string | null;
  /** The cursor after the last edit or cursor move, in the active file. */
  readonly cursor: { readonly file: string; readonly offset: number } | null;
}

/**
 * The recorded work on one task, replayable to any moment. Files start as the candidate saw them
 * ({@code initialFiles}) and change by the {@code edit} events in their recorded order: undo and redo are recorded
 * as ordinary edits, several changes of one editor event come in an order that can be applied one after another.
 *
 * Seeking must stay instant on a 30-minute session with 20 000 events: the state is kept every
 * {@link CHECKPOINT_EVERY} events, and a frame is built from the nearest checkpoint before it.
 */
export class SessionRecording {
  static readonly CHECKPOINT_EVERY = 500;

  /** Events in their recorded order, with times made non-decreasing (a later event is never earlier). */
  readonly events: readonly ReplayEvent[];
  /** Time of the last event, ms. */
  readonly duration: number;
  /** Edits that did not fit the text (the recording is incomplete there); their text is applied clamped. */
  readonly brokenEdits: number;
  /** Editable paths first, then the read-only files and the tests, as the candidate saw them. */
  readonly paths: readonly string[];

  private readonly checkpoints: ReplayFrame[] = [];

  constructor(initialFiles: readonly ReplayFile[], events: readonly ReplayEvent[]) {
    let time = 0;
    this.events = events.map((event) => {
      time = Math.max(time, event.t);
      return time === event.t ? event : { ...event, t: time };
    });
    this.duration = time;
    const ordered = [...initialFiles].sort((a, b) => Number(b.editable) - Number(a.editable));
    // a file the candidate edited that is not among the task files still gets a tab
    const known = new Set(ordered.map((file) => file.path));
    const extra = [
      ...new Set(
        this.events
          .filter((event) => event.type === 'edit' && event.file !== undefined && !known.has(event.file))
          .map((event) => event.file!),
      ),
    ];
    this.paths = [...ordered.map((file) => file.path), ...extra];

    const files: Record<string, string> = {};
    ordered.forEach((file) => (files[file.path] = file.content));
    // the candidate starts where the first edit, cursor or selection is; without them — in the first editable file
    const first =
      this.events.find((event) => event.file !== undefined && FILE_EVENTS.has(event.type))?.file ??
      ordered.find((file) => file.editable)?.path ??
      ordered[0]?.path ??
      null;
    const state: MutableFrame = { files, activeFile: first, cursor: null };
    let broken = 0;
    this.events.forEach((event, index) => {
      if (index % SessionRecording.CHECKPOINT_EVERY === 0) {
        this.checkpoints.push(freeze(state));
      }
      broken += apply(state, event) ? 0 : 1;
    });
    this.brokenEdits = broken;
    this.checkpoints.push(freeze(state));
  }

  /** The state after the first {@code count} events (0 — the start, {@code events.length} — the end). */
  frameAfter(count: number): ReplayFrame {
    const n = Math.max(0, Math.min(this.events.length, Math.floor(count)));
    if (n === this.events.length) {
      return this.checkpoints[this.checkpoints.length - 1];
    }
    const base = Math.floor(n / SessionRecording.CHECKPOINT_EVERY);
    const checkpoint = this.checkpoints[base];
    const from = base * SessionRecording.CHECKPOINT_EVERY;
    if (from === n) {
      return checkpoint;
    }
    const state: MutableFrame = { ...checkpoint, files: { ...checkpoint.files } };
    for (let i = from; i < n; i++) {
      apply(state, this.events[i]);
    }
    return state;
  }

  /** How many events happened at or before time {@code t} (ms). */
  countAt(t: number): number {
    let low = 0;
    let high = this.events.length;
    while (low < high) {
      const middle = (low + high) >>> 1;
      if (this.events[middle].t <= t) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }
    return low;
  }

  frameAt(t: number): ReplayFrame {
    return this.frameAfter(this.countAt(t));
  }

  /** The files at the end of the recording. */
  finalFiles(): Readonly<Record<string, string>> {
    return this.frameAfter(this.events.length).files;
  }
}

const FILE_EVENTS = new Set(['edit', 'cursor', 'select']);

interface MutableFrame {
  files: Record<string, string>;
  activeFile: string | null;
  cursor: { file: string; offset: number } | null;
}

function freeze(state: MutableFrame): ReplayFrame {
  // strings are immutable: a shallow copy of the map is a full snapshot
  return { files: { ...state.files }, activeFile: state.activeFile, cursor: state.cursor };
}

/** Applies one event; false when an edit did not fit the text it was recorded against. */
function apply(state: MutableFrame, event: ReplayEvent): boolean {
  if (event.type === 'edit' && event.file !== undefined) {
    const text = state.files[event.file] ?? '';
    const offset = event.rangeOffset ?? 0;
    const length = event.rangeLength ?? 0;
    const fits = offset >= 0 && length >= 0 && offset + length <= text.length;
    const start = Math.min(Math.max(offset, 0), text.length);
    const end = Math.min(start + Math.max(length, 0), text.length);
    const inserted = event.text ?? '';
    state.files[event.file] = text.slice(0, start) + inserted + text.slice(end);
    state.activeFile = event.file;
    state.cursor = { file: event.file, offset: start + inserted.length };
    return fits;
  }
  if ((event.type === 'cursor' || event.type === 'select') && event.file !== undefined) {
    state.activeFile = event.file;
    state.cursor = { file: event.file, offset: event.offset ?? 0 };
  }
  return true;
}
