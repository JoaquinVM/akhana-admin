# Contexto de Directorio: frontend/

## Propósito y Responsabilidad
Capa de interfaz de usuario para el panel de administración de Akhana. Proyecto SPA desarrollado en **Angular 21** con arquitectura standalone components y enrutamiento modular.

## Estructura del Proyecto Angular
- `src/app/`:
  - `app.ts`: Componente raíz de la aplicación.
  - `app.config.ts`: Configuración global de providers (`provideRouter`, `provideHttpClient`).
  - `app.routes.ts`: Definición de rutas del frontend.
  - `components/`: Componentes UI reutilizables (a crear en futuros REQs).
  - `services/`: Servicios cliente HTTP para consumir la API de `backend/`.
- `public/`: Recursos estáticos e iconos.
- `angular.json`: Configuración del workspace Angular.
- `package.json`: Dependencias (Angular 21.2+, TypeScript 5.9+).

## Comandos Operativos
- Ejecución de desarrollo: `npm start` (inicia en `http://localhost:4200`)
- Compilación de producción: `npm run build`
- Pruebas unitarias: `npm test`

## Convenciones
1. Utilizar Standalone Components en Angular.
2. Inyectar `HttpClient` a través de servicios dedicados en `src/app/services/` sin realizar llamadas directas desde componentes.
3. Consumir el backend Spring Boot en `http://localhost:8080/api/...`.
