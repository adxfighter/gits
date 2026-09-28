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

  /** Ends the session on the server; the app forgets the user even when the server could not be reached. */
  logout(): Observable<void> {
    return this.csrf.withCsrf(this.http.post<void>('/api/auth/logout', null)).pipe(
      catchError(() => of(undefined)),
      tap(() => this.user.set(null)),
    );
  }
}
