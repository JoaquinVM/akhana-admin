# Estrategia de Pruebas - REQ-0007: Redirección Inmediata a Login al Cerrar Sesión

## Pruebas Unitarias Frontend (Vitest)

1. **TEST-AUTH-LOGOUT-01: Limpieza de estado y navegación inmediata a /login**
   - Archivo: `frontend/src/app/core/auth/auth.service.spec.ts`
   - Acción: Invocar `authService.logout()`.
   - Aserción: `isAuthenticated` es `false`, token es `null`, usuario es `null`, almacenamiento limpio, y `router.navigate(['/login'])` ejecutado.

2. **TEST-NAVBAR-LOGOUT-01: Invocación de logout y cierre de menús**
   - Archivo: `frontend/src/app/layout/navbar/navbar.component.spec.ts`
   - Acción: Hacer clic en botón de logout.
   - Aserción: `authService.logout()` invocado y menús cerrados.
