import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

import { ROOT } from './env';

const BANK = join(ROOT, 'tasks', 'java');

export interface CandidateFile {
  path: string;
  content: string;
  editable: boolean;
}

/**
 * The reference solution of the variant the candidate got, read from tasks/: the variant whose starter files are
 * exactly the candidate's editable files. Returns path → content for every editable file (the starter where the
 * solution does not change a file). The candidate UI never sees these files: only the test reads them.
 */
export function solutionFor(files: CandidateFile[]): Record<string, string> {
  const editable = files.filter((file) => file.editable);
  for (const template of readdirSync(BANK)) {
    const variants = join(BANK, template, 'variants');
    if (!existsSync(variants)) {
      continue;
    }
    for (const variant of readdirSync(variants)) {
      const dir = join(variants, variant);
      const matches = editable.every((file) => {
        const starter = join(dir, 'starter', file.path);
        return existsSync(starter) && normalize(readFileSync(starter, 'utf8')) === normalize(file.content);
      });
      if (matches && editable.length > 0) {
        return Object.fromEntries(
          editable.map((file) => {
            const solution = join(dir, 'solution', file.path);
            return [file.path, existsSync(solution) ? normalize(readFileSync(solution, 'utf8')) : file.content];
          }),
        );
      }
    }
  }
  throw new Error(`No variant in tasks/java has the starter files ${editable.map((f) => f.path).join(', ')}`);
}

function normalize(text: string): string {
  return text.replace(/\r\n/g, '\n');
}
