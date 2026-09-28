import { Pipe, PipeTransform } from '@angular/core';
import { marked } from 'marked';

/**
 * Markdown of task statements and the consent text to HTML. The result is bound with [innerHTML], which Angular
 * sanitizes, so raw HTML in the source cannot run scripts.
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  transform(source: string | null | undefined): string {
    return source ? (marked.parse(source, { async: false, gfm: true }) as string) : '';
  }
}
