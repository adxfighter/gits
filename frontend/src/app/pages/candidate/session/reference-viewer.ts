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

import { MonacoApi, MonacoLoader } from '../../../core/editor/monaco-loader.service';

/**
 * A read-only view of the warm-up sample (Sample.txt) above the typing editor. It has its own Monaco editor
 * and model, so the main editor — and the telemetry attached to it — stays the only place where the candidate types.
 */
@Component({
  selector: 'app-reference-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #host class="editor-host" data-testid="reference-viewer"></div>`,
  styles: `
    :host { display: block; position: relative; min-height: 0; }
    .editor-host { position: absolute; inset: 0; }
  `,
})
export class ReferenceViewer {
  readonly content = input.required<string>();
  readonly label = input('Образец, только чтение');
  /** Ctrl+S with the focus on the sample saves the candidate's code, as in the main editor. */
  readonly saveRequested = output<void>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly monaco = signal<MonacoApi | null>(null);
  private editor?: Monaco.editor.IStandaloneCodeEditor;
  private destroyed = false;

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
          readOnlyMessage: { value: 'Это образец: наберите его в нижнем окне' },
          // a model without a URI of its own: it never clashes with the task's models
          model: monaco.editor.createModel(this.content(), 'java'),
          contextmenu: false,
          ariaLabel: this.label(),
        });
        this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => this.saveRequested.emit());
        this.monaco.set(monaco);
      })
      .catch(() => undefined);
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.editor?.getModel()?.dispose();
      this.editor?.dispose();
    });
    effect(() => {
      const content = this.content();
      if (this.monaco() && this.editor && this.editor.getValue() !== content) {
        this.editor.setValue(content);
      }
    });
  }
}
