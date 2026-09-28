/** Models of the candidate API (see docs/api.md). Dates are ISO-8601 strings from the server. */

export type Level = 'JUNIOR' | 'MIDDLE' | 'SENIOR';
export type InviteStatus = 'CREATED' | 'STARTED' | 'COMPLETED' | 'EXPIRED' | 'REVOKED';
export type SessionStatus = 'IN_PROGRESS' | 'FINISHED' | 'EXPIRED';
export type TaskKind = 'TASK' | 'CALIBRATION';
export type TaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED';
export type FileKind = 'STARTER' | 'READONLY' | 'VISIBLE_TEST';
export type RunMode = 'RUN' | 'SUBMIT';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'ERROR' | 'TIMEOUT';

export interface CandidateView {
  candidateLabel: string;
  targetLevel: Level;
  status: InviteStatus;
  consentGiven: boolean;
}

export interface ConsentView {
  version: number;
  text: string;
  accepted: boolean;
}

export interface TaskSummary {
  id: string;
  orderNo: number;
  kind: TaskKind;
  title: string;
  status: TaskStatus;
  timeLimitMin: number;
  runsUsed: number;
  runsLimit: number;
}

export interface SessionView {
  id: string;
  status: SessionStatus;
  startedAt: string;
  deadline: string;
  remainingSeconds: number;
  tasks: TaskSummary[];
}

export interface TaskFileView {
  path: string;
  kind: FileKind;
  editable: boolean;
  content: string;
}

export interface TaskView {
  id: string;
  orderNo: number;
  kind: TaskKind;
  title: string;
  status: TaskStatus;
  statementMd: string;
  timeLimitMin: number;
  files: TaskFileView[];
  /** Current code of the editable files: path to content. */
  code: Record<string, string>;
  codeSavedAt: string | null;
  runsUsed: number;
  runsLimit: number;
  /** One-time token for a sendBeacon telemetry batch (docs/telemetry.md). */
  beaconToken: string | null;
  /** Where the task's telemetry continues after a reload: next seq and the end of the time scale. */
  telemetryNextSeq: number;
  telemetryLastT: number;
}

export interface RunAccepted {
  runId: string;
  mode: RunMode;
  status: RunStatus;
}

export interface TestView {
  name: string;
  status: string;
  message: string | null;
}

/** For SUBMIT only the counts are filled: hidden tests are never listed. */
export interface RunView {
  id: string;
  mode: RunMode;
  status: RunStatus;
  createdAt: string;
  finishedAt: string | null;
  compiled: boolean | null;
  testsTotal: number | null;
  testsPassed: number | null;
  compileOutput: string | null;
  tests: TestView[];
  output: string | null;
  durationMs: number | null;
}

export const FINAL_RUN_STATUSES: readonly RunStatus[] = ['DONE', 'ERROR', 'TIMEOUT'];
