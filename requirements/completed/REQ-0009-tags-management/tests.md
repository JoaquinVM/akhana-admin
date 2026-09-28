# Estrategia de Pruebas — REQ-0009: Gestión de Etiquetas

## 1. Pruebas Unitarias de Backend (`src/test/java/com/akhana/akhana_admin/`)

### `TagServiceTest` (Mockito + JUnit 5):
1. `createTag_Success`: Crea etiqueta con estado `ACTIVO`, color y auditoría inicial.
2. `createTag_DuplicateNameActive_ThrowsDuplicateResourceException`: Falla con 409 si el nombre coincide con una etiqueta `ACTIVO`.
3. `createTag_DuplicateNameDeleted_Success`: Permite crear etiqueta si la coincidencia es con una etiqueta `ELIMINADO`.
4. `getAllTags_FilterActivo`: Retorna lista ordenada de etiquetas activas.
5. `getAllTags_FilterEliminado`: Retorna lista de etiquetas eliminadas.
6. `getAllTags_SearchByName`: Filtra por coincidencia parcial insensible a mayúsculas.
7. `getTagById_Success` y `getTagById_NotFound`: Retorna entidad o lanza 404.
8. `updateTag_Success`: Actualiza nombre y color registrando `updated_by` y `updated_at`.
9. `updateTag_DuplicateName_ThrowsException`: Falla si el nuevo nombre colisiona con otra activa.
10. `updateTag_SameName_Success`: Permite actualizar sin cambiar el nombre sin arrojar error.
11. `deleteTag_Success`: Marca estado `ELIMINADO`, `deleted_by` y `deleted_at`.
12. `deleteTag_AlreadyDeleted_ThrowsException`: Lanza excepción si ya está eliminada.

### `TagControllerTest` (WebMvcTest + MockMvc):
1. `GET /api/tags`: Retorna 200 OK y lista JSON.
2. `GET /api/tags/{id}`: Retorna 200 OK cuando existe, 404 cuando no.
3. `POST /api/tags`: Retorna 201 Created con cuerpo válido.
4. `POST /api/tags`: Retorna 400 Bad Request cuando falta nombre o color.
5. `POST /api/tags`: Retorna 409 Conflict ante duplicado.
6. `PUT /api/tags/{id}`: Retorna 200 OK con actualización válida.
7. `DELETE /api/tags/{id}`: Retorna 204 No Content al eliminar lógicamente.
8. Petición sin token: Retorna 401 Unauthorized.

---

## 2. Pruebas Unitarias de Frontend (`src/app/`)

### `TagService` (`tag.service.spec.ts`):
1. GET a `/api/tags` con y sin parámetros de búsqueda y estado.
2. GET a `/api/tags/{id}`.
3. POST a `/api/tags`.
4. PUT a `/api/tags/{id}`.
5. DELETE a `/api/tags/{id}`.

### `TagsComponent` (`tags.component.spec.ts`):
1. Inicialización y carga de etiquetas ordenadas A-Z.
2. Búsqueda en tiempo real por nombre case-insensitive.
3. Cambio de filtro segmentado entre `Activos` y `Eliminados`.
4. Apertura del modal de creación con formulario limpio y botón Guardar habilitado.
5. Apertura del modal de edición con datos precargados y botón Guardar deshabilitado.
6. Detección reactiva de cambios en el formulario (dirty-check) y etiqueta `.field-modified-badge`.
7. Confirmación de descarte de cambios al intentar cerrar modal sucio (`closeOnBackdrop="false"`).
8. Manejo de error 409 Conflict devuelto por el backend.
9. Eliminación lógica con confirmación y actualización reactiva de la lista.
10. Apertura del modal de auditoría con el icono de reloj unificado.
