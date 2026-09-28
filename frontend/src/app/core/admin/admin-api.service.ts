import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CsrfService } from '../http/csrf.service';
import { retryTransient } from '../http/http-errors';
import { AdminSessionRow, ScoringResult, TemplateRow, VariantDetails } from './admin.models';

/** Administrator endpoints of the API (docs/api.md, «Администратор»). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);

  tasks(): Observable<TemplateRow[]> {
    return this.http.get<TemplateRow[]>('/api/admin/tasks').pipe(retryTransient());
  }

  variant(id: string): Observable<VariantDetails> {
    return this.http.get<VariantDetails>(`/api/admin/tasks/variants/${id}`).pipe(retryTransient());
  }

  sessions(): Observable<AdminSessionRow[]> {
    return this.http.get<AdminSessionRow[]>('/api/admin/sessions').pipe(retryTransient());
  }

  /** Recomputes indicators and score of a finished session; 409 while it runs or its solutions are not checked. */
  rescore(sessionId: string): Observable<ScoringResult> {
    return this.csrf.withCsrf(this.http.post<ScoringResult>(`/api/admin/sessions/${sessionId}/scoring`, null));
  }

  /** The research archive is a file download: the browser opens this address with the session cookie. */
  exportUrl(from: string | null, to: string | null): string {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    const query = params.toString();
    return '/api/admin/export' + (query ? `?${query}` : '');
  }
}
