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

  // Clave de permiso de la pantalla definida en app.routes.ts (PERMISSIONS); el backend exige la misma en sus endpoints
  const requiredPermission = route.data['permission'] as PermissionKey | undefined;

  // Si la ruta no especifica permiso requerido o si el usuario cuenta con él
  if (!requiredPermission || authService.hasPermission(requiredPermission)) {
    return true;
  }

  // Si no tiene el permiso requerido, buscar la primera ruta permitida según la jerarquía del sistema
  for (const group of NAVIGATION_CONFIG) {
    for (const child of group.children) {
      if (child.permissionKey && authService.hasPermission(child.permissionKey)) {
        if (state.url !== child.route) {
          return router.createUrlTree([child.route]);
        }
      }
    }
  }

  // Si no tiene permisos en ninguna sección del sistema, redirigir a vista sin permisos
  return router.createUrlTree(['/unauthorized']);
};
