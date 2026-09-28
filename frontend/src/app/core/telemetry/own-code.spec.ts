import { describe, expect, it } from 'vitest';

import { judgePaste, withoutRange, withoutSpace } from './own-code';

const CODE = `public final class Visits {

    public int total(List<Visit> visits) {
        return visits.stream().mapToInt(Visit::minutes).sum();
    }
}`;
const STATEMENT = 'Реализуйте метод Visits.total(visits), который возвращает суммарное время визитов.';

describe('judgePaste', () => {
  it('accepts the candidate\'s own code, whatever the indentation', () => {
    const verdict = judgePaste('return visits.stream()\n  .mapToInt(Visit::minutes).sum();', [CODE, STATEMENT]);
    expect(verdict.own).toBe(true);
    expect(verdict.suspicious).toBe(false);
  });

  it('accepts text from the task statement', () => {
    expect(judgePaste('возвращает суммарное время визитов', [CODE, STATEMENT]).suspicious).toBe(false);
  });

  it('flags ten or more characters found neither in the code nor in the statement', () => {
    const verdict = judgePaste('Collectors.groupingBy(Visit::doctor)', [CODE, STATEMENT]);
    expect(verdict.own).toBe(false);
    expect(verdict.suspicious).toBe(true);
    expect(verdict.meaningful).toBe(36);
  });

  it('does not flag a short paste or whitespace', () => {
    expect(judgePaste('foo(bar)', [CODE]).suspicious).toBe(false);
    expect(judgePaste('      \n\t', [CODE]).suspicious).toBe(false);
  });

  it('checks a menu paste against the file as it was before the insertion', () => {
    const pasted = 'Collectors.groupingBy(Visit::doctor)';
    const after = CODE.slice(0, 20) + pasted + CODE.slice(20);
    // the inserted text itself is in the file now: it must not count as its own source
    expect(judgePaste(pasted, [withoutRange(after, 20, pasted.length)]).suspicious).toBe(true);
  });
});

describe('withoutSpace', () => {
  it('removes every kind of space', () => {
    expect(withoutSpace(' a\u00A0b\u200Bc\n\td ')).toBe('abcd');
  });
});
