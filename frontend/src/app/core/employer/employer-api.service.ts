import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CsrfService } from '../http/csrf.service';
import { retryTransient } from '../http/http-errors';
import { CreatedInvite, InviteRow, InviteStatus, Level, SessionReport } from './employer.models';

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
}
