import { signal } from '@angular/core';

import { statusOf } from '../../../core/http/http-errors';

export interface CodeSaverOptions {
  /** Sends the current code of the task (PUT /tasks/{id}/code); rejects with the HTTP error. */
  send: (taskId: string) => Promise<void>;
  /** The server accepts one save per task in this interval (gits.session.autosave-interval). */
  minIntervalMs?: number;
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

/** Margin over the server's interval, so that clock jitter does not produce a 429. */
const MARGIN_MS = 300;
const MAX_ATTEMPTS = 4;

/**
 * Autosave bookkeeping of all tasks of a session. Every edit bumps the task's version; a save clears the task only
 * when no edit arrived while it was in flight. Saves of one task never overlap and respect the server's interval:
 * a save that comes too early waits (or is deferred to the next autosave tick), a 429 is retried after the
 * interval. Nothing is dropped silently: a task stays dirty until the server has its latest version.
 */
export class CodeSaver {
  readonly dirty = signal<ReadonlySet<string>>(new Set());

  private readonly send: (taskId: string) => Promise<void>;
  private readonly minIntervalMs: number;
  private readonly now: () => number;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly versions = new Map<string, number>();
  private readonly lastSave = new Map<string, number>();
  private readonly inFlight = new Map<string, Promise<void>>();

  constructor(options: CodeSaverOptions) {
    this.send = options.send;
    this.minIntervalMs = options.minIntervalMs ?? 5000;
    this.now = options.now ?? Date.now;
    this.sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));
  }

  /** The candidate changed the code of the task. */
  changed(taskId: string): void {
    this.versions.set(taskId, this.version(taskId) + 1);
    if (!this.dirty().has(taskId)) {
      this.dirty.update((set) => new Set(set).add(taskId));
    }
  }

  isDirty(taskId: string): boolean {
    return this.dirty().has(taskId);
  }

  version(taskId: string): number {
    return this.versions.get(taskId) ?? 0;
  }

  /** The server stored {@code version} of the task by other means (RUN and SUBMIT save the files they get). */
  markSaved(taskId: string, version: number): void {
    this.lastSave.set(taskId, this.now());
    if (this.version(taskId) === version) {
      this.dirty.update((set) => {
        const next = new Set(set);
        next.delete(taskId);
        return next;
      });
    }
  }

  /**
   * Autosave tick: sends every dirty task whose interval has passed and which is not being saved already.
   * Errors other than 429 are reported to {@code onError}; the task stays dirty.
   */
  tick(onError: (taskId: string, error: unknown) => void, skip: (taskId: string) => boolean = () => false): void {
    for (const taskId of this.dirty()) {
      if (!skip(taskId) && !this.inFlight.has(taskId) && this.waitFor(taskId) === 0) {
        this.save(taskId, false).catch((error: unknown) => onError(taskId, error));
      }
    }
  }

  /**
   * Saves the task's latest version. With {@code wait} the call waits for the server's interval and for a save
   * already in flight, retries after a 429 and repeats while edits keep arriving; it resolves once the task is
   * clean. Without it, a save that would come too early is skipped (the next tick sends it).
   */
  async save(taskId: string, wait = true): Promise<void> {
    for (let attempt = 0; attempt < MAX_ATTEMPTS && this.isDirty(taskId); attempt++) {
      const running = this.inFlight.get(taskId);
      if (running) {
        if (!wait) {
          return;
        }
        await running.catch(() => undefined);
        continue;
      }
      const delay = this.waitFor(taskId);
      if (delay > 0) {
        if (!wait) {
          return;
        }
        await this.sleep(delay);
      }
      const version = this.version(taskId);
      const request = this.send(taskId);
      this.inFlight.set(taskId, request);
      try {
        await request;
        this.markSaved(taskId, version);
      } catch (error) {
        if (statusOf(error) !== 429) {
          throw error;
        }
        // saved a moment ago by another path: the server's interval starts now
        this.lastSave.set(taskId, this.now());
        if (!wait) {
          return;
        }
      } finally {
        this.inFlight.delete(taskId);
      }
    }
  }

  /** Saves all dirty tasks, waiting as needed; used before finishing and before the time runs out. */
  async flushAll(): Promise<void> {
    await Promise.all([...this.dirty()].map((taskId) => this.save(taskId, true)));
  }

  private waitFor(taskId: string): number {
    const last = this.lastSave.get(taskId);
    return last === undefined ? 0 : Math.max(0, last + this.minIntervalMs + MARGIN_MS - this.now());
  }
}
