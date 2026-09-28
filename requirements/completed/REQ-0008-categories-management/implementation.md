# Documento de Implementación — REQ-0008: Gestión de Categorías

## 1. Resumen Ejecutivo
Se implementó de extremo a extremo el módulo de **Gestión de Categorías** (`/categories`) para el sistema Akhana Admin, cumpliendo con los estándares de diseño Zen Minimalist / Organic Glassmorphism y la reutilización completa de componentes arquitectónicos (Modales, Tablas, Auditoría, Badges y Alertas).

---

## 2. Componentes y Artefactos Creados y Modificados

### 2.1 Base de Datos (PostgreSQL 17)
- [V3__create_categories_table.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/migrations/V3__create_categories_table.sql): Creación de tabla `categories` con columnas de auditoría completas (`created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_by`, `deleted_at`).
- **Índice Único Parcial:** `uq_categories_name_active ON categories (LOWER(name)) WHERE status != 'ELIMINADO'`. Garantiza unicidad case-insensitive exclusivamente entre registros no eliminados, permitiendo reutilizar nombres de categorías que hayan sido eliminadas lógicamente.
- [schema.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql): Actualización del esquema canónico del workspace.

### 2.2 Backend (Spring Boot 3.4 / Java 21)
- [CategoryStatus.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/CategoryStatus.java): Enumeración con estados estrictos `ACTIVO` y `ELIMINADO` (sin estado `INACTIVO`).
- [Category.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/Category.java): Entidad JPA con mapeo a tabla `categories`.
- [CategoryRepository.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/repository/CategoryRepository.java): Métodos de persistencia Spring Data JPA con consultas JPQL para búsquedas insensibles a mayúsculas y filtros por estado.
- [CategoryRequest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/CategoryRequest.java): DTO de entrada con validaciones Jakarta (`@NotBlank`, `@Size`).
- [CategoryResponse.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/CategoryResponse.java): DTO de salida con mapeo de campos de auditoría.
- [CategoryService.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/CategoryService.java) e [CategoryServiceImpl.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/impl/CategoryServiceImpl.java): Lógica de negocio con validación exclusiva en backend de unicidad (`DuplicateResourceException` ➔ 409 Conflict), eliminación lógica y trazabilidad de auditoría.
- [CategoryController.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/controller/CategoryController.java): Endpoints REST `/api/categories` con inyección de autenticación de usuario.
- **Suites de Pruebas Backend:**
  - [CategoryServiceTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/test/java/com/akhana/akhana_admin/service/CategoryServiceTest.java): Cobertura unitaria de CRUD, unicidad de nombre con exclusión de eliminados, eliminación lógica y excepciones.
  - [CategoryControllerTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/test/java/com/akhana/akhana_admin/controller/CategoryControllerTest.java): Cobertura de endpoints REST (200, 201, 400, 404, 409, 204 y 401 sin token).

### 2.3 Frontend (Angular 21 Standalone & Signals)
- **Componente Selector de Color Reutilizable:**
  - [ColorPickerComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/color-picker/color-picker.component.ts) ([HTML](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/color-picker/color-picker.component.html) / [CSS](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/color-picker/color-picker.component.css) / [Spec](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/color-picker/color-picker.component.spec.ts)):
    - Implementa `ControlValueAccessor` para integración nativa con `ReactiveFormsModule` (`formControlName="color"`).
    - Paleta curada de 18 colores Zen/Orgánicos con botones redondeados, hover dinámico (`scale(1.18)`), anillo exterior activo e icono check blanco.
    - Ficha de previsualización activa que refleja el color y código en tiempo real.
- **Capa Core de Categorías:**
  - [category.models.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/category/models/category.models.ts): Interfaces `Category`, `CategoryRequest` y tipo `CategoryStatus`.
  - [category.service.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/category/category.service.ts): Servicio cliente HTTP para `/api/categories`.
  - [category.service.spec.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/category/category.service.spec.ts): Pruebas unitarias de consumo HTTP con `provideHttpClientTesting()`.
- **Página de Categorías:**
  - [CategoriesComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/categories/categories.component.ts) ([HTML](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/categories/categories.component.html) / [CSS](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/categories/categories.component.css) / [Spec](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/categories/categories.component.spec.ts)):
    - Filtros por estado: `Activos` (por defecto) y `Eliminados`.
    - Búsqueda en tiempo real por nombre.
    - Orden inicial obligatorio por Nombre A-Z.
    - Columna Color con píldora estética (`.category-color-pill`).
    - Acciones condicionales por estado: Activos (Auditoría, Editar, Eliminar) vs Eliminados (únicamente Auditoría).
    - Modal de creación: Botón guardar habilitado, marca campos al enviar.
    - Modal de edición: Botón guardar deshabilitado si no hay cambios, indicador visual de campos modificados (`.is-modified`, `.field-modified-badge`).
    - Modal de descarte de cambios (`ConfirmModalComponent`) al intentar cerrar con cambios sin guardar (`closeOnBackdrop="false"` en modal de formulario).
    - Modal de confirmación de eliminación lógica (`ConfirmModalComponent`).
    - Modal de auditoría reutilizable (`AuditModalComponent`, `closeOnBackdrop="true"`).
- **Rutas:**
  - [app.routes.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/app.routes.ts): Enrutamiento de `/categories` hacia `CategoriesComponent`.

---

## 3. Verificación de Calidad y Resultados de Pruebas

| Módulo | Pruebas Ejecutadas | Resultado |
| :--- | :--- | :--- |
| **Backend (Gradle / JUnit 5)** | 61 pruebas unitarias y de integración | **100% Pasadas (BUILD SUCCESSFUL)** |
| **Frontend (Vitest / Angular)** | 92 pruebas en 16 suites | **100% Pasadas (16/16 suites)** |
| **Frontend Build (AOT/Prod)** | `npm run build` | **Exitoso (0 errores, 418.75 kB bundle)** |
| **Integración API en Vivo** | `curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/categories` | **200 OK (datos persistidos en PostgreSQL)** |
