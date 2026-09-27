import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

export type ApiStatus = 'UP' | 'DOWN' | 'UNREACHABLE';

interface HealthResponse {
  status: string;
}

@Injectable({ providedIn: 'root' })
export class ApiHealthService {
  private readonly http = inject(HttpClient);

  status(): Observable<ApiStatus> {
    return this.http.get<HealthResponse>('/api/actuator/health').pipe(
      map((response) => (response.status === 'UP' ? 'UP' : 'DOWN')),
      catchError(() => of<ApiStatus>('UNREACHABLE')),
    );
  }
}
