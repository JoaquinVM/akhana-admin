import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // Omit token injection on the login endpoint
  if (req.url.includes('/api/auth/login')) {
    return next(req);
  }

  const token = authService.getToken();
  const authReq = token
    ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
    : req;

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // If a protected request fails with 401, clear local session and redirect to login
      if (error.status === 401 && !req.url.includes('/api/auth/login')) {
        authService.clearSession();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
