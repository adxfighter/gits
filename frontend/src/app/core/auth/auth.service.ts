import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap, throwError } from 'rxjs';

import { CsrfService } from '../http/csrf.service';
import { retryTransient, statusOf } from '../http/http-errors';

export type UserRole = 'EMPLOYER' | 'ADMIN';

/** GET /api/auth/me. */
export interface CurrentUser {
  userId: string;
  email: string;
  role: UserRole;
  companyId: string;
  companyName: string;
}

/** Employer and admin sign-in (docs/api.md, «Работодатель»): the HTTP session cookie, CSRF on every change. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  /** The signed-in user as last seen by the app; null when signed out or not known yet. */
  readonly user = signal<CurrentUser | null>(null);

  /** The current user, or null without a session (401). Other failures are errors. */
  me(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>('/api/auth/me').pipe(
      retryTransient(),
      catchError((error: unknown) => (statusOf(error) === 401 ? of(null) : throwError(() => error))),
      tap((user) => this.user.set(user)),
    );
  }

  login(email: string, password: string): Observable<CurrentUser> {
    // a fresh token: the one of an earlier session may be stale
    return this.csrf.csrf().pipe(
      switchMap(() => this.http.post<CurrentUser>('/api/auth/login', { email, password })),
      tap((user) => this.user.set(user)),
    );
  }

  /**
   * Ends the session on the server. Without an answer (no connection, server error) the session may still be alive,
   * so the error is passed on and the user stays signed in. 403 is a stale CSRF token: once more with a fresh one.
   */
  logout(): Observable<void> {
    const post = () => this.http.post<void>('/api/auth/logout', null);
    return this.csrf.withCsrf(post()).pipe(
      catchError((error: unknown) =>
        statusOf(error) === 403 ? this.csrf.csrf().pipe(switchMap(post)) : throwError(() => error),
      ),
      tap(() => this.user.set(null)),
    );
  }

  /** The session ended on the server (expired, api restarted): the app forgets the user. */
  sessionLost(): void {
    this.user.set(null);
  }
}
