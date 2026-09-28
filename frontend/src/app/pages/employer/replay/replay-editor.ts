import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type * as Monaco from 'monaco-editor';

import { MonacoApi, MonacoLoader } from '../../../core/editor/monaco-loader.service';

/**
 * The candidate's editor during the replay: read-only Monaco, a model per file. A new frame changes a model by the
 * smallest edit that turns the old text into the new one, so the view keeps its scroll while the text is typed.
 * The candidate's cursor is drawn as a caret decoration.
 */
@Component({
  selector: 'app-replay-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (failed()) {
      <p class="error">{{ failed() }}</p>
    }
    <div #host class="replay-editor" data-testid="replay-editor"></div>
  `,
  styles: `
    :host { display: block; position: relative; min-height: 0; }
    .replay-editor { position: absolute; inset: 0; }
  `,
})
export class ReplayEditor {
  /** Text of every file at the current moment. */
  readonly files = input.required<Readonly<Record<string, string>>>();
  /** The file shown. */
  readonly path = input.required<string | null>();
  /** The candidate's cursor, drawn when it is in the file shown. */
  readonly cursor = input<{ readonly file: string; readonly offset: number } | null>(null);

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly monaco = signal<MonacoApi | null>(null);
  private readonly models = new Map<string, Monaco.editor.ITextModel>();
  private editor?: Monaco.editor.IStandaloneCodeEditor;
  private decorations?: Monaco.editor.IEditorDecorationsCollection;
  private destroyed = false;
  protected readonly failed = signal<string | null>(null);

  constructor() {
    inject(MonacoLoader)
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
          readOnly: true,
          domReadOnly: true,
          contextmenu: false,
          renderLineHighlight: 'none',
          ariaLabel: 'Код кандидата в момент записи, только чтение',
        });
        this.decorations = this.editor.createDecorationsCollection();
        this.monaco.set(monaco);
      })
      .catch((error: unknown) => this.failed.set(error instanceof Error ? error.message : 'Не удалось показать код.'));
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.editor?.dispose();
      this.models.forEach((model) => model.dispose());
    });
    effect(() => {
      const monaco = this.monaco();
      const path = this.path();
      const files = this.files();
      const cursor = this.cursor();
      if (!monaco || !this.editor || path === null) {
        return;
      }
      const text = files[path] ?? '';
      let model = this.models.get(path);
      if (!model) {
        // models without a URI of their own: they never clash with other editors on the page
        model = monaco.editor.createModel(text, path.endsWith('.java') ? 'java' : 'plaintext');
        this.models.set(path, model);
      } else {
        update(monaco, model, text);
      }
      if (this.editor.getModel() !== model) {
        this.editor.setModel(model);
      }
      this.showCursor(monaco, model, cursor?.file === path ? cursor.offset : null);
    });
  }

  private showCursor(monaco: MonacoApi, model: Monaco.editor.ITextModel, offset: number | null): void {
    if (offset === null) {
      this.decorations?.clear();
      return;
    }
    const position = model.getPositionAt(offset);
    this.decorations?.set([
      {
        range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
        options: { beforeContentClassName: 'replay-caret', stickiness: 1 },
      },
      {
        range: new monaco.Range(position.lineNumber, 1, position.lineNumber, 1),
        options: { isWholeLine: true, className: 'replay-caret-line' },
      },
    ]);
    this.editor?.revealPositionInCenterIfOutsideViewport(position);
  }
}

/** Replaces only the changed middle of the text: the common start and end stay, and so does the view. */
function update(monaco: MonacoApi, model: Monaco.editor.ITextModel, text: string): void {
  const old = model.getValue();
  if (old === text) {
    return;
  }
  let start = 0;
  const shorter = Math.min(old.length, text.length);
  while (start < shorter && old.charCodeAt(start) === text.charCodeAt(start)) {
    start++;
  }
  let end = 0;
  while (end < shorter - start && old.charCodeAt(old.length - 1 - end) === text.charCodeAt(text.length - 1 - end)) {
    end++;
  }
  const from = model.getPositionAt(start);
  const to = model.getPositionAt(old.length - end);
  model.applyEdits([
    {
      range: new monaco.Range(from.lineNumber, from.column, to.lineNumber, to.column),
      text: text.slice(start, text.length - end),
    },
  ]);
}
