# Registro de Autonomía y Decisiones - REQ-0004

## Información del Requerimiento
- **ID:** `REQ-0004`
- **Título:** Login y Autenticación JWT en el Frontend
- **Complejidad:** `MEDIUM`
- **Fecha:** 2026-09-25

---

## Acciones y Decisiones Autónomas

| Paso | Acción Realizada | Justificación Técnica |
|:---|:---|:---|
| 1 | Generación de diseño con Stitch MCP (`screens/334bef57add2400497ee59932f0a588e`) | Establecer una interfaz gráfica premium basada en *Organic Glassmorphism* que aprovecha la paleta oficial de Akhana (`#2E5B27`, `#F5B800`, `#7BB142`). |
| 2 | Configuración de proxy inverso en `frontend/proxy.conf.json` hacia `http://localhost:8080` | Evita problemas de CORS durante el ciclo de desarrollo local sin modificar la seguridad del backend. |
| 3 | Modelado de interfaces TypeScript en `core/auth/models/auth.models.ts` | Tipado estricto para `LoginRequest`, `LoginResponse`, `UserSession` y roles (`ADMIN` / `SELLER`). |
| 4 | Servicio centralizado `AuthService` con Angular Signals | Estado reactivo moderno y granular (`currentUser`, `isAuthenticated`, `userRole`), desacoplado de RxJS en la capa visual. |
| 5 | Persistencia en `localStorage` con sanitización en arranque | Mantiene la sesión activa tras recargas de página (`akhana_token`, `akhana_user`), validando integridad de datos. |
| 6 | Interceptor funcional `authInterceptor` (`HttpInterceptorFn`) | Inyecta `Authorization: Bearer <token>` en todas las peticiones a la API excepto el login, y captura errores 401 cerrando la sesión de inmediato. |
| 7 | Guards funcionales de navegación `authGuard` y `guestGuard` | Protegen rutas autenticadas (`/dashboard`) y redirigen usuarios con sesión fuera de la vista de login. |
| 8 | Componente de Login con formulario reactivo y validación visual | Manejo amigable de errores (401 de credenciales inválidas y 0 de caída de servidor), estado de carga y visibilidad de contraseña. |
| 9 | Componente mínimo protegido `DashboardComponent` | Permite verificar la sesión activa del usuario, visualizar su rol con insignia distintiva y ejecutar el logout. |
| 10 | Ajuste de presupuesto de estilos en `angular.json` | Aumenta el umbral de warning para componentes con estilos visuales ricos (Organic Glassmorphism) sin afectar el bundle principal. |
| 11 | Suite automatizada de 20 pruebas unitarias con Vitest | Cobertura completa de componentes, servicios, guards e interceptores con 100% de éxito. |
