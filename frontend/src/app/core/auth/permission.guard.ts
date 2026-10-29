import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';
import { NAVIGATION_CONFIG, PermissionKey } from '../navigation/navigation.config';

export const permissionGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  // Screen permission key set in app.routes.ts (PERMISSIONS); the backend requires the same one on its endpoints
  const requiredPermission = route.data['permission'] as PermissionKey | undefined;

  // The route needs no permission, or the user has it
  if (!requiredPermission || authService.hasPermission(requiredPermission)) {
    return true;
  }

  // Without the required permission, go to the first allowed route in menu order
  for (const group of NAVIGATION_CONFIG) {
    for (const child of group.children) {
      if (child.permissionKey && authService.hasPermission(child.permissionKey)) {
        if (state.url !== child.route) {
          return router.createUrlTree([child.route]);
        }
      }
    }
  }

  // Without permission for any screen, go to the no-access view
  return router.createUrlTree(['/unauthorized']);
};
