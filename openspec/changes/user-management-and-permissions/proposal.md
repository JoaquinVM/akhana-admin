# Proposal: user-management-and-permissions

## Why

El sistema cuenta actualmente con autenticación mediante JWT y usuarios semilla en base de datos (`admin`, `seller`), pero carece de un módulo visual y operativo para administrar usuarios, asignar permisos granulares por submenú y controlar el acceso tanto en el frontend como en el backend. Se requiere una solución robusta que proteja la integridad referencial y de auditoría histórica, mantenga la inmutabilidad de los nombres de usuario, preserve las sesiones válidas e invalide inmediatamente el acceso de usuarios eliminados o no autorizados.

## What Changes

- **Gestión visual de usuarios:** Nueva pantalla en **Seguridad → Usuarios** (`/users`) con listado paginado, búsqueda por nombre completo y nombre de usuario, y acciones exclusivas idénticas a la pantalla de productos: **Ver auditoría** (reutilizando `AuditModalComponent` para los 6 campos), **Editar** y **Eliminar** (baja lógica confirmada). No se incluye vista ni modal independiente de detalle.
- **Creación de usuarios:** Formulario para registrar nuevos usuarios con nombre completo, nombre de usuario inmutable, contraseña con hash BCrypt y confirmación, y matriz de asignación de permisos por submenú.
- **Edición ergonómica con detección de cambios:** Formulario de edición con nombre de usuario de solo lectura (inmutable), edición de nombre completo y permisos, sin exposición ni requerimiento de contraseña. Detección reactiva de modificaciones idéntica a la pantalla de productos (`.field-modified-badge`, `.is-modified`, botón Guardar deshabilitado si no hay cambios efectivos o es inválido, y reversión automática si se restauran valores originales).
- **Auditoría de 6 campos y baja lógica:** Registro de `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy` generados exclusivamente en el servidor. Preservación histórica sin borrado físico (`active = false` / `deleted_at IS NOT NULL`). Prohibición estricta de auto-eliminación y de eliminación del último administrador.
- **Matriz de permisos granulares por submenú:** Modelo relacional de permisos asociados a la estructura real de navegación (`Ventas`, `Finanzas`, `Catálogo`, `Compras`, `Seguridad`). Soporte para selección individual, por menú completo y global, con estados indeterminados.
- **Control de acceso integral (Frontend + Backend):**
  - **Frontend:** Filtro reactivo en el Navbar (ocultando submenús y menús vacíos no autorizados), Guards en Angular (`authGuard` / `permissionGuard`) que impiden el acceso por URL directa y redirigen a la pantalla autorizada por defecto o a una vista de acceso no disponible.
  - **Backend:** Protección de endpoints con validación de permisos en tiempo real (`@PreAuthorize` o interceptor de permisos), invalidación inmediata de sesiones de usuarios dados de baja al consultar el usuario en base de datos durante el filtro JWT.
- **Migración de datos inicial (V9):** Migración idempotente en PostgreSQL que agrega los campos de auditoría, la tabla de permisos de usuario y otorga a todos los usuarios existentes al momento de la migración acceso completo a todos los submenús del sistema.

## Capabilities

### New Capabilities
- `user-management`: Administrar usuarios, permisos granulares por submenú, auditoría de 6 campos, baja lógica y control de acceso en frontend y backend.

### Modified Capabilities
<!-- No requirement changes in existing capabilities -->

## Impact

- **Base de Datos:** Migración Flyway `V9__create_user_management_and_permissions_tables.sql` alterando `app_users` (agregando `full_name`, `created_by`, `updated_by`, `deleted_at`, `deleted_by`) y creando la tabla `user_permissions`.
- **Backend:**
  - Actualización de `User.java` y `UserRepository.java`.
  - Nueva entidad `UserPermission.java` y `UserPermissionRepository.java`.
  - Nuevo servicio `UserService` y controlador `UserController` (`/api/users`).
  - Actualización de `JwtAuthenticationFilter.java` para validar el estado activo/no-eliminado y cargar las autoridades basadas en permisos.
  - Actualización de `AuthService.java` y DTOs (`LoginResponse`, `UserProfileResponse`) para incluir permisos y nombre completo.
- **Frontend:**
  - Actualización de `AuthService` para almacenar y exponer reactivamente los permisos del usuario actual.
  - Actualización de `NavbarComponent` para filtrar menús y submenús según los permisos del usuario.
  - Nuevo `permissionGuard` en Angular para proteger rutas en `app.routes.ts`.
  - Nueva pantalla `/users` (`UsersComponent`) con tabla, modales de creación, edición con detección de cambios, detalle y eliminación lógica.
  - Actualización de `_context.md` en frontend, backend y database.
