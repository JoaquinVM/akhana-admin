# Documento de Implementación — REQ-0009: Gestión de Etiquetas

## 1. Resumen Ejecutivo
Se implementó de extremo a extremo el módulo de **Gestión de Etiquetas** (`/tags`) para el sistema Akhana Admin, garantizando paridad total visual y de comportamiento con **Categorías**. Se reutilizaron todos los componentes, estilos y patrones de interacción existentes (`ColorPickerComponent`, `ModalComponent`, `ConfirmModalComponent`, `AuditModalComponent` y estilos canónicos de tablas y modales).

---

## 2. Componentes y Artefactos Creados y Modificados

### 2.1 Base de Datos (PostgreSQL 17)
- [V4__create_tags_table.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/migrations/V4__create_tags_table.sql): Creación de tabla `tags` con columnas de auditoría completas (`created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_by`, `deleted_at`).
- **Índice Único Parcial:** `uq_tags_name_active ON tags (LOWER(name)) WHERE status != 'ELIMINADO'`. Garantiza unicidad case-insensitive exclusivamente entre registros no eliminados, permitiendo reutilizar nombres de etiquetas eliminadas lógicamente.
- [schema.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql): Actualización del esquema canónico del workspace.

### 2.2 Backend (Spring Boot 3.4 / Java 21)
- [TagStatus.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/TagStatus.java): Enumeración con estados estrictos `ACTIVO` y `ELIMINADO` (sin estado `INACTIVO`).
- [Tag.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/Tag.java): Entidad JPA con mapeo a tabla `tags`.
- [TagRepository.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/repository/TagRepository.java): Métodos de persistencia Spring Data JPA con consultas JPQL para búsquedas insensibles a mayúsculas y filtros por estado.
- [TagRequest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/TagRequest.java): DTO de entrada (`name`, `color`) con validaciones Jakarta (`@NotBlank`, `@Size`).
- [TagResponse.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/TagResponse.java): DTO de salida con mapeo de campos de auditoría.
- [TagService.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/TagService.java) y [TagServiceImpl.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/impl/TagServiceImpl.java): Lógica de negocio con validación exclusiva en backend de unicidad (`DuplicateResourceException` ➔ 409 Conflict), eliminación lógica y trazabilidad de auditoría.
- [TagController.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/controller/TagController.java): Endpoints REST `/api/tags` con inyección de autenticación de usuario.
- **Suites de Pruebas Backend:**
  - [TagServiceTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/test/java/com/akhana/akhana_admin/service/TagServiceTest.java): Cobertura unitaria de CRUD, unicidad de nombre con exclusión de eliminados, eliminación lógica y excepciones.
  - [TagControllerTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/test/java/com/akhana/akhana_admin/controller/TagControllerTest.java): Cobertura de endpoints REST (200, 201, 400, 404, 409, 204 y 401 sin token).

### 2.3 Frontend (Angular 21 Standalone & Signals)
- **Capa Core de Etiquetas:**
  - [tag.models.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/tag/models/tag.models.ts): Interfaces `Tag`, `TagRequest` y tipo `TagStatus`.
  - [tag.service.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/tag/tag.service.ts): Servicio cliente HTTP para `/api/tags`.
  - [tag.service.spec.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/tag/tag.service.spec.ts): Pruebas unitarias de consumo HTTP con `provideHttpClientTesting()`.
- **Página de Etiquetas:**
  - [TagsComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/tags/tags.component.ts) ([HTML](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/tags/tags.component.html) / [CSS](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/tags/tags.component.css) / [Spec](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/tags/tags.component.spec.ts)):
    - Filtros por estado: `Activos` (por defecto) y `Eliminados`.
    - Búsqueda en tiempo real por nombre.
    - Orden inicial obligatorio por Nombre A-Z.
    - Columna Color con píldora estética (`.category-color-pill`).
    - Acciones condicionales por estado: Activos (Auditoría, Editar, Eliminar) vs Eliminados (únicamente Auditoría).
    - Reutilización de `ColorPickerComponent` en formulario.
    - Modal de creación: Botón guardar habilitado, valida al pulsar.
    - Modal de edición: Botón guardar deshabilitado si no hay cambios, indicador visual de campos modificados (`.field-modified-badge`).
    - Modal de descarte de cambios (`ConfirmModalComponent`) al intentar cerrar con cambios sin guardar (`closeOnBackdrop="false"`).
    - Modal de confirmación de eliminación lógica (`ConfirmModalComponent`).
    - Modal de auditoría reutilizable (`AuditModalComponent`, `closeOnBackdrop="true"`, icono unificado).
- **Rutas:**
  - [app.routes.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/app.routes.ts): Enrutamiento de `/tags` hacia `TagsComponent`.

---

## 3. Verificación de Calidad y Resultados de Pruebas

| Módulo | Pruebas Ejecutadas | Resultado |
| :--- | :--- | :--- |
| **Backend (Gradle / JUnit 5)** | 74 pruebas unitarias e integración | **100% Pasadas (BUILD SUCCESSFUL)** |
| **Frontend (Vitest / Angular)** | 112 pruebas en 18 suites | **100% Pasadas (18/18 suites)** |
| **Frontend Build (AOT/Prod)** | `npm run build` | **Exitoso (0 errores, 438.89 kB bundle)** |
| **Integración API en Vivo** | `curl /api/tags` | **200 OK y 409 Conflict verificados en vivo** |
