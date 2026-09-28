import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, GuardResult, MaybeAsync, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CandidateView } from './candidate.models';
import { candidateGuard, consentGuard } from './consent.guard';

describe('consent guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function me(consentGiven: boolean): CandidateView {
    return { candidateLabel: 'Иван', targetLevel: 'MIDDLE', status: 'STARTED', consentGiven };
  }

  async function run(guard: typeof consentGuard, answer: (request: ReturnType<HttpTestingController['expectOne']>) => void): Promise<GuardResult> {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as MaybeAsync<GuardResult>;
    const pending = firstValueFrom(result as Observable<GuardResult>);
    answer(http.expectOne('/api/candidate/me'));
    return pending;
  }

  function url(result: GuardResult): string {
    return TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('lets a candidate who accepted the consent through', async () => {
    expect(await run(consentGuard, (request) => request.flush(me(true)))).toBe(true);
  });

  it('sends a candidate without consent to the consent page', async () => {
    const result = await run(consentGuard, (request) => request.flush(me(false)));
    expect(url(result)).toBe('/c/consent');
  });

  it('sends a visitor without candidate access to the closed page', async () => {
    const result = await run(consentGuard, (request) =>
      request.flush({ detail: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' }),
    );
    expect(url(result)).toBe('/c/closed');
  });

  it('sends the candidate to the offline page when the server fails', async () => {
    const result = await run(consentGuard, (request) =>
      request.flush(null, { status: 500, statusText: 'Server Error' }),
    );
    expect(url(result)).toBe('/c/offline');
  });

  it('opens the consent page only for a candidate who has not agreed yet', async () => {
    expect(await run(candidateGuard, (request) => request.flush(me(false)))).toBe(true);
    expect(url(await run(candidateGuard, (request) => request.flush(me(true))))).toBe('/c/intro');
  });
});
