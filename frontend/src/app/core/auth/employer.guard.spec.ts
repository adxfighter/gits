import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AuthService, CurrentUser } from './auth.service';
import { employerGuard } from './employer.guard';

describe('employerGuard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function user(role: CurrentUser['role']): CurrentUser {
    return { userId: 'u', email: 'employer@demo.local', role, companyId: 'c', companyName: 'Демо' };
  }

  async function run(answer: (request: ReturnType<HttpTestingController['expectOne']>) => void): Promise<GuardResult> {
    const result = TestBed.runInInjectionContext(() =>
      employerGuard({} as ActivatedRouteSnapshot, { url: '/employer/sessions/s-1' } as RouterStateSnapshot),
    ) as MaybeAsync<GuardResult>;
    const pending = firstValueFrom(result as Observable<GuardResult>);
    answer(http.expectOne('/api/auth/me'));
    return pending;
  }

  function url(result: GuardResult): string {
    return decodeURIComponent(TestBed.inject(Router).serializeUrl(result as UrlTree));
  }

  it('lets a signed-in employer through and remembers the user for the header', async () => {
    expect(await run((request) => request.flush(user('EMPLOYER')))).toBe(true);
    expect(TestBed.inject(AuthService).user()?.email).toBe('employer@demo.local');
  });

  it('sends a visitor without a session to the login page and back afterwards', async () => {
    const result = await run((request) => request.flush({}, { status: 401, statusText: 'Unauthorized' }));
    expect(url(result)).toBe('/login?returnUrl=/employer/sessions/s-1');
  });

  it('sends an administrator to the login page with a note', async () => {
    const result = await run((request) => request.flush(user('ADMIN')));
    expect(url(result)).toBe('/login?returnUrl=/employer/sessions/s-1&reason=role');
  });

  it('tells a lost connection from a missing session', async () => {
    const result = await run((request) => request.flush({}, { status: 500, statusText: 'Error' }));
    expect(url(result)).toContain('reason=offline');
  });
});
