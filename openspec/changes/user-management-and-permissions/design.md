# Design: user-management-and-permissions

## Context

El sistema cuenta con autenticación JWT stateless. La tabla `app_users` almacena usuarios con `username`, `password_hash`, `role`, `active`, `created_at` y `updated_at`. Existen usuarios iniciales (`admin`, `seller`). La navegación en frontend está centralizada en `navigation.config.ts` y las rutas en `app.routes.ts`.

Ver [proposal.md](file:///Users/joaquin/Documents/Akhana%20Admin/openspec/changes/user-management-and-permissions/proposal.md) y [spec.md](file:///Users/joaquin/Documents/Akhana%20Admin/openspec/changes/user-management-and-permissions/specs/user-management/spec.md) para los requerimientos funcionales.

## Goals / Non-Goals

**Goals:**
- Módulo completo de gestión de usuarios en `/users` bajo el menú `Seguridad`.
- Permisos granulares asignados por submenú real del sistema.
- Auditoría estricta de 6 campos generada exclusivamente por el backend (`createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`).
- Inmutabilidad inquebrantable del campo `username`.
- Detección reactiva de cambios en el formulario de edición idéntica a `ProductsComponent`.
- Filtro dinámico en el Navbar y protección estricta de rutas con Angular Guard (`permissionGuard`).
- Protección de endpoints en Spring Security con validación de permisos en base de datos.
- Baja lógica y revocación inmediata de acceso sin eliminar registros históricos.
- Migración Flyway V9 idempotente que otorga permisos iniciales completos a todos los usuarios preexistentes.

**Non-Goals:**
- Gestión de roles jerárquicos personalizados (RBAC dinámico); los permisos se asignan directamente al usuario por submenú conforme a los requerimientos.
- Recuperación o reseteo de contraseñas por correo electrónico (la contraseña no se toca en la edición general).
- Edición del nombre de usuario una vez creado.
- Modal o pantalla adicional de detalle de usuario (se prescinde de ella, reutilizando `AuditModalComponent` para la auditoría de 6 campos y el modal de edición para consultar/modificar datos y permisos).

## Decisions

### 1. Modelo de Datos y Migración Flyway V9
**Decisión:** Extender `app_users` y crear una tabla relacional para permisos:
```sql
ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS deleted_by VARCHAR(100);

-- Actualizar usuarios existentes con nombre completo por defecto si es nulo
UPDATE app_users SET full_name = 'Administrador General' WHERE username = 'admin' AND full_name IS NULL;
UPDATE app_users SET full_name = 'Vendedor' WHERE username = 'seller' AND full_name IS NULL;
UPDATE app_users SET created_by = 'system' WHERE created_by IS NULL;
ALTER TABLE app_users ALTER COLUMN full_name SET NOT NULL;

CREATE TABLE IF NOT EXISTS user_permissions (
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, permission_key)
);
CREATE INDEX IF NOT EXISTS idx_user_permissions_user_id ON user_permissions(user_id);
```
**Rationale:** Permite una relación 1:N limpia, escalable y con integridad referencial, sin almacenar JSONs o arrays difíciles de indexar y consultar en SQL estándar.

### 2. Diccionario de Claves de Permisos alineado a `navigation.config.ts`
**Decisión:** Definir un enum/constante estandarizado en Backend y Frontend mapeado a los submenús:
- `SALES_POS` (`/pos`)
- `SALES_HISTORY` (`/sales`)
- `SALES_CASH_HISTORY` (`/cash/history`)
- `FINANCES_SUMMARY` (`/finances/summary`)
- `FINANCES_TRANSACTIONS` (`/finances/transactions`)
- `FINANCES_ACCOUNTS` (`/finances/accounts`)
- `FINANCES_CATEGORIES` (`/finances/categories`)
- `CATALOG_PRODUCTS` (`/products`)
- `CATALOG_CATEGORIES` (`/categories`)
- `CATALOG_TAGS` (`/tags`)
- `PURCHASES_SUPPLIERS` (`/suppliers`)
- `SECURITY_USERS` (`/users`)

**Rationale:** Desacopla la lógica de permisos de cambios cosméticos de URLs, permitiendo asociar cada ruta a un `permission_key` canónico.

### 3. Inmutabilidad de `username` en DTOs y Servicios
**Decisión:**
- `CreateUserRequest`: incluye `username`, `fullName`, `password`, `permissions`.
- `UpdateUserRequest`: incluye ÚNICAMENTE `fullName` y `permissions`.
- `UserService.updateUser(id, request, currentUsername)` busca la entidad por ID y actualiza solo `fullName`, `permissions`, `updatedAt = Instant.now()`, `updatedBy = currentUsername`. No existe setter ni parámetro para `username` en la actualización.
**Rationale:** Imposibilita a nivel de contrato HTTP y de lógica JPA que un atacante o llamada manipulada altere el `username`.

### 4. Detección Ergonómica de Cambios en Frontend
**Decisión:** Replicar exactamente la arquitectura de señales y validación de `ProductsComponent`:
- Señales: `originalFormValues = signal<Record<string, any> | null>(null)`, `formVersion = signal<number>(0)`.
- Comparación de arrays de permisos ordenados para detectar cambios reales en la selección.
- Método `isFieldModified(fieldName: string): boolean`.
- Clases CSS: `.form-control.is-modified`, `.custom-select-trigger.is-modified`, `.field-modified-badge`.
- Señal computada `hasFormChanges`: si no hay cambios, botón Guardar deshabilitado.
- Reversión: restaurar los valores iniciales desactiva `.is-modified` y deshabilita Guardar.

### 5. Autorización en Backend e Invalidación Inmediata
**Decisión:**
- En `JwtAuthenticationFilter`, dado que ya se consulta `userRepository.findByUsername(username)`, se verificará que `user.isActive()` sea `true` y `user.getDeletedAt() == null`.
- Se cargarán en el `SecurityContext` las autoridades del usuario como `PERMISSION_<KEY>` obtenidas de `user_permissions`.
- Los controladores (`UserController`, etc.) utilizarán comprobaciones de seguridad (`@PreAuthorize("hasAuthority('PERMISSION_SECURITY_USERS')")` o verificación de servicio).
- Al dar de baja a un usuario (`user.setActive(false)`, `user.setDeletedAt(Instant.now())`), cualquier llamada posterior con su JWT fallará con 401 en el filtro sin necesidad de gestionar blacklist externa en Redis.

### 6. Control de Acceso y Redirección en Frontend
**Decisión:**
- `AuthService` guarda los permisos del usuario en `currentUser` y emite una señal reactiva `permissions()`.
- `NavbarComponent` computa `visibleNavigation = computed(() => ...)` filtrando los `children` por permisos autorizados y descartando grupos cuyos `children` queden vacíos.
- `permissionGuard`: verifica si la ruta destino tiene un `data: { permission: '...' }` asignado. Si el usuario no tiene el permiso, busca la primera ruta autorizada en la jerarquía estándar (`/pos`, `/products`, etc.) y redirige. Si el usuario no tiene ningún permiso, redirige a una vista informativa `/unauthorized`.

### 7. Protección contra Auto-eliminación y Pérdida de Administración
**Decisión:**
- En `UserService.deleteUser(id, currentUsername)`:
  - Si `user.getUsername().equals(currentUsername)`, arrojar `BusinessException("No puede eliminarse a sí mismo.")`.
  - Si el usuario a eliminar posee el permiso `SECURITY_USERS` o rol `ADMIN`, verificar si es el único usuario activo en la base de datos con dicho permiso:
    `long activeAdmins = userRepository.countActiveUsersWithPermission("SECURITY_USERS");`
    Si `activeAdmins <= 1`, arrojar `BusinessException("No se puede eliminar al último usuario con permisos de administración.")`.
### 8. Acciones Estándar de Tabla y Reutilización de `AuditModalComponent`
**Decisión:**
- Cada fila de usuario en la tabla expondrá exclusivamente 3 botones de acción con los mismos estilos (`.btn-icon`), iconos y tooltips de `ProductsComponent`:
  1. `Ver auditoría` (`.btn-audit`, icono de reloj): abre `AuditModalComponent` con título "Auditoría de Usuario", subtítulo `${user.fullName} (${user.username})`, y los 6 campos mapeados (`createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`).
  2. `Editar` (`.btn-edit`, icono de lápiz): abre el formulario de edición con detección de cambios.
  3. `Eliminar` (`.btn-delete`, icono de papelera): abre el modal de confirmación de baja lógica.
- Se descarta cualquier modal o pantalla de detalle adicional para evitar duplicidad de componentes y mantener total consistencia con Catálogo → Productos.

## Risks / Trade-offs

- **[Riesgo: Sesiones activas con permisos antiguos tras edición]** → *Mitigación:* Los endpoints del backend evalúan los permisos en tiempo real contra la base de datos o en el próximo login. En el frontend, la edición del usuario en sesión actualiza su perfil local o instruye refrescar.
- **[Riesgo: Usuarios existentes sin permisos tras migración]** → *Mitigación:* La migración V9 inserta de forma automática e idempotente todas las filas correspondientes en `user_permissions` para todos los usuarios existentes al momento de correr Flyway.
- **[Riesgo: Bucles de redirección en Angular Guards]** → *Mitigación:* `permissionGuard` evalúa si la ruta permitida es idéntica a la ruta actual y si el usuario no tiene ningún permiso redirige a una ruta estática desprotegida (`/unauthorized`) fuera del grupo principal.

## Migration Plan

1. Crear y ejecutar migración `database/migrations/V9__create_user_management_and_permissions_tables.sql`.
2. Actualizar backend (entidades, repositorios, servicios, DTOs, seguridad y tests).
3. Actualizar frontend (servicios, guards, navbar, componentes de usuarios y tests).
4. Validar compilación, tests de integración y arranque sin regresiones.
