import { TaskFileView, TaskView } from '../../../core/candidate/candidate.models';

/**
 * Part 1 of the warm-up: the sample and the file it is retyped into (CAL template, tasks/java/CAL-calibration).
 * Both are text files: never compiled, the platform compares them (POST /candidate/tasks/{id}/retyping).
 */
const REFERENCE_FILE = 'Sample.txt';
const TYPING_FILE = 'Typing.txt';
/** The heading that starts part 2 in the warm-up statement. */
const PART_2_HEADING = /^##\s*Часть\s*2\b/m;

export type CalibrationPart = 1 | 2;

/** How the warm-up is shown: part 1 (retyping, split screen) and part 2 (a short task, the normal editor). */
export interface CalibrationLayout {
  reference: TaskFileView;
  typing: TaskFileView;
  /** The files of part 2: everything except the sample and the typing file. */
  others: TaskFileView[];
  statement: Record<CalibrationPart, string>;
}

/**
 * The layout of a calibration task, or null for a regular task and for a warm-up without the retyping files (then
 * it is shown like any task).
 */
export function calibrationLayout(task: TaskView | null): CalibrationLayout | null {
  if (!task || task.kind !== 'CALIBRATION') {
    return null;
  }
  const reference = task.files.find((f) => fileName(f.path) === REFERENCE_FILE && !f.editable);
  const typing = task.files.find((f) => fileName(f.path) === TYPING_FILE && f.editable);
  if (!reference || !typing) {
    return null;
  }
  return {
    reference,
    typing,
    others: task.files.filter((f) => f !== reference && f !== typing),
    statement: splitStatement(task.statementMd),
  };
}

/**
 * The warm-up statement cut at the "## Часть 2" heading: the introduction and part 1 for part 1; part 2 and what
 * follows (how it is checked) for part 2. Without the heading both parts show the whole text.
 */
export function splitStatement(markdown: string): Record<CalibrationPart, string> {
  const match = PART_2_HEADING.exec(markdown);
  if (!match) {
    return { 1: markdown, 2: markdown };
  }
  const title = /^#\s.*$/m.exec(markdown)?.[0] ?? '';
  return {
    1: markdown.slice(0, match.index).trimEnd(),
    2: `${title}\n\n${markdown.slice(match.index)}`.trim(),
  };
}

function fileName(path: string): string {
  return path.substring(path.lastIndexOf('/') + 1);
}
