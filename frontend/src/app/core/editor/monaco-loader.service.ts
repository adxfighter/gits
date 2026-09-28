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
      // the AMD build finds its editor worker (vs/assets/editor.worker-*.js) next to the loader by itself
      const base = new URL('assets/monaco/vs', document.baseURI).href;
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
      script.onerror = () => {
        script.remove();
        reject(new Error('Не удалось загрузить редактор кода. Обновите страницу.'));
      };
      document.head.appendChild(script);
    }).catch((error: unknown) => {
      // a failed load is not remembered: the next editor tries again
      this.loading = undefined;
      throw error;
    });
    return this.loading;
  }

  /** Models of the calibration block get no completion suggestions. */
  disableSuggestions(model: Monaco.editor.ITextModel): void {
    this.noSuggestionModels.add(model.uri.toString());
  }
}
