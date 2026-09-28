# Supuestos Técnicos y de Negocio — REQ-0009: Gestión de Etiquetas

## 1. Supuestos de Base de Datos
- La tabla se llamará `tags` y seguirá el mismo estándar de auditoría de `categories` y `suppliers`.
- El índice único parcial `uq_tags_name_active` (`LOWER(name) WHERE status != 'ELIMINADO'`) garantiza que las etiquetas eliminadas lógicamente no restrinjan la creación de nuevas etiquetas con el mismo nombre.
- A diferencia de `categories`, la tabla `tags` no cuenta con columna `description`.

## 2. Supuestos de Backend
- El controlador responderá en `/api/tags`.
- Los códigos de respuesta REST estándar serán: `200 OK` (listados, getById, update), `201 Created` (creación), `204 No Content` (eliminación lógica), `400 Bad Request` (validación DTO), `404 Not Found` (no existe ID), `409 Conflict` (duplicado en nombre).
- La identidad del usuario autenticado se extraerá de `Authentication` para registrar `created_by`, `updated_by` y `deleted_by`.

## 3. Supuestos de Frontend
- `TagsComponent` residirá en `src/app/pages/tags/tags.component.ts`.
- Se reutilizarán directamente sin duplicar:
  - `ColorPickerComponent` (`shared/components/color-picker/`)
  - `ModalComponent` (`shared/components/modal/`)
  - `ConfirmModalComponent` (`shared/components/confirm-modal/`)
  - `AuditModalComponent` (`shared/components/audit-modal/`)
  - Clases canónicas de `styles.css`: `.table`, `.filter-pill-group`, `.badge-active`, `.badge-deleted`, `.form-group`, `.field-modified-badge`, `.btn-icon`, etc.
- El ordenamiento inicial obligatorio por Nombre ascendente A-Z se aplicará tanto en backend (`ORDER BY name ASC`) como en frontend computado para ordenación local inmediata.
- En la tabla de etiquetas, las columnas serán: Color, Nombre, Estado y Acciones (sin columna Descripción).
