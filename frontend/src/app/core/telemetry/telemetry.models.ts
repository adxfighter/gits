/** Telemetry events and batches exactly as described in docs/telemetry.md. */

export type KeyClass =
  | 'letter'
  | 'digit'
  | 'space'
  | 'enter'
  | 'backspace'
  | 'delete'
  | 'tab'
  | 'arrow'
  | 'punct'
  | 'bracket'
  | 'modifier'
  | 'other';

export type EditSource = 'typing' | 'paste' | 'completion' | 'other';

/** An event without its time: the stream stamps {@code t} when it is recorded. */
export type TelemetryEventBody =
  | { type: 'kd'; keyClass: KeyClass; repeat: boolean }
  | { type: 'ku'; keyClass: KeyClass }
  | {
      type: 'edit';
      file: string;
      rangeOffset: number;
      rangeLength: number;
      textLength: number;
      text: string;
      isUndo: boolean;
      isRedo: boolean;
      source: EditSource;
    }
  | { type: 'cursor'; file: string; offset: number }
  | { type: 'select'; file: string; offset: number; length: number }
  | { type: 'paste' | 'copy'; file: string; length: number }
  | { type: 'completion'; accepted: boolean; insertedLength: number }
  | { type: 'visibility'; state: 'visible' | 'hidden' }
  | { type: 'focus' | 'blur' | 'run' | 'submit' }
  | { type: 'resize'; width: number; height: number };

export type TelemetryEventType = TelemetryEventBody['type'];

/** Milliseconds from the start of the task (performance.now based, continued across page reloads). */
export type TelemetryEvent = TelemetryEventBody & { t: number };

export interface TelemetryBatch {
  seq: number;
  clientTsStart: number;
  clientTsEnd: number;
  events: TelemetryEvent[];
}

export interface TelemetryAccepted {
  seq: number;
  duplicate: boolean;
  flags: Record<string, boolean>;
  beaconToken: string | null;
}
