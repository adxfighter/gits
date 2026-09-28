import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type * as Monaco from 'monaco-editor';

import { TaskView } from '../../../core/candidate/candidate.models';
import { MonacoApi, MonacoLoader } from '../../../core/editor/monaco-loader.service';

/** The editor instance, for modules that attach to it (telemetry, P11). */
export interface EditorReady {
  monaco: MonacoApi;
  editor: Monaco.editor.IStandaloneCodeEditor;
}

/**
 * Monaco with the files of the current task. One model per task file is kept for the whole session, so switching
 * tasks keeps each file's undo history. Readonly files and closed tasks cannot be edited.
 */
@Component({
  selector: 'app-code-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loadError(); as message) {
      <p class="error editor-error">{{ message }}</p>
    }
    <div #host class="editor-host" data-testid="editor"></div>
  `,
  styles: `
    :host { display: block; position: relative; min-height: 0; }
    .editor-host { position: absolute; inset: 0; }
    .editor-error { position: absolute; top: 8px; left: 8px; z-index: 1; }
  `,
})
export class CodeEditor {
  readonly task = input<TaskView | null>(null);
  readonly activePath = input<string | null>(null);
  /** The task is submitted or the time is over. */
  readonly closed = input(false);

  readonly changed = output<{ taskId: string; path: string }>();
  readonly saveRequested = output<void>();
  readonly runRequested = output<void>();
  readonly ready = output<EditorReady>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly loader = inject(MonacoLoader);
  private readonly monaco = signal<MonacoApi | null>(null);
  private editor?: Monaco.editor.IStandaloneCodeEditor;
  private destroyed = false;
  private readonly models = new Map<string, Monaco.editor.ITextModel>();
  protected readonly loadError = signal<string | null>(null);

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.loader
      .load()
      .then((monaco) => {
        if (this.destroyed) {
          return;
        }
        this.editor = monaco.editor.create(this.host().nativeElement, {
          theme: 'vs-dark',
          automaticLayout: true,
          fontSize: 14,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          tabSize: 4,
          insertSpaces: true,
          model: null,
        });
        this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => this.saveRequested.emit());
        this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => this.runRequested.emit());
        this.monaco.set(monaco);
        this.ready.emit({ monaco, editor: this.editor });
      })
      .catch((error: unknown) => {
        this.loadError.set(error instanceof Error ? error.message : 'Не удалось загрузить редактор кода');
      });
    destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.editor?.dispose();
      this.models.forEach((model) => model.dispose());
    });

    effect(() => {
      const monaco = this.monaco();
      const task = this.task();
      const path = this.activePath();
      const closed = this.closed();
      if (!monaco || !this.editor || !task || !path) {
        return;
      }
      const file = task.files.find((f) => f.path === path);
      if (!file) {
        return;
      }
      const model = this.model(monaco, task, file.path);
      if (this.editor.getModel() !== model) {
        this.editor.setModel(model);
      }
      const calibration = task.kind === 'CALIBRATION';
      this.editor.updateOptions({
        readOnly: closed || !file.editable,
        readOnlyMessage: { value: closed ? 'Задание закрыто для правок' : 'Этот файл нельзя изменять' },
        // the calibration block measures plain typing: no suggestions and no automatic closing pairs
        quickSuggestions: !calibration,
        suggestOnTriggerCharacters: !calibration,
        wordBasedSuggestions: calibration ? 'off' : 'currentDocument',
        snippetSuggestions: calibration ? 'none' : 'inline',
        acceptSuggestionOnEnter: calibration ? 'off' : 'on',
        autoClosingBrackets: calibration ? 'never' : 'languageDefined',
        autoClosingQuotes: calibration ? 'never' : 'languageDefined',
        autoIndent: calibration ? 'none' : 'full',
        formatOnType: false,
      });
    });
  }

  /** Current contents of the editable files of a task, from the editor models. */
  contents(task: TaskView): Record<string, string> {
    const files: Record<string, string> = {};
    for (const file of task.files) {
      if (file.kind === 'STARTER' && file.editable) {
        files[file.path] = this.models.get(this.key(task.id, file.path))?.getValue() ?? task.code[file.path] ?? file.content;
      }
    }
    return files;
  }

  focus(): void {
    this.editor?.focus();
  }

  private model(monaco: MonacoApi, task: TaskView, path: string): Monaco.editor.ITextModel {
    const key = this.key(task.id, path);
    let model = this.models.get(key);
    if (!model) {
      const file = task.files.find((f) => f.path === path)!;
      const content = file.editable ? (task.code[path] ?? file.content) : file.content;
      model = monaco.editor.createModel(content, 'java', monaco.Uri.parse(`inmemory://task/${task.id}/${path}`));
      if (task.kind === 'CALIBRATION') {
        this.loader.disableSuggestions(model);
      }
      model.onDidChangeContent(() => this.changed.emit({ taskId: task.id, path }));
      this.models.set(key, model);
    }
    return model;
  }

  private key(taskId: string, path: string): string {
    return `${taskId}/${path}`;
  }
}
