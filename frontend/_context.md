# Contexto de Directorio: frontend/

## Propósito y Responsabilidad
Capa de interfaz de usuario para el panel de administración de Akhana. Proyecto SPA desarrollado en **Angular 21** con arquitectura standalone components, reactividad basada en Angular Signals, diseño *Organic Glassmorphism* alineado a la identidad de marca Akhana, y enrutamiento modular protegido por JWT.

## Estructura del Proyecto Angular
- `src/app/`:
  - `app.ts`: Componente raíz de la aplicación.
  - `app.html`: Layout raíz (`<router-outlet />`).
  - `app.config.ts`: Configuración global de providers (`provideRouter`, `provideHttpClient(withInterceptors([authInterceptor]))`).
  - `app.routes.ts`: Definición de rutas (`/login`, `/dashboard`, redirección raíz).
  - `core/auth/`:
    - `models/auth.models.ts`: Tipos e interfaces (`LoginRequest`, `LoginResponse`, `UserSession`, `Role`).
    - `auth.service.ts`: Servicio centralizado de autenticación con Signals (`currentUser`, `isAuthenticated`, `userRole`) y almacenamiento en `localStorage` (`akhana_token`, `akhana_user`).
    - `auth.interceptor.ts`: Interceptor funcional HTTP que inyecta el token Bearer en peticiones salientes (omite `/api/auth/login`) y redirige a `/login` al recibir 401.
    - `auth.guard.ts`: Guards funcionales (`authGuard` para áreas protegidas, `guestGuard` para invitados).
  - `pages/`:
    - `login/`: Pantalla de inicio de sesión con diseño Organic Glassmorphism, validación de formularios reactivos, estado de carga y retroalimentación de errores.
    - `dashboard/`: Panel principal protegido con saludo de usuario, visualización de rol (`ADMIN` / `SELLER`) y botón de cerrar sesión.
- `public/`:
  - `images/akhana-logo.png`: Logo oficial de Akhana (círculo zen dorado y follaje verde).
- `proxy.conf.json`: Proxy de desarrollo que reenvía peticiones `/api` a `http://localhost:8080`.
- `angular.json`: Configuración del workspace Angular con proxy y presupuestos de estilo ajustados.
- `package.json`: Dependencias (Angular 21.2+, TypeScript 5.9+, Vitest).

## Comandos Operativos
- Ejecución de desarrollo: `PATH="/opt/homebrew/bin:$PATH" npm start` (inicia en `http://localhost:4200` con proxy a `:8080`)
- Compilación de producción: `PATH="/opt/homebrew/bin:$PATH" npm run build`
- Pruebas unitarias: `PATH="/opt/homebrew/bin:$PATH" npm test`

## Convenciones
1. Utilizar Standalone Components en Angular.
2. Inyectar `HttpClient` a través de servicios dedicados en `src/app/core/` o `src/app/services/` sin llamadas directas en componentes.
3. Consumir el backend Spring Boot vía `/api/...` aprovechando el proxy en desarrollo.
4. Usar Angular Signals (`signal`, `computed`) para la gestión reactiva de estado en servicios y componentes.
5. Mantener la paleta de identidad visual de Akhana: Verde Bosque (`#2E5B27`), Dorado Zen (`#F5B800`), Acento Hoja (`#7BB142`).
