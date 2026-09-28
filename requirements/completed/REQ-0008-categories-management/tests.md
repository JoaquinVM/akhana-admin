# Estrategia de Pruebas - REQ-0008: Gestión de Categorías

## 🧪 Pruebas Unitarias Backend (JUnit 5 + Mockito)

1. **TEST-CAT-SVC-01: Listado de categorías**
   - Validación de orden alfabético A-Z.
   - Filtrado por estado (`ACTIVO` por defecto, `ELIMINADO` cuando se especifica).
   - Búsqueda por término insensible a mayúsculas.
2. **TEST-CAT-SVC-02: Creación de categoría**
   - Creación exitosa con estado `ACTIVO` y auditoría.
   - Rechazo con `DuplicateResourceException` (HTTP 409) si ya existe categoría activa con el mismo nombre.
   - Permitir reutilizar nombre si la categoría anterior fue marcada como `ELIMINADO`.
3. **TEST-CAT-SVC-03: Edición de categoría**
   - Actualización exitosa excluyendo el propio ID en la validación de unicidad.
   - Rechazo de edición si la categoría se encuentra en estado `ELIMINADO`.
4. **TEST-CAT-SVC-04: Eliminación lógica**
   - Cambio de estado a `ELIMINADO` con registro inmutable de `deletedBy` y `deletedAt`.
5. **TEST-CAT-CTRL-01: Endpoints REST**
   - Verificación de status codes: 200, 201, 204, 404, 409, 400.

---

## 🧪 Pruebas Unitarias Frontend (Angular 21 + Vitest)

1. **TEST-COLOR-PICKER-01:** Renderizado de paleta, selección de color al hacer clic, emisión de evento y foco visual.
2. **TEST-CAT-SVC-01:** Peticiones HTTP en `CategoryService` con y sin parámetros de búsqueda y estado.
3. **TEST-CAT-COMP-01:** Carga de categorías activas ordenadas A-Z.
4. **TEST-CAT-COMP-02:** Filtro por estado (`Activos` vs `Eliminados`).
5. **TEST-CAT-COMP-03:** Registros `ELIMINADO` solo muestran opción Auditoría.
6. **TEST-CAT-COMP-04:** Búsqueda en tiempo real por nombre.
7. **TEST-CAT-COMP-05:** Modal de creación con botón Guardar siempre activo y validación de campos obligatorios al pulsar.
8. **TEST-CAT-COMP-06:** Manejo de error 409 devuelto por backend en nombre duplicado.
9. **TEST-CAT-COMP-07:** Modal de edición con botón Guardar inicialmente deshabilitado, habilitándose al modificar y deshabilitándose al revertir.
10. **TEST-CAT-COMP-08:** Indicadores visuales de campos modificados (`.is-modified`, `.field-modified-badge`).
11. **TEST-CAT-COMP-09:** Modal de formulario no cierra al hacer clic fuera (`closeOnBackdrop=false`).
12. **TEST-CAT-COMP-10:** Confirmación al descartar cambios con `ConfirmModalComponent`.
13. **TEST-CAT-COMP-11:** Modal de auditoría cierra al hacer clic fuera (`closeOnBackdrop=true`).
