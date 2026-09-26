# Registro de Implementación Real - REQ-0006: Proveedores y Componentes Reutilizables

> **Nota:** Este documento registra la trazabilidad del código y artefactos efectivamente implementados en `backend/` y `frontend/`.

---

## 📦 Estado de la Implementación
- **Fase Actual:** `IMPLEMENTATION_COMPLETED / READY_FOR_DELIVERY` (HITL 3)
- **Fecha:** 2026-09-26

---

## 🛠️ Archivos Creados y Modificados

### 1. Base de Datos (`database/`)
- [`database/migrations/V2__create_suppliers_table.sql`](file:///Users/joaquin/Documents/Akhana%20Admin/database/migrations/V2__create_suppliers_table.sql): Script de migración con tabla `suppliers`, índices normales y dos índices únicos parciales (`uq_suppliers_name_active` y `uq_suppliers_code_active` con cláusula `WHERE status != 'ELIMINADO'`). Ejecutada exitosamente en la base de datos PostgreSQL.
- [`database/schemas/schema.sql`](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql): Actualización del esquema canónico incorporando la definición de `suppliers`.

### 2. Backend (`backend/`)
- [`backend/src/main/java/com/akhana/akhana_admin/model/SupplierStatus.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/SupplierStatus.java): Enum con estados `ACTIVO`, `INACTIVO`, `ELIMINADO`.
- [`backend/src/main/java/com/akhana/akhana_admin/model/Supplier.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/Supplier.java): Entidad JPA con soft-delete y trazabilidad inmutable de auditoría (`createdBy`, `createdAt`, `updatedBy`, `updatedAt`, `deletedBy`, `deletedAt`).
- [`backend/src/main/java/com/akhana/akhana_admin/exception/DuplicateResourceException.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/exception/DuplicateResourceException.java): Excepción de negocio para duplicados.
- [`backend/src/main/java/com/akhana/akhana_admin/exception/ResourceNotFoundException.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/exception/ResourceNotFoundException.java): Excepción para recursos no encontrados (HTTP 404).
- [`backend/src/main/java/com/akhana/akhana_admin/exception/GlobalExceptionHandler.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/exception/GlobalExceptionHandler.java): Mapeador global que traduce `DuplicateResourceException` a HTTP 409 Conflict, `ResourceNotFoundException` a HTTP 404, y `IllegalStateException` a HTTP 400.
- [`backend/src/main/java/com/akhana/akhana_admin/dto/ErrorResponse.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/ErrorResponse.java): Métodos de factoría `conflict()` y `notFound()`.
- [`backend/src/main/java/com/akhana/akhana_admin/dto/SupplierRequest.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/SupplierRequest.java): Payload de entrada con validaciones Jakarta (`@NotBlank`, `@Size`).
- [`backend/src/main/java/com/akhana/akhana_admin/dto/SupplierResponse.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/SupplierResponse.java): Payload de salida con datos principales y de auditoría.
- [`backend/src/main/java/com/akhana/akhana_admin/repository/SupplierRepository.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/repository/SupplierRepository.java): Métodos de consulta con exclusión de eliminados, validación de unicidad insensible a mayúsculas y búsqueda parcial.
- [`backend/src/main/java/com/akhana/akhana_admin/service/SupplierService.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/SupplierService.java) e [`impl/SupplierServiceImpl.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/impl/SupplierServiceImpl.java): Lógica de negocio con validación exclusiva de unicidad en backend, auditoría automática y soft delete.
- [`backend/src/main/java/com/akhana/akhana_admin/controller/SupplierController.java`](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/controller/SupplierController.java): Endpoints REST `/api/suppliers` (GET, GET/{id}, POST, PUT/{id}, DELETE/{id}).

### 3. Frontend (`frontend/`)
- [`frontend/src/app/core/navigation/navigation.config.ts`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/navigation/navigation.config.ts): Incorporación del grupo `Compras` con opción `Proveedores` (`/suppliers`).
- [`frontend/src/app/core/supplier/models/supplier.models.ts`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/supplier/models/supplier.models.ts): Interfaces TypeScript `Supplier`, `SupplierRequest`, `SupplierStatus`.
- [`frontend/src/app/core/supplier/supplier.service.ts`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/supplier/supplier.service.ts): Cliente HTTP Angular para las operaciones CRUD con soporte de filtrado opcional por `status` y término de búsqueda.
- [`frontend/src/app/shared/components/modal/`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/modal/): Componente modal genérico y reutilizable (`ModalComponent`) con soporte de tamaños (`sm`, `md`, `lg`, `xl`), proyección semántica de contenido (`[modal-icon]`, cuerpo default, `[modal-footer]`), control por teclado (Escape) y backdrop configurable (`closeOnBackdrop`).
- [`frontend/src/app/shared/components/confirm-modal/`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/confirm-modal/): Componente modal reutilizable de confirmación (`ConfirmModalComponent`) con soporte de variantes (`danger`, `warning`, `primary`), accesibilidad por backdrop/escape y micro-animaciones. Utilizado para confirmación de eliminación lógica y descarte de cambios en formularios.
- [`frontend/src/app/shared/components/audit-modal/`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/audit-modal/): Componente modal reutilizable de auditoría (`AuditModalComponent`) que evalúa y filtra estrictamente cualquier propiedad `null`, `undefined` o vacía. Cierra al hacer clic en el backdrop.
- [`frontend/src/styles.css`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/styles.css): Tokens corporativos de diseño y utilidades centralizadas para formularios (`.form-grid`, `.form-grid-2`, `.form-grid-3`, `.form-group`, `.form-label`, `.form-control`, `.form-control.is-modified`, `.field-modified-badge`, `.invalid-feedback`), tablas (`.table-container`, `.table`, `.table th/td`, `.table-actions`), botones, badges de estado (`.badge-active`, `.badge-inactive`, `.badge-deleted`), y segmented controls de filtrado reutilizables (`.filter-pill-group`, `.filter-pill`).
- [`frontend/src/app/pages/suppliers/`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/suppliers/): Vista completa de gestión de proveedores (`SuppliersComponent`):
  - Formulario en modal con `closeOnBackdrop=false` (solo cierra por X o Cancelar).
  - Dirty-checking reactivo que solicita confirmación al intentar cerrar con cambios sin guardar (en creación o edición).
  - Botón Guardar en creación siempre habilitado, ejecutando validación reactiva al hacer clic.
  - Botón Guardar en edición condicionado a la existencia de cambios respecto a los valores iniciales.
  - Indicadores visuales automáticos de campos modificados (`.is-modified`, `.field-modified-badge`) que desaparecen si se restauran a su valor original.
  - Filtro por estado tipo segmented control ("Activos/Inactivos", "Activos", "Inactivos", "Eliminados") combinado con búsqueda por nombre y código.
  - Restricción de acciones según estado: registros `ELIMINADO` solo muestran opción Auditoría.
- [`frontend/src/app/app.routes.ts`](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/app.routes.ts): Configuración de ruta `/suppliers` bajo `MainLayoutComponent` con `authGuard`.

---

## 🧪 Pruebas Automatizadas

### Backend (`./gradlew test`)
- 100% de tests aprobados (37 tests en suites `SupplierServiceTest`, `SupplierControllerTest`, `AuthServiceTest`, `AuthControllerTest`, `JwtServiceTest`).

### Frontend (`npx ng test --watch=false`)
- 100% de tests aprobados (67 tests en 13 suites: `modal.component.spec.ts`, `suppliers.component.spec.ts`, `supplier.service.spec.ts`, `confirm-modal.component.spec.ts`, `audit-modal.component.spec.ts`, `navbar.component.spec.ts`, etc.).

### Build de Producción
- Compilación limpia con `npm run build` en `frontend/dist/akhana-frontend` (0 errores, 0 advertencias).
