import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';

import { CsrfService } from '../http/csrf.service';
import { retryTransient } from '../http/http-errors';
import { ReplayPage } from '../replay/replay.models';
import { CreatedInvite, InviteRow, InviteStatus, Level, SessionReport } from './employer.models';

/** The largest page of the replay API (batches): fewer requests for a long session. */
export const REPLAY_PAGE_BATCHES = 200;

/** Employer endpoints of the API (docs/api.md). Reads are repeated on network failures, changes are not. */
@Injectable({ providedIn: 'root' })
export class EmployerApi {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  invites(status: InviteStatus | null): Observable<InviteRow[]> {
    const params = status ? new HttpParams().set('status', status) : undefined;
    return this.http.get<InviteRow[]>('/api/employer/invites', { params }).pipe(retryTransient());
  }

  createInvite(candidateLabel: string, targetLevel: Level): Observable<CreatedInvite> {
    return this.csrf.withCsrf(this.http.post<CreatedInvite>('/api/invites', { candidateLabel, targetLevel }));
  }

  /** Revokes an unused invite; repeating is safe (204). */
  revokeInvite(id: string): Observable<void> {
    return this.csrf.withCsrf(this.http.delete<void>(`/api/employer/invites/${id}`));
  }

  report(sessionId: string): Observable<SessionReport> {
    return this.http.get<SessionReport>(`/api/employer/sessions/${sessionId}/report`).pipe(retryTransient());
  }

  replayPage(sessionTaskId: string, fromSeq: number): Observable<ReplayPage> {
    const params = new HttpParams().set('fromSeq', fromSeq).set('limit', REPLAY_PAGE_BATCHES);
    return this.http
      .get<ReplayPage>(`/api/employer/session-tasks/${sessionTaskId}/replay`, { params })
      .pipe(retryTransient());
  }

  /** The whole recording of a task: every page, their events joined in order. */
  replay(sessionTaskId: string): Observable<ReplayPage> {
    return this.replayPage(sessionTaskId, 0).pipe(
      expand((page) => (page.nextSeq === null ? EMPTY : this.replayPage(sessionTaskId, page.nextSeq))),
      reduce((all, page) => ({ ...page, fromSeq: all.fromSeq, events: all.events.concat(page.events) })),
    );
  }
}
