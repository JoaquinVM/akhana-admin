# Plan de Desarrollo - REQ-0007: Redirección Inmediata a Login al Cerrar Sesión

## Tareas

1. **TASK-01: Inyección de Router y Redirección en `AuthService`**
   - Modificar `frontend/src/app/core/auth/auth.service.ts`.
   - Inyectar `Router`.
   - Actualizar `logout()` para ejecutar `this.router.navigate(['/login'])` inmediatamente después de `this.clearSession()`.

2. **TASK-02: Verificación de `NavbarComponent`**
   - Verificar que `logout()` en `NavbarComponent` cierre menús y delegue en `authService.logout()`.

3. **TASK-03: Actualización de Pruebas Unitarias**
   - Actualizar `auth.service.spec.ts` para proveer `provideRouter([])` y verificar con spy que `router.navigate(['/login'])` es llamado.
   - Ejecutar la suite completa de pruebas unitarias (`npx ng test --watch=false`).

4. **TASK-04: Verificación de Build de Producción**
   - Ejecutar `npm run build` para comprobar cero errores.
