import { InviteRow, SessionReport, TaskReport } from '../../core/employer/employer.models';

// Test data of the employer pages, shaped as the API returns it (docs/api.md).

export function inviteRow(change: Partial<InviteRow> = {}): InviteRow {
  return {
    id: 'invite-1',
    candidateLabel: 'Иван П.',
    targetLevel: 'MIDDLE',
    status: 'COMPLETED',
    createdAt: '2026-09-28T08:00:00Z',
    expiresAt: '2026-10-05T08:00:00Z',
    usedAt: '2026-09-28T08:05:00Z',
    sessionId: 'session-1',
    sessionStatus: 'FINISHED',
    startedAt: '2026-09-28T08:06:00Z',
    finishedAt: '2026-09-28T09:10:00Z',
    preliminaryScore: 72.5,
    trustLevel: 'YELLOW',
    ...change,
  };
}

export function taskReport(change: Partial<TaskReport> = {}): TaskReport {
  return {
    id: 'task-2',
    orderNo: 2,
    kind: 'TASK',
    templateCode: 'T02',
    templateTitle: 'Взаимная блокировка при переводах между ресурсами',
    competencies: ['java.concurrency.locking'],
    competencyTitles: ['Блокировки и взаимные блокировки'],
    level: 'MIDDLE',
    title: 'Зависание взаиморасчётов',
    status: 'SUBMITTED',
    submitStatus: 'DONE',
    submitCompiled: true,
    hiddenTestsPassed: 3,
    hiddenTestsTotal: 5,
    startedAt: '2026-09-28T08:10:00Z',
    submittedAt: '2026-09-28T08:32:05Z',
    durationSeconds: 1325,
    runs: 4,
    trustLevel: 'YELLOW',
    indicators: {
      largestPaste: { value: 420, explanation: 'Наибольшая вставка — 420 симв.' },
      pasteRatio: { value: 0.3, explanation: 'Доля итогового кода, пришедшая вставкой: 30% (420 симв. вставлено).' },
      trustReasons: { value: ['Была крупная вставка кода (больше 300 символов).'], explanation: 'Сработавшие правила' },
    },
    finalCode: { 'src/main/java/ru/gits/task/Transfers.java': 'class Transfers {}' },
    ...change,
  };
}

export function calibrationReport(change: Partial<TaskReport> = {}): TaskReport {
  return taskReport({
    id: 'task-1',
    orderNo: 1,
    kind: 'CALIBRATION',
    templateCode: 'CAL',
    templateTitle: 'Калибровочный блок (не оценивается)',
    title: 'Разминка перед оценкой',
    level: 'JUNIOR',
    hiddenTestsPassed: 0,
    hiddenTestsTotal: null,
    trustLevel: 'YELLOW',
    indicators: {
      retyping: {
        value: { passed: true, pasteSuspected: false, similarityPercent: 99.3 },
        explanation: 'Перепечатка засчитана: текст совпадает с образцом на 99,3%.',
      },
      trustReasons: { value: ['Правило разминки'], explanation: 'Разминка' },
    },
    ...change,
  });
}

export function sessionReport(change: Partial<SessionReport> = {}): SessionReport {
  return {
    sessionId: 'session-1',
    inviteId: 'invite-1',
    candidateLabel: 'Иван П.',
    targetLevel: 'MIDDLE',
    status: 'FINISHED',
    startedAt: '2026-09-28T08:06:00Z',
    finishedAt: '2026-09-28T09:10:00Z',
    preliminaryScore: 60,
    scoreComputedAt: '2026-09-28T09:10:30Z',
    scorePerTask: { note: 'Предварительный балл, до психометрической калибровки.', tasks: [] },
    trustLevel: 'YELLOW',
    tasks: [calibrationReport(), taskReport()],
    ...change,
  };
}
