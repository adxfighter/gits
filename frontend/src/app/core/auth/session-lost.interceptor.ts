import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { statusOf } from '../http/http-errors';
import { AuthService } from './auth.service';

/** Employer and admin API paths: a 401 there means the session of the signed-in user is gone. */
const EMPLOYER_API = ['/api/employer/', '/api/invites', '/api/admin/'];

/**
 * The employer's session may end while a page is open (it expired, the api restarted): the next request of the
 * dashboard gets 401. The employer is then sent to the login page and comes back to the same page after it.
 */
export const sessionLostInterceptor: HttpInterceptorFn = (request, next) => {
  if (!EMPLOYER_API.some((path) => request.url.startsWith(path))) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (statusOf(error) === 401 && !router.url.startsWith('/login')) {
        auth.sessionLost();
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url, reason: 'expired' } });
      }
      return throwError(() => error);
    }),
  );
};
