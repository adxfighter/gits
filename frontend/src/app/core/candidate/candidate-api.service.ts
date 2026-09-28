import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap } from 'rxjs';

import { retryTransient } from '../http/http-errors';
import { TelemetryAccepted, TelemetryBatch } from '../telemetry/telemetry.models';
import {
  CandidateView,
  ConsentView,
  RunAccepted,
  RunView,
  SessionView,
  TaskView,
} from './candidate.models';

const API = '/api';

/** Candidate endpoints of the API (docs/api.md). Idempotent requests are repeated on network failures. */
@Injectable({ providedIn: 'root' })
export class CandidateApi {
  private readonly http = inject(HttpClient);

  /** Issues the XSRF-TOKEN cookie that every changing request needs. */
  csrf(): Observable<void> {
    return this.http.get(`${API}/auth/csrf`).pipe(
      retryTransient(),
      map(() => undefined),
    );
  }

  enter(token: string): Observable<CandidateView> {
    return this.csrf().pipe(
      switchMap(() => this.http.post<CandidateView>(`${API}/candidate/enter`, { token })),
    );
  }

  me(): Observable<CandidateView> {
    return this.http.get<CandidateView>(`${API}/candidate/me`).pipe(retryTransient());
  }

  consent(): Observable<ConsentView> {
    return this.http.get<ConsentView>(`${API}/candidate/consent`).pipe(retryTransient());
  }

  acceptConsent(): Observable<void> {
    return this.withCsrf(this.http.post<void>(`${API}/candidate/consent`, null));
  }

  /** Starts the session, or returns the running one: repeating is safe. */
  startSession(): Observable<SessionView> {
    return this.withCsrf(this.http.post<SessionView>(`${API}/candidate/session/start`, null)).pipe(
      retryTransient(),
    );
  }

  session(): Observable<SessionView> {
    return this.http.get<SessionView>(`${API}/candidate/session`).pipe(retryTransient());
  }

  finishSession(): Observable<SessionView> {
    return this.withCsrf(this.http.post<SessionView>(`${API}/candidate/session/finish`, null)).pipe(
      retryTransient(),
    );
  }

  task(id: string): Observable<TaskView> {
    return this.http.get<TaskView>(`${API}/candidate/tasks/${id}`).pipe(retryTransient());
  }

  saveCode(taskId: string, files: Record<string, string>): Observable<void> {
    return this.withCsrf(
      this.http.put<void>(`${API}/candidate/tasks/${taskId}/code`, { files }),
    ).pipe(retryTransient());
  }

  /** Not repeated automatically: a second run would count against the limit. */
  run(taskId: string, files: Record<string, string>): Observable<RunAccepted> {
    return this.withCsrf(this.http.post<RunAccepted>(`${API}/candidate/tasks/${taskId}/run`, { files }));
  }

  submit(taskId: string, files: Record<string, string>): Observable<RunAccepted> {
    return this.withCsrf(
      this.http.post<RunAccepted>(`${API}/candidate/tasks/${taskId}/submit`, { files }),
    );
  }

  runResult(runId: string): Observable<RunView> {
    return this.http.get<RunView>(`${API}/candidate/runs/${runId}`).pipe(retryTransient());
  }

  /** A telemetry batch; repeats are handled by the telemetry stream (the server is idempotent by seq). */
  telemetry(taskId: string, batch: TelemetryBatch): Observable<TelemetryAccepted> {
    return this.withCsrf(this.http.post<TelemetryAccepted>(`${API}/candidate/tasks/${taskId}/telemetry`, batch));
  }

  /** The URL a sendBeacon batch of the task goes to (docs/telemetry.md). */
  telemetryUrl(taskId: string): string {
    return `${API}/candidate/tasks/${taskId}/telemetry`;
  }

  /** The XSRF cookie may be missing after a reload of a deep link: fetch it first when it is. */
  private withCsrf<T>(request: Observable<T>): Observable<T> {
    return document.cookie.split('; ').some((c) => c.startsWith('XSRF-TOKEN='))
      ? request
      : this.csrf().pipe(switchMap(() => request));
  }
}
