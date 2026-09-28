import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, firstValueFrom, interval, switchMap, takeWhile } from 'rxjs';

import { CandidateApi } from '../../../core/candidate/candidate-api.service';
import {
  FINAL_RUN_STATUSES,
  RunMode,
  RunView,
  SessionView,
  TaskSummary,
  TaskView,
} from '../../../core/candidate/candidate.models';
import { messageOf, statusOf } from '../../../core/http/http-errors';
import { MarkdownPipe } from '../../../core/markdown/markdown.pipe';
import { CodeEditor } from './code-editor';
import { ConfirmDialog } from './confirm-dialog';
import { ResultsPanel } from './results-panel';
import { SessionTimer } from './session-timer';

const AUTOSAVE_MS = 5000;
const SESSION_SYNC_MS = 30000;
const POLL_MS = 1000;

interface Confirmation {
  title: string;
  text: string;
  confirmLabel: string;
  action: () => void;
}

/**
 * /c/session — the workspace: statement and files on the left, the editor in the middle, results below, tasks,
 * timer and actions on top. Code is saved every 5 seconds, on task switch, on Ctrl+S and when the tab is hidden.
 */
@Component({
  selector: 'app-session-page',
  imports: [CodeEditor, ConfirmDialog, MarkdownPipe, ResultsPanel, SessionTimer],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './session-page.html',
})
export class SessionPage implements OnInit {
  private readonly api = inject(CandidateApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly editor = viewChild(CodeEditor);

  protected readonly session = signal<SessionView | null>(null);
  protected readonly syncedAt = signal(Date.now());
  protected readonly currentTaskId = signal<string | null>(null);
  private readonly tasks = signal<Record<string, TaskView>>({});
  protected readonly activePath = signal<string | null>(null);
  private readonly runs = signal<Record<string, RunView | null>>({});
  /** Change counter per task; a save clears the task only if nothing changed while it was in flight. */
  private readonly edits = new Map<string, number>();
  private readonly savedEdits = new Map<string, number>();
  protected readonly dirty = signal<ReadonlySet<string>>(new Set());
  protected readonly saving = signal(false);
  protected readonly running = signal(false);
  protected readonly timeOver = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly confirmation = signal<Confirmation | null>(null);
  private poll?: Subscription;
  private destroyed = false;

  protected readonly task = computed(() => {
    const id = this.currentTaskId();
    return id ? (this.tasks()[id] ?? null) : null;
  });
  protected readonly summary = computed(() =>
    this.session()?.tasks.find((t) => t.id === this.currentTaskId()) ?? null,
  );
  protected readonly run = computed(() => {
    const id = this.currentTaskId();
    return id ? (this.runs()[id] ?? null) : null;
  });
  protected readonly closed = computed(
    () => this.timeOver() || this.summary()?.status === 'SUBMITTED' || this.session()?.status !== 'IN_PROGRESS',
  );
  protected readonly allSubmitted = computed(
    () => !!this.session() && this.session()!.tasks.every((t) => t.status === 'SUBMITTED'),
  );
  protected readonly activeFile = computed(
    () => this.task()?.files.find((f) => f.path === this.activePath()) ?? null,
  );

  async ngOnInit(): Promise<void> {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.poll?.unsubscribe();
    });
    try {
      const session = await firstValueFrom(this.api.session());
      if (session.status !== 'IN_PROGRESS') {
        await this.router.navigateByUrl('/c/done');
        return;
      }
      this.applySession(session);
      const first = session.tasks.find((t) => t.status !== 'SUBMITTED') ?? session.tasks[0];
      await this.openTask(first.id);
    } catch (error) {
      await this.handleFatal(error);
      return;
    }
    if (this.destroyed) {
      return;
    }
    const autosave = setInterval(() => void this.saveCurrent(), AUTOSAVE_MS);
    const sync = setInterval(() => void this.syncSession(), SESSION_SYNC_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(autosave);
      clearInterval(sync);
    });
  }

  // --- tasks and files ---------------------------------------------------------------------------------------------

  protected async selectTask(summary: TaskSummary): Promise<void> {
    if (summary.id === this.currentTaskId()) {
      return;
    }
    await this.saveCurrent();
    await this.openTask(summary.id);
  }

  protected selectFile(path: string): void {
    this.activePath.set(path);
    this.editor()?.focus();
  }

  private async openTask(id: string): Promise<void> {
    let task = this.tasks()[id];
    if (!task) {
      task = await firstValueFrom(this.api.task(id));
      this.tasks.update((all) => ({ ...all, [id]: task! }));
      // opening starts the task on the server
      this.session.update((s) =>
        s && { ...s, tasks: s.tasks.map((t) => (t.id === id && t.status === 'NOT_STARTED' ? { ...t, status: 'IN_PROGRESS' } : t)) },
      );
    }
    this.currentTaskId.set(id);
    const editable = task.files.find((f) => f.kind === 'STARTER' && f.editable);
    this.activePath.set((editable ?? task.files[0])?.path ?? null);
  }

  protected onChanged(change: { taskId: string }): void {
    this.edits.set(change.taskId, (this.edits.get(change.taskId) ?? 0) + 1);
    if (!this.dirty().has(change.taskId)) {
      this.dirty.update((set) => new Set(set).add(change.taskId));
    }
  }

  // --- saving ------------------------------------------------------------------------------------------------------

  protected async saveCurrent(): Promise<void> {
    const task = this.task();
    if (!task || !this.dirty().has(task.id) || this.closed() || this.saving()) {
      return;
    }
    const version = this.edits.get(task.id) ?? 0;
    this.saving.set(true);
    try {
      await firstValueFrom(this.api.saveCode(task.id, this.editor()!.contents(task)));
      this.markSaved(task.id, version);
    } catch (error) {
      // 429: saved a moment ago, the next autosave will send it; the rest is shown
      if (statusOf(error) === 409) {
        await this.syncSession();
      } else if (statusOf(error) === 401) {
        await this.handleFatal(error);
      } else if (statusOf(error) !== 429) {
        this.notice.set(messageOf(error, 'Не удалось сохранить код — повторим через несколько секунд.'));
      }
    } finally {
      this.saving.set(false);
    }
  }

  private markSaved(taskId: string, version: number): void {
    this.savedEdits.set(taskId, version);
    if ((this.edits.get(taskId) ?? 0) === version) {
      this.dirty.update((set) => {
        const next = new Set(set);
        next.delete(taskId);
        return next;
      });
    }
  }

  // --- run and submit ----------------------------------------------------------------------------------------------

  protected async runTests(): Promise<void> {
    await this.start('RUN');
  }

  protected askSubmit(): void {
    this.confirmation.set({
      title: 'Отправить решение?',
      text: 'Решение будет проверено скрытыми тестами. После отправки задачу нельзя будет изменить.',
      confirmLabel: 'Отправить',
      action: () => void this.start('SUBMIT'),
    });
  }

  protected askFinish(): void {
    const unsent = this.session()?.tasks.filter((t) => t.status !== 'SUBMITTED').length ?? 0;
    this.confirmation.set({
      title: 'Завершить оценку?',
      text:
        unsent > 0
          ? `Неотправленные задачи (${unsent}) будут отправлены на проверку в текущем виде. Вернуться к ним будет нельзя.`
          : 'Все задачи отправлены. После завершения вернуться к ним будет нельзя.',
      confirmLabel: 'Завершить',
      action: () => void this.finish(),
    });
  }

  protected confirm(): void {
    const action = this.confirmation()?.action;
    this.confirmation.set(null);
    action?.();
  }

  private async start(mode: RunMode): Promise<void> {
    const task = this.task();
    if (!task || this.closed() || this.running()) {
      return;
    }
    const version = this.edits.get(task.id) ?? 0;
    this.running.set(true);
    this.notice.set(null);
    try {
      const files = this.editor()!.contents(task);
      const accepted = await firstValueFrom(
        mode === 'RUN' ? this.api.run(task.id, files) : this.api.submit(task.id, files),
      );
      // the files of a run are saved as the task's snapshot
      this.markSaved(task.id, version);
      this.setRun(task.id, {
        id: accepted.runId, mode, status: accepted.status, createdAt: new Date().toISOString(), finishedAt: null,
        compiled: null, testsTotal: null, testsPassed: null, compileOutput: null, tests: [], output: null,
        durationMs: null,
      });
      this.updateSummary(task.id, (t) =>
        mode === 'SUBMIT' ? { ...t, status: 'SUBMITTED' } : { ...t, runsUsed: t.runsUsed + 1 },
      );
      this.follow(task.id, accepted.runId);
    } catch (error) {
      this.running.set(false);
      if (statusOf(error) === 401) {
        await this.handleFatal(error);
        return;
      }
      this.notice.set(messageOf(error));
      if (statusOf(error) === 409) {
        await this.syncSession();
      }
    }
  }

  /** Polls the run once a second until it is finished. */
  private follow(taskId: string, runId: string): void {
    this.poll?.unsubscribe();
    this.poll = interval(POLL_MS)
      .pipe(
        switchMap(() => this.api.runResult(runId)),
        takeWhile((run) => !FINAL_RUN_STATUSES.includes(run.status), true),
      )
      .subscribe({
        next: (run) => {
          this.setRun(taskId, run);
          if (FINAL_RUN_STATUSES.includes(run.status)) {
            this.running.set(false);
            if (run.mode === 'SUBMIT' && this.allSubmitted()) {
              this.askFinish();
            }
          }
        },
        error: (error: unknown) => {
          this.running.set(false);
          this.notice.set(messageOf(error, 'Не удалось получить результат запуска.'));
        },
      });
  }

  private setRun(taskId: string, run: RunView): void {
    this.runs.update((all) => ({ ...all, [taskId]: run }));
  }

  // --- session -----------------------------------------------------------------------------------------------------

  private async finish(): Promise<void> {
    await this.saveCurrent();
    try {
      await firstValueFrom(this.api.finishSession());
      await this.router.navigateByUrl('/c/done');
    } catch (error) {
      if (statusOf(error) === 401) {
        await this.router.navigateByUrl('/c/done');
        return;
      }
      this.notice.set(messageOf(error));
    }
  }

  protected onTimeOver(): void {
    this.timeOver.set(true);
    this.confirmation.set(null);
  }

  protected async leave(): Promise<void> {
    await this.router.navigateByUrl('/c/done');
  }

  private async syncSession(): Promise<void> {
    try {
      const session = await firstValueFrom(this.api.session());
      this.applySession(session);
      if (session.status !== 'IN_PROGRESS') {
        this.timeOver.set(true);
      }
    } catch (error) {
      if (statusOf(error) === 401) {
        // the session was closed and the invite completed: the assessment is over
        this.timeOver.set(true);
      }
    }
  }

  private applySession(session: SessionView): void {
    this.session.set(session);
    this.syncedAt.set(Date.now());
  }

  private updateSummary(taskId: string, change: (task: TaskSummary) => TaskSummary): void {
    this.session.update((s) => s && { ...s, tasks: s.tasks.map((t) => (t.id === taskId ? change(t) : t)) });
  }

  private async handleFatal(error: unknown): Promise<void> {
    if (statusOf(error) === 404) {
      await this.router.navigateByUrl('/c/intro');
    } else if (statusOf(error) === 401 || statusOf(error) === 403) {
      await this.router.navigateByUrl('/c/closed');
    } else {
      this.notice.set(messageOf(error));
    }
  }

  protected taskLabel(task: TaskSummary): string {
    return task.kind === 'CALIBRATION' ? 'Разминка' : `Задача ${task.orderNo - 1}`;
  }

  protected fileName(path: string): string {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  // --- keyboard and page visibility ---------------------------------------------------------------------------------

  @HostListener('document:keydown', ['$event'])
  protected onKeydown(event: KeyboardEvent): void {
    if (!(event.ctrlKey || event.metaKey)) {
      return;
    }
    if (event.key === 's' || event.key === 'S' || event.code === 'KeyS') {
      event.preventDefault();
      void this.saveCurrent();
    } else if (event.key === 'Enter') {
      event.preventDefault();
      void this.runTests();
    }
  }

  @HostListener('document:visibilitychange')
  protected onVisibilityChange(): void {
    if (document.visibilityState === 'hidden') {
      void this.saveCurrent();
    }
  }
}
