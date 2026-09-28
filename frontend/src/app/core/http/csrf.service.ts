import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap } from 'rxjs';

import { retryTransient } from './http-errors';

/**
 * The XSRF-TOKEN cookie every changing request needs (docs/api.md): Angular copies it into the X-XSRF-TOKEN header,
 * this service makes sure the cookie exists.
 */
@Injectable({ providedIn: 'root' })
export class CsrfService {
  private readonly http = inject(HttpClient);

  /** Issues the XSRF-TOKEN cookie. */
  csrf(): Observable<void> {
    return this.http.get('/api/auth/csrf').pipe(
      retryTransient(),
      map(() => undefined),
    );
  }

  /** The cookie may be missing after a reload of a deep link or a logout: fetch it first when it is. */
  withCsrf<T>(request: Observable<T>): Observable<T> {
    return document.cookie.split('; ').some((c) => c.startsWith('XSRF-TOKEN='))
      ? request
      : this.csrf().pipe(switchMap(() => request));
  }
}
