# Historial de Conversación - REQ-0007: Redirección Inmediata a Login al Cerrar Sesión

### 2026-09-26T11:01:00-04:00 - Usuario
> **Descripción del Problema:**
> Se identificó un comportamiento incorrecto al cerrar sesión. Actualmente, al ejecutar la acción de Cerrar sesión, se elimina correctamente la información visual del usuario autenticado, pero el sistema no redirige inmediatamente a la pantalla de Login.
> 
> **Comportamiento esperado:**
> 1. Invalidar/eliminar la información correspondiente a la sesión actual.
> 2. Redirigir inmediatamente al usuario a la pantalla de Login.
> 3. Impedir que el usuario continúe navegando o ejecutando acciones que requieran autenticación.
> 4. No depender de un refresh, de una nueva acción del usuario o del vencimiento de algún tiempo para realizar la redirección.

### 2026-09-26T11:03:00-04:00 - Orquestador
> Requerimiento registrado como `REQ-0007-immediate-logout-redirect`.
> Complejidad clasificada: `SMALL` (Bugfix en capa frontend de autenticación y navegación).
> Causa raíz identificada: `AuthService.logout()` llamaba a `clearSession()` pero no ejecutaba la navegación a `/login`.
