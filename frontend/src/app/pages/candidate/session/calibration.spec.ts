import { describe, expect, it } from 'vitest';

import { TaskFileView, TaskView } from '../../../core/candidate/candidate.models';
import { calibrationLayout, splitStatement } from './calibration';

const STATEMENT = `# Разминка перед оценкой

Этот блок немного влияет на оценку.

## Часть 1. Перепечатайте фрагмент
В файле \`Sample.txt\` находится фрагмент.

## Часть 2. Короткая задача
Реализуйте метод \`PhoneNumbers.normalize(raw)\`.

## Как будет проверено
Только видимыми тестами.`;

function file(path: string, kind: TaskFileView['kind'], editable: boolean): TaskFileView {
  return { path, kind, editable, content: `// ${path}` };
}

function task(kind: TaskView['kind'], files: TaskFileView[]): TaskView {
  return {
    id: 't', orderNo: 1, kind, title: 'Разминка', status: 'IN_PROGRESS', statementMd: STATEMENT, timeLimitMin: 5,
    files, code: {}, codeSavedAt: null, runsUsed: 0, runsLimit: 60, beaconToken: null, telemetryNextSeq: 0,
    telemetryLastT: 0,
  };
}

const DIR = 'src/main/java/ru/gits/task/telecom/cal/';
const FILES = [
  file(DIR + 'PhoneNumbers.java', 'STARTER', true),
  file(DIR + 'Sample.txt', 'STARTER', false),
  file(DIR + 'Typing.txt', 'STARTER', true),
  file('src/test/java/ru/gits/task/telecom/cal/PhoneNumbersTest.java', 'VISIBLE_TEST', false),
];

describe('warm-up layout', () => {
  it('puts the sample and the typing file in part 1 and the rest in part 2', () => {
    const layout = calibrationLayout(task('CALIBRATION', FILES))!;

    expect(layout.reference.path).toBe(DIR + 'Sample.txt');
    expect(layout.typing.path).toBe(DIR + 'Typing.txt');
    expect(layout.others.map((f) => f.path)).toEqual([
      DIR + 'PhoneNumbers.java',
      'src/test/java/ru/gits/task/telecom/cal/PhoneNumbersTest.java',
    ]);
  });

  it('shows a regular task, or a warm-up without the retyping files, as usual', () => {
    expect(calibrationLayout(task('TASK', FILES))).toBeNull();
    expect(calibrationLayout(task('CALIBRATION', [FILES[0], FILES[3]]))).toBeNull();
    expect(calibrationLayout(null)).toBeNull();
  });

  it('cuts the statement at the part 2 heading', () => {
    const parts = splitStatement(STATEMENT);

    expect(parts[1]).toContain('# Разминка перед оценкой');
    expect(parts[1]).toContain('Этот блок немного влияет на оценку.');
    expect(parts[1]).toContain('## Часть 1. Перепечатайте фрагмент');
    expect(parts[1]).not.toContain('Часть 2');
    expect(parts[1]).not.toContain('Как будет проверено');

    expect(parts[2].startsWith('# Разминка перед оценкой')).toBe(true);
    expect(parts[2]).toContain('## Часть 2. Короткая задача');
    expect(parts[2]).toContain('## Как будет проверено');
    expect(parts[2]).not.toContain('Перепечатайте');
  });

  it('keeps the whole statement in both parts when there is no part 2 heading', () => {
    const parts = splitStatement('# Разминка\n\nПросто текст.');
    expect(parts[1]).toBe(parts[2]);
  });
});
