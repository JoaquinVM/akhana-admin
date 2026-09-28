# Plan de Desarrollo — REQ-0009: Gestión de Etiquetas

## Fase 1: Base de Datos y Backend

- [x] **TASK-01: Migración V4 de Base de Datos**
  - Crear `database/migrations/V4__create_tags_table.sql`.
  - Aplicar script en base de datos PostgreSQL (`akhana-postgres`).
  - Actualizar `database/schemas/schema.sql`.

- [x] **TASK-02: Modelo y Repositorio JPA**
  - Crear enum `TagStatus` (`ACTIVO`, `ELIMINADO`).
  - Crear entidad `Tag` con auditoría inmutable.
  - Crear `TagRepository` con consultas de unicidad (`existsByNameIgnoreCaseAndStatusNot`) y búsqueda por estado.

- [x] **TASK-03: DTOs, Servicio y Controlador REST**
  - Crear `TagRequest` y `TagResponse`.
  - Crear `TagService` e implementación `TagServiceImpl` con unicidad estricta y soft-delete.
  - Crear `TagController` (`/api/tags`) con endpoints REST GET, GET /{id}, POST, PUT /{id}, DELETE /{id}.

- [x] **TASK-04: Pruebas Backend**
  - Crear `TagServiceTest` y `TagControllerTest`.
  - Ejecutar `./gradlew test` (validar 100% de éxito - 74/74 pasadas).

---

## Fase 2: Frontend y Componentes Reutilizables

- [x] **TASK-05: Capa Core de Etiquetas**
  - Crear `src/app/core/tag/tag.service.ts` y modelos `tag.models.ts`.
  - Crear pruebas unitarias `tag.service.spec.ts`.

- [x] **TASK-06: Vista `TagsComponent`**
  - Crear `src/app/pages/tags/tags.component.ts`, `.html`, `.css`.
  - Reutilizar `ColorPickerComponent`, `ModalComponent`, `ConfirmModalComponent`, `AuditModalComponent`.
  - Implementar tabla A-Z, buscador en tiempo real, filtro Activos/Eliminados, dirty-checking, botón guardar condicional e indicadores visuales de modificación.
  - Conectar ruta `/tags` en `app.routes.ts`.

- [x] **TASK-07: Pruebas Frontend y Verificación Final**
  - Crear `tags.component.spec.ts` cubriendo todos los criterios de aceptación.
  - Ejecutar `npm test -- --watch=false` (112/112 pasadas) y `npm run build` (0 errores).

---

## Fase 3: Documentación y Cierre

- [x] **TASK-08: Registro y Entrega HITL 3**
  - Registrar implementación en `implementation.md` y `conversation.md`.
  - Presentar resultados ejecutivos al desarrollador.
