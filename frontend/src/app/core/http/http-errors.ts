import { HttpErrorResponse } from '@angular/common/http';
import { MonoTypeOperatorFunction, retry, timer } from 'rxjs';

/** Network failures and gateway errors are worth repeating; anything else is an answer of the server. */
export function isTransient(error: unknown): boolean {
  return error instanceof HttpErrorResponse && [0, 502, 503, 504].includes(error.status);
}

/** Repeats an idempotent request on transient failures: after 1, 2 and 4 seconds. */
export function retryTransient<T>(attempts = 3): MonoTypeOperatorFunction<T> {
  return retry({
    count: attempts,
    delay: (error, retryCount) => {
      if (!isTransient(error)) {
        throw error;
      }
      return timer(1000 * 2 ** (retryCount - 1));
    },
  });
}

/** HTTP status of an error, or undefined for anything that is not an HTTP error. */
export function statusOf(error: unknown): number | undefined {
  return error instanceof HttpErrorResponse ? error.status : undefined;
}

/** A message for the candidate: the server's Problem Details text, or a general one. */
export function messageOf(error: unknown, fallback = 'Что-то пошло не так. Попробуйте ещё раз.'): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Нет связи с сервером. Проверьте подключение — мы повторим попытку.';
    }
    const detail = (error.error as { detail?: unknown } | null)?.detail;
    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }
  }
  return fallback;
}
