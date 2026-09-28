import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type * as Monaco from 'monaco-editor';

import { MonacoApi, MonacoLoader } from '../../core/editor/monaco-loader.service';

/** The candidate's final code: one read-only Monaco editor with a tab per file. */
@Component({
  selector: 'app-code-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (paths().length === 0) {
      <p class="muted" data-testid="code-empty">Кода нет.</p>
    } @else {
      <div class="code-viewer__tabs" role="tablist" aria-label="Файлы">
        @for (path of paths(); track path) {
          <button
            class="code-viewer__tab"
            type="button"
            role="tab"
            [class.code-viewer__tab--active]="path === active()"
            [attr.aria-selected]="path === active()"
            [title]="path"
            (click)="selected.set(path)"
            data-testid="code-tab"
          >
            {{ fileName(path) }}
          </button>
        }
      </div>
    }
    @if (failed()) {
      <p class="error">{{ failed() }}</p>
    }
    <div #host class="code-viewer__editor" [hidden]="paths().length === 0" data-testid="code-viewer"></div>
  `,
  styles: `
    :host { display: block; }
    .code-viewer__tabs { display: flex; flex-wrap: wrap; gap: 2px; border-bottom: 1px solid var(--border); }
    .code-viewer__tab {
      font: inherit; font-size: 13px; padding: 4px 10px; cursor: pointer;
      background: transparent; color: var(--text-muted);
      border: 1px solid transparent; border-bottom: none; border-radius: 6px 6px 0 0;
    }
    .code-viewer__tab--active { color: var(--text); background: var(--surface); border-color: var(--border); }
    .code-viewer__tab:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
    .code-viewer__editor { height: 380px; border: 1px solid var(--border); border-top: none; }
  `,
})
export class CodeViewer {
  readonly files = input.required<Record<string, string>>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly monaco = signal<MonacoApi | null>(null);
  private readonly models = new Map<string, Monaco.editor.ITextModel>();
  private editor?: Monaco.editor.IStandaloneCodeEditor;
  private destroyed = false;

  protected readonly selected = signal<string | null>(null);
  protected readonly failed = signal<string | null>(null);
  protected readonly paths = computed(() => Object.keys(this.files()).sort(byTypingLast));
  protected readonly active = computed(() => {
    const selected = this.selected();
    return selected !== null && this.paths().includes(selected) ? selected : (this.paths()[0] ?? null);
  });

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
          fontSize: 13,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          readOnly: true,
          domReadOnly: true,
          contextmenu: false,
          ariaLabel: 'Итоговый код кандидата, только чтение',
        });
        this.monaco.set(monaco);
      })
      .catch((error: unknown) => this.failed.set(error instanceof Error ? error.message : 'Не удалось показать код.'));
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.models.forEach((model) => model.dispose());
      this.editor?.dispose();
    });
    effect(() => {
      const monaco = this.monaco();
      const path = this.active();
      const files = this.files();
      if (!monaco || !this.editor || path === null) {
        return;
      }
      let model = this.models.get(path);
      if (!model || model.getValue() !== files[path]) {
        model?.dispose();
        // models without a URI of their own: several viewers on one page never clash
        model = monaco.editor.createModel(files[path], path.endsWith('.java') ? 'java' : 'plaintext');
        this.models.set(path, model);
      }
      this.editor.setModel(model);
    });
  }

  protected fileName(path: string): string {
    return path.slice(path.lastIndexOf('/') + 1);
  }
}

/** Java sources first, then the warm-up text files. */
function byTypingLast(a: string, b: string): number {
  const text = (path: string) => (path.endsWith('.java') ? 0 : 1);
  return text(a) - text(b) || a.localeCompare(b);
}
