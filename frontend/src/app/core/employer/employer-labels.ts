import { InviteRow, InviteStatus, Level, TaskReport, TrustLevel } from './employer.models';

// Russian texts of the employer dashboard: statuses, trust levels, indicators, numbers and dates.

export const LEVELS: readonly Level[] = ['JUNIOR', 'MIDDLE', 'SENIOR'];

export const LEVEL_LABELS: Record<Level, string> = { JUNIOR: 'Junior', MIDDLE: 'Middle', SENIOR: 'Senior' };

export const INVITE_STATUSES: readonly InviteStatus[] = ['CREATED', 'STARTED', 'COMPLETED', 'EXPIRED', 'REVOKED'];

export const INVITE_STATUS_LABELS: Record<InviteStatus, string> = {
  CREATED: 'Ожидает кандидата',
  STARTED: 'Идёт оценка',
  COMPLETED: 'Завершено',
  EXPIRED: 'Срок ссылки истёк',
  REVOKED: 'Отозвано',
};

/** The status of a row, more precise than the invite status where the session tells more. */
export function inviteStatusText(row: InviteRow): string {
  if (row.status === 'STARTED' && row.sessionStatus === null) {
    return 'Ссылка открыта';
  }
  if (row.status === 'COMPLETED' && row.sessionStatus === 'EXPIRED') {
    return 'Завершено: время вышло';
  }
  return INVITE_STATUS_LABELS[row.status];
}

/** A trust level is always shown with a text, never by the colour alone. */
export const TRUST_LABELS: Record<TrustLevel, string> = {
  GREEN: 'Замечаний нет',
  YELLOW: 'Есть замечания',
  RED: 'Серьёзные замечания',
};

/** Indicator names in the order of the report (docs/indicators.md); trustReasons is shown on its own. */
export const INDICATOR_LABELS: Readonly<Record<string, string>> = {
  retyping: 'Перепечатка образца',
  pasteRatio: 'Доля вставок',
  largestPaste: 'Наибольшая вставка',
  externalPastes: 'Вставки не из задачи',
  focusLoss: 'Уход со вкладки',
  burstMax: 'Пиковая скорость набора',
  idleThenBurst: 'Пауза, затем всплеск',
  linearity: 'Набор сверху вниз',
  editRatio: 'Исправления',
  timeToFirstRun: 'Первый запуск тестов',
  runsCount: 'Запуски тестов',
  telemetryEvents: 'События телеметрии',
};

export interface IndicatorLine {
  name: string;
  label: string;
  explanation: string;
}

/** The indicators of a task as lines: known ones in the fixed order, then any new ones under their own name. */
export function indicatorLines(indicators: TaskReport['indicators']): IndicatorLine[] {
  if (!indicators) {
    return [];
  }
  const names = Object.keys(indicators).filter((name) => name !== 'trustReasons');
  const known = Object.keys(INDICATOR_LABELS).filter((name) => names.includes(name));
  const other = names.filter((name) => !(name in INDICATOR_LABELS)).sort();
  return [...known, ...other].map((name) => ({
    name,
    label: INDICATOR_LABELS[name] ?? name,
    explanation: indicators[name].explanation,
  }));
}

/** The rules that fired for a task (trustReasons.value). */
export function trustReasons(indicators: TaskReport['indicators']): string[] {
  const value = indicators?.['trustReasons']?.value;
  return Array.isArray(value) ? value.filter((reason): reason is string => typeof reason === 'string') : [];
}

/**
 * The result on hidden tests, or why there is none, as the score sees it (docs/indicators.md): a task left out of the
 * score ({@code excluded}) was not checked because of the platform; a check that ran no hidden test counts as 0.
 */
export function hiddenTestsText(task: TaskReport, excluded = false): string {
  if (task.kind === 'CALIBRATION') {
    return 'Не оценивается';
  }
  if (excluded) {
    return 'Не проверено: сбой проверки';
  }
  if (task.submitStatus === null) {
    return task.startedAt === null ? 'Задача не открыта' : 'Решение не отправлено';
  }
  if (task.submitStatus === 'QUEUED' || task.submitStatus === 'RUNNING') {
    return 'Проверяется…';
  }
  if (task.hiddenTestsTotal !== null) {
    return `${task.hiddenTestsPassed ?? 0} из ${task.hiddenTestsTotal}`;
  }
  if (task.submitStatus === 'TIMEOUT') {
    return '0: превышено время выполнения';
  }
  if (task.submitCompiled === false) {
    return '0: код не скомпилировался';
  }
  return task.submitStatus === 'ERROR' ? 'Не проверено: сбой проверки' : '0: скрытые тесты не выполнились';
}

const number = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 2 });

export function formatScore(score: number | null): string {
  return score === null ? '—' : number.format(score);
}

/** «12 мин 5 с», «1 ч 3 мин», «40 с». */
export function formatDuration(seconds: number | null): string {
  if (seconds === null) {
    return '—';
  }
  const total = Math.max(0, Math.round(seconds));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const rest = total % 60;
  if (hours > 0) {
    return `${hours} ч ${minutes} мин`;
  }
  return minutes > 0 ? `${minutes} мин ${rest} с` : `${rest} с`;
}

const dateTime = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(iso: string | null): string {
  return iso === null ? '—' : dateTime.format(new Date(iso));
}
