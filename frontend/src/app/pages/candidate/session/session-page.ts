import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnInit,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, concatMap, firstValueFrom, takeWhile, timer } from 'rxjs';

import { CandidateApi } from '../../../core/candidate/candidate-api.service';
import {
  FINAL_RUN_STATUSES,
  RetypingResult,
  RunMode,
  RunView,
  SessionView,
  TaskSummary,
  TaskView,
} from '../../../core/candidate/candidate.models';
import { messageOf, statusOf } from '../../../core/http/http-errors';
import { MarkdownPipe } from '../../../core/markdown/markdown.pipe';
import { TelemetryCollector } from '../../../core/telemetry/telemetry-collector.service';
import { CalibrationPart, calibrationLayout } from './calibration';
import { CodeEditor } from './code-editor';
import { CodeSaver } from './code-saver';
import { ConfirmDialog } from './confirm-dialog';
import { ReferenceViewer } from './reference-viewer';
import { ResultsPanel } from './results-panel';
import { SessionTimer } from './session-timer';
import { TelemetryDebugPanel } from './telemetry-debug';

/** Autosave checks every second; each task is sent at most once per server interval (5 s). */
const AUTOSAVE_TICK_MS = 1000;
const SESSION_SYNC_MS = 30000;
const POLL_MS = 1000;
/** Before the deadline all dirty tasks are flushed, so the automatic submit gets the latest code. */
const FINAL_FLUSH_SECONDS = 12;
/** Telemetry is uploaded before a submit and the end of the session, but never holds them up for longer. */
const TELEMETRY_FLUSH_TIMEOUT_MS = 3000;

interface Confirmation {
  title: string;
  text: string;
  confirmLabel: string;
  action: () => void;
}

/**
 * /c/session — the workspace: statement and files on the left, the editor in the middle, results below, tasks,
 * timer and actions on top. Code of every task is saved within about 5 seconds of an edit (see CodeSaver), on task
 * switch, on Ctrl+S, when the tab is hidden and before the session ends.
 */
@Component({
  selector: 'app-session-page',
  imports: [CodeEditor, ConfirmDialog, MarkdownPipe, ReferenceViewer, ResultsPanel, SessionTimer, TelemetryDebugPanel],
  // telemetry exists only on this page, which opens after the consent (see consentGuard)
  providers: [TelemetryCollector],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './session-page.html',
})
export class SessionPage implements OnInit {
  private readonly api = inject(CandidateApi);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly telemetry = inject(TelemetryCollector);
  protected readonly debug = new URLSearchParams(location.search).get('debug') === '1';
  private readonly editor = viewChild(CodeEditor);
  private readonly statementElement = viewChild<ElementRef<HTMLElement>>('statementElement');
  private readonly resultsElement = viewChild<ElementRef<HTMLElement>>('resultsElement');
  private readonly overlayButton = viewChild<ElementRef<HTMLButtonElement>>('overlayButton');

  protected readonly session = signal<SessionView | null>(null);
  protected readonly syncedAt = signal(Date.now());
  protected readonly currentTaskId = signal<string | null>(null);
  private readonly tasks = signal<Record<string, TaskView>>({});
  protected readonly activePath = signal<string | null>(null);
  private readonly runs = signal<Record<string, RunView | null>>({});
  private readonly retypings = signal<Record<string, RetypingResult>>({});
  protected readonly running = signal(false);
  protected readonly timeOver = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly confirmation = signal<Confirmation | null>(null);
  protected readonly saver = new CodeSaver({
    send: (taskId) => firstValueFrom(this.api.saveCode(taskId, this.editor()!.contents(this.tasks()[taskId]))),
  });
  private poll?: Subscription;
  private destroyed = false;
  private finalFlushDone = false;
  /** The latest task the candidate asked for; answers for earlier clicks do not switch the view. */
  private opening = 0;

  protected readonly task = computed(() => {
    const id = this.currentTaskId();
    return id ? (this.tasks()[id] ?? null) : null;
  });
  protected readonly summary = computed(
    () => this.session()?.tasks.find((t) => t.id === this.currentTaskId()) ?? null,
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
  protected readonly hasUnsaved = computed(() => this.saver.dirty().size > 0);

  /** The warm-up is shown in two parts: retyping on a split screen, then a short task. */
  protected readonly calibration = computed(() => calibrationLayout(this.task()));
  /** The part of each warm-up task the candidate is on; kept while the page is open. */
  private readonly calibrationParts = signal<Record<string, CalibrationPart>>({});
  protected readonly calibrationPart = computed<CalibrationPart>(() => {
    const id = this.currentTaskId();
    return (id && this.calibrationParts()[id]) || 1;
  });
  protected readonly split = computed(() => !!this.calibration() && this.calibrationPart() === 1);
  protected readonly retyping = computed(() => {
    const id = this.currentTaskId();
    return id ? (this.retypings()[id] ?? null) : null;
  });
  protected readonly statement = computed(() => {
    const layout = this.calibration();
    return layout ? layout.statement[this.calibrationPart()] : (this.task()?.statementMd ?? '');
  });
  protected readonly visibleFiles = computed(() => {
    const layout = this.calibration();
    if (!layout) {
      return this.task()?.files ?? [];
    }
    // part 1: the sample is always shown on top, the list holds only the file typed into
    return this.calibrationPart() === 1 ? [layout.typing] : layout.others;
  });

  constructor() {
    effect(() => {
      if (this.timeOver()) {
        queueMicrotask(() => this.overlayButton()?.nativeElement.focus());
      }
    });
    // a paste is checked against the task's files and its statement as the candidate sees it
    this.telemetry.setSourceLookup((exclude) => {
      const task = this.task();
      const editor = this.editor();
      if (!task || !editor) {
        return [];
      }
      // the statement and the run results (expected values, compiler messages) are the task's text too
      return [
        ...editor.texts(task, exclude),
        this.statementElement()?.nativeElement.textContent ?? '',
        this.resultsElement()?.nativeElement.textContent ?? '',
      ];
    });
    effect(() => {
      const suspicion = this.telemetry.copySuspicion();
      if (suspicion) {
        this.notice.set(
          `Подозрение на копирование: вставлен текст (${suspicion.length} симв.), которого нет ни в коде, ни в ` +
            'условии задачи. Решение должно быть вашим — такие вставки видит работодатель.',
        );
      }
    });
  }

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
    const autosave = setInterval(() => this.autosaveTick(), AUTOSAVE_TICK_MS);
    const sync = setInterval(() => void this.syncSession(), SESSION_SYNC_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(autosave);
      clearInterval(sync);
    });
  }

  // --- tasks and files ---------------------------------------------------------------------------------------------

  protected async selectTask(summary: TaskSummary): Promise<void> {
    const previous = this.currentTaskId();
    if (summary.id === previous) {
      return;
    }
    if (previous) {
      // sent now if the server allows it, otherwise by the next autosave tick: the task's model keeps the code
      this.saver.save(previous, false).catch((error: unknown) => void this.onSaveError(error));
    }
    try {
      await this.openTask(summary.id);
    } catch (error) {
      if (statusOf(error) === 401) {
        await this.handleFatal(error);
      } else {
        this.notice.set(messageOf(error, 'Не удалось открыть задачу. Попробуйте ещё раз.'));
      }
    }
  }

  /** Switches the warm-up between part 1 (retyping) and part 2 (the short task). */
  protected selectPart(part: CalibrationPart): void {
    const layout = this.calibration();
    if (!layout) {
      return;
    }
    const id = this.currentTaskId()!;
    this.calibrationParts.update((parts) => ({ ...parts, [id]: part }));
    this.showCalibrationPart(layout, part);
    // straight to typing: the part switch is a step of the warm-up, not a setting
    this.editor()?.focus();
  }

  private showCalibrationPart(layout: NonNullable<ReturnType<typeof calibrationLayout>>, part: CalibrationPart): void {
    const target = part === 1 ? layout.typing
        : (layout.others.find((f) => f.kind === 'STARTER' && f.editable) ?? layout.others[0]);
    if (target) {
      this.activePath.set(target.path);
    }
  }

  protected selectFile(path: string): void {
    this.activePath.set(path);
    this.editor()?.focus();
  }

  private async openTask(id: string): Promise<void> {
    const ticket = ++this.opening;
    let task = this.tasks()[id];
    if (!task) {
      const loaded = await firstValueFrom(this.api.task(id));
      this.tasks.update((all) => ({ ...all, [id]: loaded }));
      task = loaded;
      // opening starts the task on the server
      this.updateSummary(id, (t) => (t.status === 'NOT_STARTED' ? { ...t, status: 'IN_PROGRESS' } : t));
    }
    if (ticket !== this.opening) {
      return;
    }
    this.telemetry.startTask(task);
    this.currentTaskId.set(id);
    const layout = calibrationLayout(task);
    if (layout) {
      // the warm-up opens on the part the candidate left it on, part 1 the first time
      this.showCalibrationPart(layout, this.calibrationParts()[id] ?? 1);
      return;
    }
    const editable = task.files.find((f) => f.kind === 'STARTER' && f.editable);
    this.activePath.set((editable ?? task.files[0])?.path ?? null);
  }

  protected onChanged(change: { taskId: string }): void {
    this.saver.changed(change.taskId);
  }

  // --- saving ------------------------------------------------------------------------------------------------------

  /** Ctrl+S: the current task now, or as soon as the server allows. */
  protected saveNow(): void {
    const id = this.currentTaskId();
    if (id && !this.isClosed(id)) {
      this.saver.save(id, true).catch((error: unknown) => void this.onSaveError(error));
    }
  }

  private autosaveTick(): void {
    this.saver.tick(
      (_taskId, error) => void this.onSaveError(error),
      (taskId) => this.isClosed(taskId),
    );
    if (!this.finalFlushDone && this.remainingSeconds() <= FINAL_FLUSH_SECONDS && !this.timeOver()) {
      this.finalFlushDone = true;
      this.saver.flushAll().catch((error: unknown) => void this.onSaveError(error));
      // events after the deadline are not accepted: send what is buffered while the tasks are still open
      void this.telemetry.flushAll(true);
    }
  }

  private async onSaveError(error: unknown): Promise<void> {
    if (statusOf(error) === 409) {
      await this.syncSession();
    } else if (statusOf(error) === 401) {
      this.timeOver.set(true);
    } else {
      this.notice.set(messageOf(error, 'Не удалось сохранить код — повторим через несколько секунд.'));
    }
  }

  private isClosed(taskId: string): boolean {
    const summary = this.session()?.tasks.find((t) => t.id === taskId);
    return this.timeOver() || this.session()?.status !== 'IN_PROGRESS' || summary?.status === 'SUBMITTED';
  }

  // --- run and submit ----------------------------------------------------------------------------------------------

  /** "Run tests" (Ctrl+Enter); in warm-up part 1 it checks the retyping instead, nothing is run. */
  protected async runTests(): Promise<void> {
    if (this.split()) {
      await this.checkRetyping();
      return;
    }
    await this.start('RUN');
  }

  /** Warm-up part 1: the platform compares the retyping with the sample and looks for pastes in the telemetry. */
  private async checkRetyping(): Promise<void> {
    const task = this.task();
    if (!task || this.closed() || this.running()) {
      return;
    }
    const version = this.saver.version(task.id);
    this.running.set(true);
    this.notice.set(null);
    try {
      // pastes are found in the stored telemetry: send what is buffered first
      await this.flushTelemetry();
      const result = await firstValueFrom(this.api.retyping(task.id, this.editor()!.contents(task)));
      this.saver.markSaved(task.id, version);
      this.retypings.update((all) => ({ ...all, [task.id]: result }));
    } catch (error) {
      if (statusOf(error) === 401) {
        await this.handleFatal(error);
        return;
      }
      this.notice.set(messageOf(error));
      if (statusOf(error) === 409) {
        await this.syncSession();
      }
    } finally {
      this.running.set(false);
    }
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
    const version = this.saver.version(task.id);
    this.running.set(true);
    this.notice.set(null);
    this.telemetry.action(mode === 'RUN' ? 'run' : 'submit');
    if (mode === 'SUBMIT') {
      // the task's events must reach the server before it closes the task
      await this.flushTelemetry();
    }
    try {
      const files = this.editor()!.contents(task);
      const accepted = await firstValueFrom(
        mode === 'RUN' ? this.api.run(task.id, files) : this.api.submit(task.id, files),
      );
      // the files of a run are saved as the task's snapshot
      this.saver.markSaved(task.id, version);
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

  /** Polls the run once a second until it is finished; a slow answer is awaited, never cancelled. */
  private follow(taskId: string, runId: string): void {
    this.poll?.unsubscribe();
    this.poll = timer(POLL_MS, POLL_MS)
      .pipe(
        concatMap(() => this.api.runResult(runId)),
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
    await this.flushTelemetry();
    try {
      // every task's latest code must reach the server before it submits the open ones
      await this.saver.flushAll();
    } catch (error) {
      if (statusOf(error) !== 409 && statusOf(error) !== 401) {
        this.notice.set(messageOf(error, 'Не удалось сохранить код. Проверьте подключение и попробуйте ещё раз.'));
        return;
      }
    }
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

  private async flushTelemetry(): Promise<void> {
    await Promise.race([
      this.telemetry.flushAll(true),
      new Promise<void>((resolve) => setTimeout(resolve, TELEMETRY_FLUSH_TIMEOUT_MS)),
    ]);
  }

  protected onTimeOver(): void {
    this.timeOver.set(true);
    this.confirmation.set(null);
  }

  protected async leave(): Promise<void> {
    await this.router.navigateByUrl('/c/done');
  }

  private remainingSeconds(): number {
    const session = this.session();
    return session ? session.remainingSeconds - (Date.now() - this.syncedAt()) / 1000 : Infinity;
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
    if (!(event.ctrlKey || event.metaKey) || this.confirmation() || this.timeOver()) {
      return;
    }
    if (event.code === 'KeyS' || event.key === 's' || event.key === 'S') {
      event.preventDefault();
      this.saveNow();
    } else if (event.key === 'Enter') {
      event.preventDefault();
      void this.runTests();
    }
  }

  @HostListener('document:visibilitychange')
  protected onVisibilityChange(): void {
    if (document.visibilityState === 'hidden') {
      this.saver.tick((_taskId, error) => void this.onSaveError(error), (taskId) => this.isClosed(taskId));
    }
  }
}
