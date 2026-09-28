// Employer dashboard API (docs/api.md, «Работодатель»).

export type Level = 'JUNIOR' | 'MIDDLE' | 'SENIOR';
export type InviteStatus = 'CREATED' | 'STARTED' | 'COMPLETED' | 'EXPIRED' | 'REVOKED';
export type SessionStatus = 'IN_PROGRESS' | 'FINISHED' | 'EXPIRED';
export type TrustLevel = 'GREEN' | 'YELLOW' | 'RED';
export type SessionTaskStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED';
export type RunStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'ERROR' | 'TIMEOUT';

/** GET /api/employer/invites. */
export interface InviteRow {
  id: string;
  candidateLabel: string;
  targetLevel: Level;
  status: InviteStatus;
  createdAt: string;
  expiresAt: string;
  usedAt: string | null;
  sessionId: string | null;
  sessionStatus: SessionStatus | null;
  startedAt: string | null;
  finishedAt: string | null;
  preliminaryScore: number | null;
  trustLevel: TrustLevel | null;
}

/** POST /api/invites. */
export interface CreatedInvite {
  id: string;
  link: string;
  expiresAt: string;
}

export interface Indicator {
  value: unknown;
  explanation: string;
}

export interface TaskReport {
  id: string;
  orderNo: number;
  kind: 'TASK' | 'CALIBRATION';
  templateCode: string;
  templateTitle: string;
  competencies: string[];
  competencyTitles: string[];
  level: Level;
  title: string;
  status: SessionTaskStatus;
  submitStatus: RunStatus | null;
  submitCompiled: boolean | null;
  hiddenTestsPassed: number | null;
  hiddenTestsTotal: number | null;
  /** What the score counts of the last submit (docs/indicators.md); null without a submit. */
  counted: CountedTests | null;
  startedAt: string | null;
  submittedAt: string | null;
  durationSeconds: number | null;
  runs: number;
  trustLevel: TrustLevel | null;
  indicators: Record<string, Indicator> | null;
  finalCode: Record<string, string>;
}

/**
 * The tests the score counts: for a task, the hidden tests the starter fails (the others — `guards` — check that
 * nothing got broken; null when the variant does not say); for the warm-up, the tests of its part 2.
 */
export interface CountedTests {
  counted: number;
  countedPassed: number;
  guards: number | null;
  guardsBroken: number;
  unchanged: boolean;
  share: number;
}

/** An entry of scorePerTask.tasks (docs/indicators.md, «Предварительный балл»). */
export interface TaskScore {
  sessionTaskId: string;
  level: Level;
  weight?: number;
  share?: number;
  hiddenTestsPassed?: number;
  hiddenTestsTotal?: number | null;
  excluded?: string;
}

/** GET /api/employer/sessions/{id}/report. */
export interface SessionReport {
  sessionId: string;
  inviteId: string;
  candidateLabel: string;
  targetLevel: Level;
  status: SessionStatus;
  startedAt: string | null;
  finishedAt: string | null;
  preliminaryScore: number | null;
  scoreComputedAt: string | null;
  scorePerTask: { note?: string; tasks?: TaskScore[] } | null;
  trustLevel: TrustLevel | null;
  tasks: TaskReport[];
}
