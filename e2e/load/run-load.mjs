// Load check of P17: N candidates at the same time press «Запустить» again and again; measures how long a run takes
// from the click (POST /run) until its result is there (GET /runs/{id} is DONE, ERROR or TIMEOUT), as the candidate
// waits for it. Runs against the stack the .env describes (scripts/up.*). Usage:
//   node load/run-load.mjs [--candidates 10] [--minutes 5] [--think 5-15] [--code mixed] [--out results.json]
// --code: what is run — starter (the code as given: in concurrency tasks the visible tests may wait for their own
// timeouts), solution (the reference solution from tasks/, as a finished candidate) or mixed (they alternate).
// Creates invites «Нагрузка <time> #i» in the demo employer's company and finishes their sessions at the end.
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

function option(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 ? process.argv[i + 1] : fallback;
}

function env(name) {
  if (process.env[name]) {
    return process.env[name];
  }
  const line = readFileSync(join(ROOT, '.env'), 'utf8')
    .split(/\r?\n/)
    .find((l) => l.startsWith(`${name}=`));
  if (!line) {
    throw new Error(`${name} is not set in .env`);
  }
  return line.slice(name.length + 1).trim();
}

const CANDIDATES = Number(option('candidates', '10'));
const MINUTES = Number(option('minutes', '5'));
const [THINK_MIN, THINK_MAX] = option('think', '5-15').split('-').map(Number);
const OUT = option('out', null);
const CODE = option('code', 'mixed');
const BASE = process.env.GITS_BASE_URL ?? `http://localhost:${env('WEB_PORT')}`;
const RUN_DEADLINE_MS = 120_000;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const normalize = (text) => text.replace(/\r\n/g, '\n');

/** The reference solution of the variant whose starter files are the candidate's editable files (as e2e/support). */
function solutionFor(files) {
  const bank = join(ROOT, 'tasks', 'java');
  for (const template of readdirSync(bank)) {
    const variants = join(bank, template, 'variants');
    if (!existsSync(variants)) {
      continue;
    }
    for (const variant of readdirSync(variants)) {
      const dir = join(variants, variant);
      const matches = Object.entries(files).every(([path, content]) => {
        const starter = join(dir, 'starter', path);
        return existsSync(starter) && normalize(readFileSync(starter, 'utf8')) === normalize(content);
      });
      if (matches) {
        const solution = Object.fromEntries(
          Object.entries(files).map(([path, content]) => {
            const file = join(dir, 'solution', path);
            return [path, existsSync(file) ? normalize(readFileSync(file, 'utf8')) : content];
          }),
        );
        return { code: `${template.slice(0, 3)}-${variant}`, solution };
      }
    }
  }
  throw new Error(`no variant in tasks/java has the starter files ${Object.keys(files).join(', ')}`);
}

/** A browser-like client: keeps cookies and sends the CSRF header on requests that change data. */
class Client {
  cookies = new Map();

  async request(method, path, body) {
    const headers = { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') };
    if (method !== 'GET') {
      if (!this.cookies.has('XSRF-TOKEN')) {
        await this.request('GET', '/api/auth/csrf');
        headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ');
      }
      headers['X-XSRF-TOKEN'] = this.cookies.get('XSRF-TOKEN');
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    const response = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';');
      const eq = pair.indexOf('=');
      this.cookies.set(pair.slice(0, eq), pair.slice(eq + 1));
    }
    if (!response.ok) {
      throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
    }
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
}

function percentile(sorted, p) {
  if (!sorted.length) {
    return null;
  }
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function summary(samples) {
  const ms = samples.map((s) => s.ms).sort((a, b) => a - b);
  return {
    runs: ms.length,
    p50: percentile(ms, 50),
    p95: percentile(ms, 95),
    max: ms.at(-1) ?? null,
    sandboxP50: percentile(samples.map((s) => s.sandboxMs ?? 0).sort((a, b) => a - b), 50),
    waitP95: percentile(samples.map((s) => s.ms - (s.sandboxMs ?? 0)).sort((a, b) => a - b), 95),
    within10s: ms.length ? Number(((ms.filter((m) => m <= 10_000).length / ms.length) * 100).toFixed(1)) : null,
    statuses: samples.reduce((acc, s) => ({ ...acc, [s.status]: (acc[s.status] ?? 0) + 1 }), {}),
  };
}

/** POST /run and wait for the result the way the page does: poll the run until it is finished. */
async function timedRun(client, task, n) {
  const solution = CODE === 'solution' || (CODE === 'mixed' && n % 2 === 1);
  const files = solution ? task.solution : task.starter;
  const started = performance.now();
  const { runId } = await client.request('POST', `/api/candidate/tasks/${task.id}/run`, { files });
  for (;;) {
    const run = await client.request('GET', `/api/candidate/runs/${runId}`);
    if (!['QUEUED', 'RUNNING'].includes(run.status)) {
      return {
        ms: Math.round(performance.now() - started),
        sandboxMs: run.durationMs ?? null,
        status: run.status,
        variant: task.variant,
        code: solution ? 'solution' : 'starter',
      };
    }
    if (performance.now() - started > RUN_DEADLINE_MS) {
      return { ms: Math.round(performance.now() - started), status: 'NO_RESULT' };
    }
    await sleep(250);
  }
}

async function candidate(link, index, until, samples) {
  const client = new Client();
  const token = link.slice(link.lastIndexOf('/') + 1);
  await client.request('POST', '/api/candidate/enter', { token });
  await client.request('POST', '/api/candidate/consent');
  await client.request('POST', '/api/candidate/session/start');
  const session = await client.request('GET', '/api/candidate/session');
  const tasks = [];
  for (const task of session.tasks.filter((t) => t.kind === 'TASK')) {
    const view = await client.request('GET', `/api/candidate/tasks/${task.id}`);
    const files = Object.fromEntries(view.files.filter((f) => f.editable).map((f) => [f.path, f.content]));
    const { code, solution } = solutionFor(files);
    tasks.push({ id: task.id, variant: code, starter: files, solution });
  }
  // the first run of everyone at the same moment: the worst queue the runner sees
  let n = 0;
  const first = await timedRun(client, tasks[0], index);
  samples.push({ ...first, candidate: index, burst: true });
  while (Date.now() < until) {
    await sleep((THINK_MIN + Math.random() * (THINK_MAX - THINK_MIN)) * 1000);
    n++;
    samples.push({ ...(await timedRun(client, tasks[n % tasks.length], index + n)), candidate: index, burst: false });
  }
  return client;
}

async function main() {
  const employer = new Client();
  await employer.request('POST', '/api/auth/login', {
    email: env('DEMO_EMPLOYER_EMAIL'),
    password: env('DEMO_EMPLOYER_PASSWORD'),
  });
  const stamp = new Date().toISOString().slice(0, 19);
  const links = [];
  for (let i = 1; i <= CANDIDATES; i++) {
    const invite = await employer.request('POST', '/api/invites', {
      candidateLabel: `Нагрузка ${stamp} #${i}`,
      targetLevel: ['JUNIOR', 'MIDDLE', 'SENIOR'][i % 3],
    });
    links.push(invite.link);
  }
  console.log(`${CANDIDATES} candidates, ${MINUTES} min, think ${THINK_MIN}-${THINK_MAX} s, ${BASE}`);
  const samples = [];
  const until = Date.now() + MINUTES * 60_000;
  const results = await Promise.allSettled(links.map((link, i) => candidate(link, i + 1, until, samples)));
  const failed = results.filter((r) => r.status === 'rejected');
  failed.forEach((r) => console.error('candidate failed:', r.reason.message));
  // Only after the last measured run: finishing submits every task, and those hidden-test runs would queue up in
  // front of the runs of candidates still measuring
  await Promise.allSettled(
    results.filter((r) => r.status === 'fulfilled').map((r) => r.value.request('POST', '/api/candidate/session/finish')),
  );
  const report = {
    at: new Date().toISOString(),
    candidates: CANDIDATES,
    minutes: MINUTES,
    thinkSeconds: [THINK_MIN, THINK_MAX],
    code: CODE,
    failedCandidates: failed.length,
    all: summary(samples),
    firstRunBurst: summary(samples.filter((s) => s.burst)),
    steady: summary(samples.filter((s) => !s.burst)),
    byVariant: Object.fromEntries(
      [...new Set(samples.map((s) => s.variant))].sort().map((v) => [v, summary(samples.filter((s) => s.variant === v))]),
    ),
  };
  console.log(JSON.stringify(report, null, 2));
  if (OUT) {
    writeFileSync(OUT, JSON.stringify({ ...report, samples }, null, 2));
  }
  process.exitCode = failed.length || report.all.p95 === null || report.all.p95 > 10_000 ? 1 : 0;
}

await main();
