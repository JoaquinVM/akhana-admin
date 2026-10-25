# Tasks: user-management-and-permissions

## 1. Migración de Base de Datos y Persistencia

- [x] 1.1 Crear migración Flyway `V9__create_user_management_and_permissions_tables.sql` agregando columnas `full_name`, `created_by`, `updated_by`, `deleted_at`, `deleted_by` a `app_users` y creando la tabla `user_permissions`. Verificar sintaxis y ejecución sin errores.
- [x] 1.2 Incorporar script de asignación inicial de permisos a todos los usuarios preexistentes (`admin`, `seller`) con todos los submenús del sistema (`SALES_POS`, `SALES_HISTORY`, `SALES_CASH_HISTORY`, `FINANCES_SUMMARY`, `FINANCES_TRANSACTIONS`, `FINANCES_ACCOUNTS`, `FINANCES_CATEGORIES`, `CATALOG_PRODUCTS`, `CATALOG_CATEGORIES`, `CATALOG_TAGS`, `PURCHASES_SUPPLIERS`, `SECURITY_USERS`). Verificar idempotencia en la migración.

## 2. Capa de Modelos y Repositorios Backend

- [x] 2.1 Actualizar la entidad `User.java` con `fullName`, `createdBy`, `updatedBy`, `deletedAt`, `deletedBy` y la relación `permissions` (`@ElementCollection` o entidad `UserPermission`). Verificar mapeo JPA y compilación.
- [x] 2.2 Actualizar `UserRepository.java` con métodos para búsqueda paginada por nombre y username, exclusión de eliminados lógicos (`deletedAt IS NULL`), y conteo de usuarios activos con permiso de administración (`countActiveUsersWithPermission`). Verificar consultas con pruebas unitarias en `UserRepositoryTest`.

## 3. Lógica de Negocio y Seguridad Backend

- [x] 3.1 Crear DTOs: `CreateUserRequest`, `UpdateUserRequest` (sin campo `username`), `UserResponse` (incluyendo datos de auditoría de 6 campos y permisos), `UserPermissionEnum`. Verificar que no admitan modificación de `username`.
- [x] 3.2 Implementar `UserService` y `UserServiceImpl` con reglas de negocio: creación con BCrypt y auditoría de creación, actualización con auditoría solo ante cambios reales, inmutabilidad de username, baja lógica con revocación, prohibición de auto-eliminación y prohibición de eliminar al último administrador.
- [x] 3.3 Actualizar `JwtAuthenticationFilter` y `AuthService` para rechazar usuarios con `deletedAt != null` o `active = false`, cargar autoridades de permisos y retornar permisos en `LoginResponse` y `UserProfileResponse`.
- [x] 3.4 Crear controlador `UserController` (`/api/users`) con endpoints `GET /`, `GET /{id}`, `POST /`, `PUT /{id}`, `DELETE /{id}` protegidos con autorización y pruebas en `UserControllerTest`. Verificar que `./gradlew test` pase exitosamente.

## 4. Servicios, Modelos y Control de Acceso Frontend

- [x] 4.1 Definir modelos e interfaces de usuario y permisos en `frontend/src/app/core/user/models/user.models.ts` y crear `UserService` con métodos para listar, crear, actualizar y dar de baja usuarios.
- [x] 4.2 Actualizar `AuthService` para almacenar, exponer mediante Signals reactivas y persistir los permisos del usuario autenticado.
- [x] 4.3 Implementar `permissionGuard` en Angular para validar permisos por ruta contra la configuración de `navigation.config.ts`, previniendo acceso por URL directa no autorizada y redirigiendo a la ruta permitida o a vista sin permisos.
- [x] 4.4 Actualizar `NavbarComponent` para filtrar reactivamente la navegación mostrando únicamente los submenús autorizados y ocultando los menús principales vacíos. Verificar con tests en `navbar.component.spec.ts`.

## 5. Pantalla de Gestión de Usuarios y Modales Básicos

- [x] 5.1 Crear componente `UsersComponent` en `frontend/src/app/pages/security/users/` y registrar la ruta en `app.routes.ts` (`/users` protegida con `permissionGuard`).
- [x] 5.2 Implementar cabecera, filtros, buscador y tabla paginada de usuarios con columnas: Nombre completo, Usuario, Creación, Modificación y Acciones exclusivas: "Ver auditoría", "Editar" y "Eliminar".
- [x] 5.3 Integrar `AuditModalComponent` reutilizado de productos para consultar de forma independiente los 6 campos de auditoría del usuario (`openAuditModal`), con título "Auditoría de Usuario" y subtítulo `${fullName} (${username})`, sin implementar vistas de detalle adicionales.
- [x] 5.4 Implementar modal de creación de usuario con validaciones reactivas, contraseñas coincidentes y matriz de selección de permisos (individual, por menú y global).
- [x] 5.5 Implementar modal de confirmación de baja lógica advirtiendo las consecuencias, bloqueando auto-eliminación y bloqueo del último administrador con mensajes claros.

## 6. Formulario de Edición con Detección Ergonómica de Cambios

- [x] 6.1 Implementar formulario de edición de usuario en `UsersComponent` con campo `username` bloqueado y de solo lectura, sin campos de contraseña, y selección de permisos.
- [x] 6.2 Implementar lógica de detección de cambios idéntica a `ProductsComponent`: señales `originalFormValues`, `formVersion`, `hasFormChanges`, método `isFieldModified`, badge `.field-modified-badge` y clase `.is-modified`.
- [x] 6.3 Configurar botón Guardar para habilitarse únicamente cuando existan cambios efectivos y el formulario sea válido; deshabilitarse automáticamente si se revierten todos los valores originales al estado inicial.
- [x] 6.4 Asegurar que si no hay cambios no se invoque la actualización ni se altere la auditoría de modificación.

## 7. Pruebas, Verificación Integral y Documentación

- [x] 7.1 Crear suite de pruebas unitarias para `UsersComponent` en `users.component.spec.ts` verificando renderizado, detección de cambios en edición, validaciones y apertura de modales.
- [x] 7.2 Ejecutar suite completa de tests de frontend (`npm test -- --watch=false`) y backend (`./gradlew test`) verificando 100% de aprobación.
- [x] 7.3 Actualizar `_context.md` en `database/`, `backend/` y `frontend/` documentando el nuevo módulo de usuarios, modelo de permisos y auditoría.
