import { Injectable } from '@angular/core';
import type * as Monaco from 'monaco-editor';

import { registerJavaCompletions } from './java-completions';

export type MonacoApi = typeof Monaco;

interface AmdRequire {
  (modules: string[], onLoad: () => void, onError: (error: unknown) => void): void;
  config(options: { paths: Record<string, string> }): void;
}

declare global {
  interface Window {
    monaco?: MonacoApi;
    MonacoEnvironment?: { getWorkerUrl(moduleId: string, label: string): string };
  }
}

/**
 * Loads Monaco from the app's own assets (/assets/monaco/vs, copied from node_modules at build time), never from a
 * CDN. Loaded once, on the first editor, so pages without an editor do not pay for it.
 */
@Injectable({ providedIn: 'root' })
export class MonacoLoader {
  private loading?: Promise<MonacoApi>;
  private readonly noSuggestionModels = new Set<string>();

  load(): Promise<MonacoApi> {
    this.loading ??= new Promise<MonacoApi>((resolve, reject) => {
      const base = new URL('assets/monaco/vs', document.baseURI).href;
      window.MonacoEnvironment = {
        getWorkerUrl: () => `${base}/editor/editor.worker.js`,
      };
      const script = document.createElement('script');
      script.src = `${base}/loader.js`;
      script.onload = () => {
        const amd = (window as unknown as { require: AmdRequire }).require;
        amd.config({ paths: { vs: base } });
        amd(
          ['vs/editor/editor.main'],
          () => {
            const monaco = window.monaco!;
            registerJavaCompletions(monaco, (model) => this.noSuggestionModels.has(model.uri.toString()));
            resolve(monaco);
          },
          reject,
        );
      };
      script.onerror = () => reject(new Error('Не удалось загрузить редактор кода'));
      document.head.appendChild(script);
    });
    return this.loading;
  }

  /** Models of the calibration block get no completion suggestions. */
  disableSuggestions(model: Monaco.editor.ITextModel): void {
    this.noSuggestionModels.add(model.uri.toString());
  }
}
