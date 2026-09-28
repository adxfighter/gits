import { describe, expect, it } from 'vitest';

import { taskReport } from '../../pages/employer/employer-fixtures';
import { TRUST_LABELS, formatDuration, formatScore, hiddenTestsText, indicatorLines } from './employer-labels';

describe('employer labels', () => {
  it('explains hidden tests the way the score counts them', () => {
    expect(hiddenTestsText(taskReport())).toBe('3 из 5');
    // compiled and checked, but no hidden test ran: 0 in the score
    expect(hiddenTestsText(taskReport({ hiddenTestsPassed: 0, hiddenTestsTotal: null }))).toBe('0: скрытые тесты не выполнились');
    expect(hiddenTestsText(taskReport({ submitStatus: 'ERROR', submitCompiled: null, hiddenTestsTotal: null }))).toBe('Не проверено: сбой проверки');
    // a check stuck in the queue is left out of the score
    expect(hiddenTestsText(taskReport({ submitStatus: 'QUEUED', hiddenTestsTotal: null }))).toBe('Проверяется…');
    expect(hiddenTestsText(taskReport({ submitStatus: 'QUEUED', hiddenTestsTotal: null }), true)).toBe('Не проверено: сбой проверки');
    expect(hiddenTestsText(taskReport({ submitStatus: null, startedAt: '2026-09-28T08:10:00Z' }))).toBe('Решение не отправлено');
  });

  it('names every trust level in words', () => {
    expect(TRUST_LABELS).toEqual({ GREEN: 'Замечаний нет', YELLOW: 'Есть замечания', RED: 'Серьёзные замечания' });
  });

  it('formats durations the Russian way', () => {
    expect(formatDuration(40)).toBe('40 с');
    expect(formatDuration(725)).toBe('12 мин 5 с');
    expect(formatDuration(3780)).toBe('1 ч 3 мин');
    expect(formatDuration(null)).toBe('—');
  });

  it('formats the score with a decimal comma', () => {
    expect(formatScore(25.56)).toBe('25,56');
    expect(formatScore(100)).toBe('100');
    expect(formatScore(null)).toBe('—');
  });

  it('puts known indicators in the report order and keeps unknown ones under their name', () => {
    const lines = indicatorLines({
      zNew: { value: 1, explanation: 'новый' },
      runsCount: { value: 2, explanation: 'Запусков видимых тестов: 2.' },
      pasteRatio: { value: 0, explanation: 'Доля 0%' },
      trustReasons: { value: [], explanation: '' },
    });
    expect(lines.map((line) => line.name)).toEqual(['pasteRatio', 'runsCount', 'zNew']);
    expect(lines[2].label).toBe('zNew');
    expect(indicatorLines(null)).toEqual([]);
  });
});
