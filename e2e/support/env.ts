import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

/** The repository root: e2e/ lives in it. */
export const ROOT = resolve(__dirname, '..', '..');

/** Values of the stack's .env (demo accounts, database): the same file docker compose reads. */
export function env(name: string): string {
  const fromProcess = process.env[name];
  if (fromProcess) {
    return fromProcess;
  }
  const file = join(ROOT, '.env');
  if (!existsSync(file)) {
    throw new Error(`${file} not found: start the stack with scripts/up.* first`);
  }
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith(`${name}=`));
  if (!line) {
    throw new Error(`${name} is not set in .env`);
  }
  return line.slice(name.length + 1).trim();
}

export const employer = () => ({ email: env('DEMO_EMPLOYER_EMAIL'), password: env('DEMO_EMPLOYER_PASSWORD') });
export const admin = () => ({ email: env('DEMO_ADMIN_EMAIL'), password: env('DEMO_ADMIN_PASSWORD') });

/**
 * SQL in the stack's database through docker compose (the tests' only back door: moving a session back in time
 * to test its expiry without waiting 90 minutes).
 */
export function sql(statement: string): string {
  return execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'postgres', 'psql', '-U', env('POSTGRES_USER'), '-d', env('POSTGRES_DB'), '-Atc', statement],
    { cwd: ROOT, encoding: 'utf8' },
  ).trim();
}
