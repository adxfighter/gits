import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService } from './auth.service';

/**
 * Employer pages need a signed-in employer. Without a session the visitor goes to /login and comes back after it;
 * an administrator, who has no employer dashboard, sees a note on the login page.
 */
export const employerGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const toLogin = (reason?: string) =>
    router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url, reason } });
  return inject(AuthService)
    .me()
    .pipe(
      map((user) => (user === null ? toLogin() : user.role === 'EMPLOYER' ? true : toLogin('role'))),
      catchError(() => of(toLogin('offline'))),
    );
};
