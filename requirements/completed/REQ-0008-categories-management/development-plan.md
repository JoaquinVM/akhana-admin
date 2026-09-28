# Plan de Desarrollo - REQ-0008: Gestión de Categorías

## Fase 1: Base de Datos y Backend

- [x] **TASK-01: Migración V3 de Base de Datos**
  - Crear `database/migrations/V3__create_categories_table.sql`.
  - Aplicar script en base de datos PostgreSQL (`akhana-postgres`).
  - Actualizar `database/schemas/schema.sql`.

- [x] **TASK-02: Modelo y Repositorio JPA**
  - Crear enum `CategoryStatus` (`ACTIVO`, `ELIMINADO`).
  - Crear entidad `Category` con auditoría inmutable.
  - Crear `CategoryRepository` con consultas de unicidad (`existsByNameIgnoreCaseAndStatusNot`) y búsqueda por estado.

- [x] **TASK-03: DTOs, Servicio y Controlador REST**
  - Crear `CategoryRequest` y `CategoryResponse`.
  - Crear `CategoryService` e implementación `CategoryServiceImpl` con unicidad estricta y soft-delete.
  - Crear `CategoryController` (`/api/categories`) con endpoints REST GET, GET /{id}, POST, PUT /{id}, DELETE /{id}.

- [x] **TASK-04: Pruebas Backend**
  - Crear `CategoryServiceTest` y `CategoryControllerTest`.
  - Ejecutar `./gradlew test` (validar 100% de éxito - 61/61 pasadas).

---

## Fase 2: Componentes Reutilizables y Frontend

- [x] **TASK-05: Componente Reutilizable `ColorPickerComponent`**
  - Crear `src/app/shared/components/color-picker/color-picker.component.ts`, `.html`, `.css`.
  - Paleta de 18 colores armoniosos, selección visual, soporte de ControlValueAccessor para Reactive Forms y accesibilidad.
  - Pruebas unitarias en `color-picker.component.spec.ts`.

- [x] **TASK-06: Servicio Frontend `CategoryService`**
  - Crear `src/app/core/category/category.service.ts` y modelos `category.models.ts`.
  - Pruebas unitarias en `category.service.spec.ts`.

- [x] **TASK-07: Vista `CategoriesComponent`**
  - Crear `src/app/pages/categories/categories.component.ts`, `.html`, `.css`.
  - Reutilizar `ModalComponent`, `ConfirmModalComponent`, `AuditModalComponent`, `ColorPickerComponent`.
  - Implementar tabla A-Z, buscador en tiempo real, filtro Activos/Eliminados, dirty-checking, botón guardar condicional e indicadores visuales de modificación.
  - Conectar ruta `/categories` en `app.routes.ts`.

- [x] **TASK-08: Pruebas Frontend y Verificación Final**
  - Crear `categories.component.spec.ts` cubriendo todos los criterios de aceptación.
  - Ejecutar `npm test -- --watch=false` (92/92 pruebas en 16 suites pasadas) y `npm run build` (exitoso).

---

## Fase 3: Documentación y Cierre

- [x] **TASK-09: Registro y Entrega HITL 3**
  - Registrar implementación en `implementation.md` y `conversation.md`.
  - Presentar resultados ejecutivos al desarrollador.
