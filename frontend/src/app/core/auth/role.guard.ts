import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AuthService, UserRole } from './auth.service';

/** Home page of each role: where a signed-in user goes by default. */
export const HOME_OF: Record<UserRole, string> = { EMPLOYER: '/employer', ADMIN: '/admin/tasks' };

/**
 * Pages of one role. Without a session the visitor goes to /login and comes back after it; a user of the other role
 * goes to their own section.
 */
function roleGuard(role: UserRole): CanActivateFn {
  return (_route, state) => {
    const router = inject(Router);
    const toLogin = (reason?: string) =>
      router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url, reason } });
    return inject(AuthService)
      .me()
      .pipe(
        map((user) => (user === null ? toLogin() : user.role === role ? true : router.parseUrl(HOME_OF[user.role]))),
        catchError(() => of(toLogin('offline'))),
      );
  };
}

export const employerGuard: CanActivateFn = roleGuard('EMPLOYER');

export const adminGuard: CanActivateFn = roleGuard('ADMIN');
