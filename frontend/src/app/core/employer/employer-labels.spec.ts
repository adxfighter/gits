import { describe, expect, it } from 'vitest';

import { formatDuration, formatScore, indicatorLines } from './employer-labels';

describe('employer labels', () => {
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
