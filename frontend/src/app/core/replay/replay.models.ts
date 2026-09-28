// GET /api/employer/session-tasks/{id}/replay (docs/api.md, «Воспроизведение»); event fields — docs/telemetry.md.

import { RunStatus, SessionTaskStatus } from '../employer/employer.models';

export interface ReplayFile {
  path: string;
  kind: 'STARTER' | 'READONLY' | 'VISIBLE_TEST';
  editable: boolean;
  content: string;
}

export interface ReplayRun {
  id: string;
  mode: 'RUN' | 'SUBMIT';
  status: RunStatus;
  createdAt: string;
  finishedAt: string | null;
  /** Creation time in ms after the task was opened on the server: the approximate place on the timeline. */
  offsetMs: number | null;
  compiled: boolean | null;
  testsTotal: number | null;
  testsPassed: number | null;
}

/** A telemetry event of the replay: the fields of its type (docs/telemetry.md) plus the batch seq. */
export interface ReplayEvent {
  t: number;
  type: string;
  seq?: number;
  file?: string;
  rangeOffset?: number;
  rangeLength?: number;
  textLength?: number;
  text?: string;
  isUndo?: boolean;
  isRedo?: boolean;
  source?: 'typing' | 'paste' | 'completion' | 'other';
  ownCode?: boolean;
  offset?: number;
  length?: number;
  state?: 'visible' | 'hidden';
  accepted?: boolean;
  insertedLength?: number;
}

export interface ReplayPage {
  sessionTaskId: string;
  sessionId: string;
  status: SessionTaskStatus;
  startedAt: string | null;
  submittedAt: string | null;
  initialFiles: ReplayFile[];
  runs: ReplayRun[];
  events: ReplayEvent[];
  fromSeq: number;
  nextSeq: number | null;
}
