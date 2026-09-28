import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { CodeSaver } from './code-saver';

/** A server with the real rule: one save per task every 5 seconds, 429 otherwise. */
class FakeServer {
  now = 0;
  readonly saved: { taskId: string; at: number }[] = [];
  private readonly last = new Map<string, number>();
  /** Holds the next answers until released, to model a slow request. */
  hold = false;
  private readonly held: (() => void)[] = [];

  send = (taskId: string): Promise<void> => {
    const at = this.now;
    const answer = (): void => undefined;
    const last = this.last.get(taskId);
    if (last !== undefined && at - last < 5000) {
      return Promise.reject(new HttpErrorResponse({ status: 429 }));
    }
    this.last.set(taskId, at);
    this.saved.push({ taskId, at });
    if (!this.hold) {
      return Promise.resolve(answer());
    }
    return new Promise((resolve) => this.held.push(() => resolve()));
  };

  release(): void {
    this.held.splice(0).forEach((resolve) => resolve());
  }

  /** Time passes instantly in tests: sleeping moves the clock. */
  sleep = async (ms: number): Promise<void> => {
    this.now += ms;
  };
}

function saver(server: FakeServer): CodeSaver {
  return new CodeSaver({ send: server.send, now: () => server.now, sleep: server.sleep });
}

describe('CodeSaver', () => {
  it('saves a changed task and marks it clean', async () => {
    const server = new FakeServer();
    const code = saver(server);
    code.changed('a');
    expect(code.isDirty('a')).toBe(true);

    await code.save('a');

    expect(server.saved).toEqual([{ taskId: 'a', at: 0 }]);
    expect(code.isDirty('a')).toBe(false);
  });

  it('waits for the server interval instead of losing the edit to a 429', async () => {
    const server = new FakeServer();
    const code = saver(server);
    code.changed('a');
    await code.save('a');
    server.now = 1000;
    code.changed('a');

    // e.g. switching to another task one second after the last autosave
    await code.save('a', true);

    expect(server.saved.map((s) => s.at)).toEqual([0, 5300]);
    expect(code.isDirty('a')).toBe(false);
  });

  it('defers an early save of the autosave tick to a later tick', async () => {
    const server = new FakeServer();
    const code = saver(server);
    code.changed('a');
    await code.save('a');
    server.now = 1000;
    code.changed('a');

    code.tick(() => undefined);
    expect(server.saved).toHaveLength(1);
    expect(code.isDirty('a')).toBe(true);

    server.now = 5400;
    code.tick(() => undefined);
    await Promise.resolve();
    expect(server.saved).toHaveLength(2);
  });

  it('keeps a task dirty when it changed while its save was in flight, and sends the rest later', async () => {
    const server = new FakeServer();
    const code = saver(server);
    server.hold = true;
    code.changed('a');
    const first = code.save('a', false);
    code.changed('a'); // typed while the request is on its way
    server.release();
    await first;

    expect(code.isDirty('a')).toBe(true);
    server.hold = false;
    await code.save('a', true);
    expect(server.saved).toHaveLength(2);
    expect(code.isDirty('a')).toBe(false);
  });

  it('retries after a 429 caused by a save through another path', async () => {
    const server = new FakeServer();
    const code = saver(server);
    // a RUN stored the files on the server a moment ago, unknown to the saver
    await server.send('a');
    code.changed('a');

    await code.save('a', true);

    expect(server.saved.map((s) => s.at)).toEqual([0, 5300]);
    expect(code.isDirty('a')).toBe(false);
  });

  it('flushes every dirty task, not only the current one', async () => {
    const server = new FakeServer();
    const code = saver(server);
    code.changed('a');
    code.changed('b');

    await code.flushAll();

    expect(server.saved.map((s) => s.taskId).sort()).toEqual(['a', 'b']);
    expect(code.dirty().size).toBe(0);
  });

  it('treats a run as a save of the version it sent', () => {
    const server = new FakeServer();
    const code = saver(server);
    code.changed('a');
    const version = code.version('a');
    code.markSaved('a', version);
    expect(code.isDirty('a')).toBe(false);

    code.changed('a');
    code.markSaved('a', version);
    expect(code.isDirty('a')).toBe(true);
  });

  it('reports errors other than 429 and keeps the task dirty', async () => {
    const code = new CodeSaver({
      send: () => Promise.reject(new HttpErrorResponse({ status: 500 })),
      now: () => 0,
      sleep: async () => undefined,
    });
    code.changed('a');

    await expect(code.save('a')).rejects.toBeInstanceOf(HttpErrorResponse);
    expect(code.isDirty('a')).toBe(true);
  });
});
