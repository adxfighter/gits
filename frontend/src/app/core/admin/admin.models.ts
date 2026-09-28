// Administrator's API (docs/api.md, «Администратор»).

import { Level, SessionStatus, TrustLevel } from '../employer/employer.models';

export type VariantStatus = 'VALIDATED' | 'DISABLED';
export type FileKind = 'STARTER' | 'READONLY' | 'VISIBLE_TEST' | 'SOLUTION' | 'HIDDEN_TEST';

export interface VariantRow {
  id: string;
  code: string;
  kind: 'TASK' | 'CALIBRATION';
  level: Level;
  status: VariantStatus;
  domain: string | null;
  validationPassed: boolean;
  failedChecks: string[];
  referenceMaxMs: number | null;
  issued: number;
}

/** GET /api/admin/tasks. */
export interface TemplateRow {
  code: string;
  title: string;
  baseLevel: Level;
  competencies: string[];
  variants: VariantRow[];
}

export interface ValidationCheck {
  id: string;
  passed: boolean;
  details: string;
}

/** GET /api/admin/tasks/variants/{id}. */
export interface VariantDetails {
  id: string;
  code: string;
  templateCode: string;
  templateTitle: string;
  kind: 'TASK' | 'CALIBRATION';
  level: Level;
  status: VariantStatus;
  timeLimitMin: number;
  statementMd: string;
  validationReport: { checks?: ValidationCheck[]; runs?: Record<string, number> } | null;
  issued: number;
  files: { path: string; kind: FileKind; editable: boolean; content: string }[];
}

/** GET /api/admin/sessions. */
export interface AdminSessionRow {
  sessionId: string;
  companyName: string;
  candidateLabel: string;
  targetLevel: Level;
  status: SessionStatus;
  startedAt: string;
  finishedAt: string | null;
  preliminaryScore: number | null;
  scoreComputedAt: string | null;
  trustLevel: TrustLevel | null;
}

/** POST /api/admin/sessions/{id}/scoring. */
export interface ScoringResult {
  scored: boolean;
  preliminaryScore: number | null;
}
