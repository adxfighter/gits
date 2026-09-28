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
  /** The worst level of the rules that fired on this indicator: the line is highlighted; null — none fired. */
  flag: TrustLevel | null;
}

/** A rule that fired (trustRules.value): its level (null in reports scored before levels were kept), text, indicators. */
export interface FiredRule {
  level: TrustLevel | null;
  reason: string;
  indicators: string[];
}

/** The fired rules of a task; before trustRules existed, the reasons alone, without levels. */
export function firedRules(indicators: TaskReport['indicators']): FiredRule[] {
  const value = indicators?.['trustRules']?.value;
  if (Array.isArray(value)) {
    return value.filter(
      (rule): rule is FiredRule => !!rule && typeof rule === 'object' && typeof (rule as FiredRule).reason === 'string',
    );
  }
  return trustReasons(indicators).map((reason) => ({ level: null, reason, indicators: [] }));
}

const LEVEL_ORDER: Record<TrustLevel, number> = { GREEN: 0, YELLOW: 1, RED: 2 };

/** The indicators of a task as lines: known ones in the fixed order, then any new ones under their own name. */
export function indicatorLines(indicators: TaskReport['indicators']): IndicatorLine[] {
  if (!indicators) {
    return [];
  }
  const names = Object.keys(indicators).filter((name) => name !== 'trustReasons' && name !== 'trustRules');
  const rules = firedRules(indicators);
  const known = Object.keys(INDICATOR_LABELS).filter((name) => names.includes(name));
  const other = names.filter((name) => !(name in INDICATOR_LABELS)).sort();
  return [...known, ...other].map((name) => ({
    name,
    label: INDICATOR_LABELS[name] ?? name,
    explanation: indicators[name].explanation,
    flag: rules
      .filter((rule): rule is FiredRule & { level: TrustLevel } => rule.level !== null && rule.indicators.includes(name))
      .reduce<TrustLevel | null>((worst, rule) => (worst && LEVEL_ORDER[worst] >= LEVEL_ORDER[rule.level] ? worst : rule.level), null),
  }));
}

/** «ещё 2 проверяют, что ничего не сломано — прошли» under the hidden tests; null when there is nothing to say. */
export function guardsText(task: TaskReport): string | null {
  const counted = task.counted;
  if (task.kind !== 'TASK' || !counted || counted.guards === null || counted.guards === 0 || counted.unchanged) {
    return null;
  }
  return counted.guardsBroken > 0
    ? `Проверок «ничего не сломано»: ${counted.guards}, сломано ${counted.guardsBroken}`
    : `Ещё ${counted.guards} ${plural(counted.guards, 'скрытый тест проверяет', 'скрытых теста проверяют', 'скрытых тестов проверяют')}, что ничего не сломано: прошли`;
}

/** Russian plural: 1 тест, 2 теста, 5 тестов (and 21 тест, 22 теста, 11 тестов). */
export function plural(n: number, one: string, few: string, many: string): string {
  const tens = n % 100;
  const units = n % 10;
  if (tens >= 11 && tens <= 14) {
    return many;
  }
  return units === 1 ? one : units >= 2 && units <= 4 ? few : many;
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
    if (task.submitStatus === null) {
      return 'Не отправлена';
    }
    if (excluded || task.submitStatus === 'ERROR') {
      return 'Не проверено: сбой проверки';
    }
    if (task.submitStatus === 'QUEUED' || task.submitStatus === 'RUNNING') {
      return 'Проверяется…';
    }
    const counted = task.counted;
    return counted && counted.counted > 0 ? `${counted.countedPassed} из ${counted.counted} (тесты части 2)` : '0 (тесты части 2)';
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
  const counted = task.counted;
  if (counted?.unchanged) {
    return '0: код не изменён';
  }
  if (counted && counted.guardsBroken > 0) {
    return `0: сломано проверок «ничего не сломано» — ${counted.guardsBroken}`;
  }
  if (counted && counted.guards !== null && counted.counted > 0) {
    return `исправлено ${counted.countedPassed} из ${counted.counted}`;
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
