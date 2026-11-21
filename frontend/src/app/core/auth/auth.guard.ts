import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protects private routes. Redirects unauthenticated users to /login and users
 * that must change their password to /change-password.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (authService.mustChangePassword()) {
    return router.createUrlTree(['/change-password']);
  }

  return true;
};

/**
 * Prevents authenticated users from unnecessarily accessing /login. Sends them to '/', where homeRedirect (DSH-04)
 * picks the dashboard or the POS.
 */
export const guestGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return router.createUrlTree([authService.mustChangePassword() ? '/change-password' : '/']);
  }

  return true;
};

/**
 * The change-password screen only needs a session (the password change itself is validated by the backend).
 */
export const sessionGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.isAuthenticated() ? true : router.createUrlTree(['/login']);
};
