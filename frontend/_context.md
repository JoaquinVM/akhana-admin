# Contexto de Directorio: frontend/

## Propósito y Responsabilidad
Capa de interfaz de usuario para el panel de administración de Akhana. Proyecto SPA desarrollado en **Angular 21** con arquitectura standalone components, reactividad basada en Angular Signals, diseño *Corporate Organic Glassmorphism* (Stitch) alineado a la identidad de marca Akhana, navegación horizontal superior data-driven sin sidebar lateral, y enrutamiento modular protegido por JWT.

## Estructura del Proyecto Angular
- `src/app/`:
  - `app.ts`: Componente raíz de la aplicación.
  - `app.html`: Layout raíz (`<router-outlet />`).
  - `app.config.ts`: Configuración global de providers (`provideRouter`, `provideHttpClient(withInterceptors([authInterceptor]))`).
  - `app.routes.ts`: Enrutamiento principal. Configura layout protegido `MainLayoutComponent` para rutas internas (`/pos`, `/sales`, `/products`, `/categories`, `/tags`, `/users`), redirección por defecto de `/` a `/pos`, y `/login` para invitados.
  - `core/`:
    - `auth/`: Modelos (`auth.models.ts`), servicio centralizado con Signals (`auth.service.ts`), interceptor JWT (`auth.interceptor.ts`) y guards de navegación (`auth.guard.ts`).
    - `navigation/`:
      - `models/navigation.models.ts`: Interfaces `NavItem`, `NavGroup`.
      - `navigation.config.ts`: Estructura centralizada y extensible del menú principal (grupos Ventas, Catálogo, Seguridad).
  - `layout/`:
    - `navbar/`: Barra de navegación horizontal superior compacta (logo corporativo, menús desplegables en hover, detección reactiva de grupo/opción activa, chip de usuario autenticado y botón de logout).
    - `main-layout/`: Contenedor maestro autenticado que aloja el `NavbarComponent` y el `<router-outlet />` de vistas.
  - `pages/`:
    - `login/`: Pantalla de inicio de sesión con Organic Glassmorphism y validación reactiva.
    - `section-page/`: Contenedor reactivo data-driven reutilizable que renderiza el encabezado y estado de la sección activa según `route.data`.
- `public/`:
  - `images/akhana-logo.png`: Logo oficial de Akhana (círculo zen dorado y follaje verde).
- `proxy.conf.json`: Proxy de desarrollo que reenvía peticiones `/api` a `http://localhost:8080`.
- `angular.json`: Configuración del workspace Angular con proxy y presupuestos de estilo ajustados.
- `package.json`: Dependencias (Angular 21.2+, TypeScript 5.9+, Vitest).

## Comandos Operativos
- Ejecución de desarrollo: `PATH="/opt/homebrew/bin:$PATH" npm start` (inicia en `http://localhost:4200` con proxy a `:8080`)
- Compilación de producción: `PATH="/opt/homebrew/bin:$PATH" npm run build`
- Pruebas unitarias: `PATH="/opt/homebrew/bin:$PATH" npx ng test --watch=false`

## Convenciones
1. Utilizar Standalone Components en Angular.
2. Centralizar la configuración de navegación en `navigation.config.ts` para posibilitar extensión modular sin tocar el HTML.
3. Usar Angular Signals (`signal`, `computed`) para la reactividad en componentes y servicios.
4. Mantener la paleta y estética Corporate Organic Glassmorphism de Akhana: Verde Bosque (`#1B3B18` / `#2E5B27`), Dorado Zen (`#F5B800`), Acento Hoja (`#7BB142`).
5. Todas las rutas administrativas deben residir dentro de `MainLayoutComponent` bajo `canActivate: [authGuard]`.
