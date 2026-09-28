import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { statusOf } from '../http/http-errors';
import { CandidateApi } from './candidate-api.service';

/**
 * Pages after the consent (/c/intro, /c/session) open only for a candidate who accepted it. Without consent the
 * candidate goes to /c/consent; without a valid candidate cookie (never entered, revoked, completed) to /c/closed.
 */
export const consentGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(CandidateApi)
    .me()
    .pipe(
      map((me) => (me.consentGiven ? true : router.parseUrl('/c/consent'))),
      catchError((error: unknown) => of(fallback(router, error))),
    );
};

/** The consent page itself needs an entered candidate; one who already agreed goes on to the rules. */
export const candidateGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(CandidateApi)
    .me()
    .pipe(
      map((me) => (me.consentGiven ? router.parseUrl('/c/intro') : true)),
      catchError((error: unknown) => of(fallback(router, error))),
    );
};

/** No candidate access: the closed page; any other failure (no connection, server error): the offline page. */
function fallback(router: Router, error: unknown): UrlTree {
  return router.parseUrl(statusOf(error) === 401 || statusOf(error) === 403 ? '/c/closed' : '/c/offline');
}
