# Supuestos Técnicos - REQ-0006: Proveedores y Componentes Reutilizables

## Supuestos Establecidos

1. **Persistencia y Unicidad en Base de Datos (PostgreSQL):**
   - La tabla se llamará `suppliers`.
   - Se utilizarán índices únicos parciales en PostgreSQL para garantizar la unicidad a nivel de motor de base de datos sin colisionar con registros eliminados:
     ```sql
     CREATE UNIQUE INDEX uk_suppliers_active_name ON suppliers (LOWER(name)) WHERE status != 'ELIMINADO';
     CREATE UNIQUE INDEX uk_suppliers_active_code ON suppliers (LOWER(code)) WHERE status != 'ELIMINADO';
     ```
   - Además de los índices, la capa de servicio de Spring Boot (`SupplierService`) realizará la validación programática previa retornando excepciones de negocio tipadas (`DuplicateResourceException`) que mapean a HTTP 409 Conflict con mensajes específicos ("Ya existe un proveedor activo o inactivo con este nombre" o "Ya existe un proveedor activo o inactivo con este código").

2. **Extracción de Usuario de Auditoría en Backend:**
   - La aplicación ya cuenta con `SecurityContextHolder.getContext().getAuthentication()` autenticado con el token JWT.
   - Si la autenticación es un `UsernamePasswordAuthenticationToken` cuyo `principal` o `name` es el username del usuario (`admin`, `seller`), se tomará directamente para `createdBy`, `updatedBy` y `deletedBy`. En caso de ejecución sin contexto (ej. tests sin mock), se utilizará fallback seguro `"system"`.

3. **Arquitectura de Componentes Reutilizables en Frontend:**
   - Se ubicarán en `src/app/shared/components/`:
     - `confirm-modal/`: `ConfirmModalComponent` con inputs (`title`, `message`, `confirmText`, `cancelText`, `dangerMode`) y outputs (`confirm`, `cancel`).
     - `audit-modal/`: `AuditModalComponent` con inputs (`title`, `auditData`) y output (`close`).
   - Se creará una interfaz estándar de datos de auditoría `AuditData`:
     ```typescript
     export interface AuditData {
       createdBy?: string | null;
       createdAt?: string | null;
       updatedBy?: string | null;
       updatedAt?: string | null;
       deletedBy?: string | null;
       deletedAt?: string | null;
     }
     ```
   - Solo se mostrarán en pantalla aquellas propiedades de `AuditData` que tengan un valor definido y no vacío.

4. **Estilos Globales de Formularios y Tablas:**
   - Se centralizarán en `src/styles.css` clases utilitarias consistentes con la estética *Organic Glassmorphism* de Akhana:
     - `.form-group`, `.form-label`, `.form-input`, `.form-textarea`, `.form-select`, `.form-error`
     - `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.btn-icon`
     - `.table-container`, `.data-table`, `.table-actions`, `.status-pill`
   - Esto garantiza que módulos futuros no requieran reescribir CSS para formularios y tablas.

5. **Navegación:**
   - Se añadirá el grupo `purchases` en `navigation.config.ts`:
     ```typescript
     {
       id: 'purchases',
       label: 'Compras',
       children: [
         { label: 'Proveedores', route: '/suppliers' }
       ]
     }
     ```
   - Se ubicará lógicamente entre `Ventas` y `Catálogo` o tras `Ventas`.
