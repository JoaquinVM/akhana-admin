# Estrategia de Pruebas — REQ-0010: Gestión de Productos

## 1. Pruebas Unitarias y de Integración Backend (JUnit 5 + MockMvc)

### `ProductServiceTest`
1. `getAllProducts_DefaultFilter_ExcludesDeleted`: Retorna productos activos e inactivos ordenados por nombre A-Z.
2. `getAllProducts_ByStatus_FiltersCorrectly`: Filtra por estado específico (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
3. `getAllProducts_Search_MatchesCodeOrName`: Coincidencia parcial insensible a mayúsculas en código y nombre.
4. `createProduct_Success_CalculatesProfit`: Crea producto calculando utilidad fija y porcentual correctamente.
5. `createProduct_DuplicateCode_ThrowsDuplicateResourceException`: Lanza HTTP 409 si el código ya existe en un producto no eliminado.
6. `createProduct_DuplicateName_ThrowsDuplicateResourceException`: Lanza HTTP 409 si el nombre ya existe en un producto no eliminado.
7. `createProduct_DeletedCodeOrName_AllowsReuse`: Permite crear producto reutilizando código/nombre de un producto eliminado.
8. `updateProduct_Success_RecalculatesProfit`: Actualiza producto y recalcula utilidades.
9. `updateProduct_DuplicateCodeAnotherProduct_Throws409`: Lanza 409 si colisiona con otro producto distinto al actual.
10. `updateProduct_SameCodeAndName_Success`: Permite guardar si mantiene su propio código y nombre.
11. `changeStatus_ToggleActiveInactive_Success`: Conmuta entre `ACTIVO` e `INACTIVO`.
12. `deleteProduct_LogicalDeletion_SetsDeletedAtAndBy`: Soft delete establece estado `ELIMINADO` y auditoría de eliminación.

### `ProductControllerTest`
1. `GET /api/products`: 200 OK con lista de productos.
2. `POST /api/products`: 201 Created con producto creado.
3. `POST /api/products` (Duplicado): 409 Conflict con mensaje descriptivo.
4. `PUT /api/products/{id}`: 200 OK al actualizar.
5. `PATCH /api/products/{id}/status`: 200 OK al cambiar estado.
6. `DELETE /api/products/{id}`: 204 No Content.

---

## 2. Pruebas Unitarias Frontend (Vitest + Angular TestBed)

### `ProductServiceTest`
1. `getProducts`: Realiza GET a `/api/products` con parámetros de búsqueda y filtro.
2. `createProduct`: Realiza POST con payload de creación.
3. `updateProduct`: Realiza PUT con payload de actualización.
4. `changeProductStatus`: Realiza PATCH a `/api/products/{id}/status`.
5. `deleteProduct`: Realiza DELETE a `/api/products/{id}`.

### `ProductsComponentTest`
1. Renderiza encabezado, buscador, botones de filtro y tabla de productos.
2. Ordena inicialmente por Nombre A-Z.
3. Filtro reactivo segmentado (`Activos/Inactivos`, `Activos`, `Inactivos`, `Eliminados`).
4. Búsqueda en tiempo real por código y nombre.
5. Cálculo reactivo de utilidades en formulario: compra 40 y venta 50 calcula 10 fija y 25% porcentual.
6. Creación: Botón Guardar habilitado inicialmente; valida al pulsar y marca errores si está incompleto.
7. Edición: Carga valores, Guardar inicia deshabilitado, se habilita al editar precio y muestra badge `.field-modified-badge`.
8. Conmutación de estado: Abre modal de confirmación antes de activar/desactivar.
9. Eliminación: Abre modal de confirmación antes de eliminar lógicamente.
10. Auditoría: Abre modal de auditoría con `closeOnBackdrop="true"` e icono de reloj.
