# Diseño Técnico de Arquitectura — REQ-0009: Gestión de Etiquetas

## 1. Arquitectura de Base de Datos (PostgreSQL 17)

### Migración: `V4__create_tags_table.sql`
```sql
CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    color VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    created_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_by VARCHAR(100),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tags_name_active ON tags (LOWER(name)) WHERE status != 'ELIMINADO';
CREATE INDEX IF NOT EXISTS idx_tags_status ON tags(status);
CREATE INDEX IF NOT EXISTS idx_tags_name ON tags(name);
```

---

## 2. Arquitectura de Backend (Spring Boot 3.4 / Java 21)

### 2.1 Modelo y Repositorio
- `TagStatus.java`: Enum (`ACTIVO`, `ELIMINADO`).
- `Tag.java`: Entidad JPA mapeada a tabla `tags`.
- `TagRepository.java`:
  - `findByStatusOrderByNameAsc(TagStatus status)`
  - `existsByNameIgnoreCaseAndStatusNot(String name, TagStatus status)`
  - `existsByNameIgnoreCaseAndStatusNotAndIdNot(String name, TagStatus status, UUID id)`
  - `@Query` para búsqueda case-insensitive:
    `searchTagsByStatus(@Param("term") String term, @Param("status") TagStatus status)`

### 2.2 DTOs
- `TagRequest(String name, String color, TagStatus status)`
- `TagResponse(UUID id, String name, String color, TagStatus status, String createdBy, Instant createdAt, ...)`

### 2.3 Servicio y Endpoints REST
- `TagService` e `TagServiceImpl`:
  - `getAllTags(String search, String status)`
  - `getTagById(UUID id)`
  - `createTag(TagRequest request, String currentUsername)`
  - `updateTag(UUID id, TagRequest request, String currentUsername)`
  - `deleteTag(UUID id, String currentUsername)`
- `TagController` (`/api/tags`):
  - `GET /api/tags?search=&status=`
  - `GET /api/tags/{id}`
  - `POST /api/tags`
  - `PUT /api/tags/{id}`
  - `DELETE /api/tags/{id}`

---

## 3. Arquitectura de Frontend (Angular 21 Standalone)

### 3.1 Capa Core
- `tag.models.ts`: Tipos `TagStatus`, interfaces `Tag`, `TagRequest`.
- `tag.service.ts`: Cliente HTTP para interactuar con `/api/tags`.

### 3.2 Vista y Reutilización
- `TagsComponent` (`src/app/pages/tags/tags.component.ts`):
  - Reutilización de `ColorPickerComponent` mediante `formControlName="color"`.
  - Reutilización de `ModalComponent`, `ConfirmModalComponent` y `AuditModalComponent`.
  - Signals para gestión reactiva: `tags`, `searchTerm`, `selectedStatusFilter`, `isFormModalOpen`, `isDiscardConfirmOpen`, `isConfirmModalOpen`, `isAuditModalOpen`, `hasFormChanges`.
  - Clases CSS canónicas de `styles.css`: `.table`, `.filter-pill-group`, `.category-color-pill`, `.status-badge`, `.badge-active`, `.badge-deleted`, etc.
