# Development Plan - REQ-0004: Frontend Login y Autenticación JWT

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad técnica para adaptar o evolucionar la solución.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Modelos y Configuración de Conectividad
- **`TASK-01`**: Crear interfaces y tipos de datos en `src/app/core/auth/models/auth.models.ts` (`LoginRequest`, `LoginResponse`, `UserSession`, `Role`).
- **`TASK-02`**: Configurar `proxy.conf.json` en `frontend/` apuntando `/api` hacia `http://localhost:8080` y vincularlo en `angular.json` para desarrollo sin fricción de CORS.

### Fase 2: Servicio Centralizado `AuthService`
- **`TASK-03`**: Implementar `AuthService` en `src/app/core/auth/auth.service.ts` utilizando Angular Signals (`currentUser`, `isAuthenticated`, `userRole`), almacenamiento en `localStorage` y métodos `login()`, `logout()`, `getToken()`, `getCurrentUser()`.
- **`TASK-04`**: Crear tests unitarios en `auth.service.spec.ts` validando login exitoso, guardado de sesión, logout y persistencia.

### Fase 3: HTTP Interceptor y Guards
- **`TASK-05`**: Implementar `authInterceptor` en `src/app/core/auth/auth.interceptor.ts` inyectando `Authorization: Bearer <token>` (omitiendo `/api/auth/login`) y gestionando respuestas 401 con logout automático.
- **`TASK-06`**: Implementar `authGuard` y `guestGuard` en `src/app/core/auth/auth.guard.ts`.
- **`TASK-07`**: Registrar el interceptor en `app.config.ts`, configurar rutas en `app.routes.ts` y limpiar `app.html` con `<router-outlet />`.

### Fase 4: Pantalla de Login y Vista Dashboard
- **`TASK-08`**: Implementar `LoginComponent` en `src/app/pages/login/` con maquetación moderna, logo oficial de Akhana (`akhana-logo.png`), paleta verde bosque (`#2E5B27`) y dorado zen (`#F5B800`), validaciones reactivas, botón de alternar contraseña y estado de carga.
- **`TASK-09`**: Implementar `DashboardComponent` mínimo en `src/app/pages/dashboard/` con saludo personalizado, despliegue de rol (`ADMIN` / `SELLER`), botón de logout y diseño coherente.
- **`TASK-10`**: Crear pruebas unitarias para `login.component.spec.ts` y `dashboard.component.spec.ts`.

### Fase 5: Validación y Build
- **`TASK-11`**: Ejecutar suite de pruebas unitarias (`npx ng test --watch=false`) y verificar que todos los tests pasen con 100% de éxito.
- **`TASK-12`**: Ejecutar `npm run build` para asegurar cero errores de compilación TypeScript y empaquetado Angular de producción.
