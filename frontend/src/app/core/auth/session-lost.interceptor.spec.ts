import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from './auth.service';
import { sessionLostInterceptor } from './session-lost.interceptor';

describe('sessionLostInterceptor', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionLostInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function get(url: string): void {
    TestBed.inject(HttpClient).get(url).subscribe({ error: () => undefined });
  }

  it('sends the employer to the login page when the session is gone', () => {
    TestBed.inject(AuthService).user.set({ userId: 'u', email: 'e', role: 'EMPLOYER', companyId: 'c', companyName: 'C' });
    get('/api/employer/invites');
    http.expectOne('/api/employer/invites').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/', reason: 'expired' } });
    expect(TestBed.inject(AuthService).user()).toBeNull();
  });

  it('leaves other failures and candidate requests alone', () => {
    get('/api/employer/invites');
    http.expectOne('/api/employer/invites').flush({}, { status: 500, statusText: 'Error' });
    get('/api/candidate/me');
    http.expectOne('/api/candidate/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navigate).not.toHaveBeenCalled();
  });
});
