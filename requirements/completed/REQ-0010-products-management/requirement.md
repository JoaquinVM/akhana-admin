# Requerimiento — REQ-0010: Gestión de Productos

## 1. Objetivo
Implementar la gestión integral de **Productos** del sistema Akhana Admin, siguiendo la misma lógica funcional y de diseño establecida en **Proveedores**, **Categorías** y **Etiquetas** para:
- Estados (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
- Creación y edición con modal reutilizable.
- Eliminación lógica y auditoría inmutable.
- Búsqueda en tiempo real (por código o nombre) y filtros segmentados por estado.
- Detección de cambios (dirty-checking) e indicadores visuales de campos modificados (`.field-modified-badge`).
- Paridad y reutilización máxima de componentes y estilos.

---

## 2. Navegación
- Ubicación: Menú **Catálogo** ➔ **Productos**.
- Ruta Angular: `/products`.

---

## 3. Campos del Producto

| Campo | Obligatorio | Tipo / Restricción | Descripción |
| :--- | :---: | :--- | :--- |
| **Código** | Sí | Texto (máx 50) | Identificador único del producto. Unicidad exclusiva en backend (`ACTIVO` e `INACTIVO`). |
| **Nombre** | Sí | Texto (máx 150) | Nombre comercial del producto. Unicidad exclusiva en backend (`ACTIVO` e `INACTIVO`). |
| **Categoría** | Sí | Relación (FK `category_id`) | Categoría existente (no eliminada). Muestra visualmente su color y nombre en selector y tabla. |
| **Proveedor** | Sí | Relación (FK `supplier_id`) | Proveedor existente (no eliminado). |
| **Descripción** | No | Texto (máx 500) | Información adicional u observaciones del producto. |
| **Etiquetas** | No | Relación N:M (`product_tags`) | Múltiples etiquetas seleccionables (no eliminadas). Cada una muestra su color y nombre (pastillas removibles). |
| **Precio de compra** | Sí | Numérico decimal (> 0) | Costo de adquisición del producto. |
| **Precio de venta** | Sí | Numérico decimal (> 0) | Precio final al consumidor. |
| **Utilidad fija** | Automático | Numérico decimal (solo lectura) | Calculada: `Precio de venta - Precio de compra`. |
| **Utilidad porcentual**| Automático | Porcentaje (solo lectura) | Calculada: `((Precio de venta - Precio de compra) / Precio de compra) * 100`. |
| **Estado** | Sí | Enum (`ACTIVO`, `INACTIVO`, `ELIMINADO`) | Por defecto `ACTIVO` en creación. |
| **Auditoría** | Automático | Trazabilidad inmutable | `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted_by`, `deleted_at`. |

---

## 4. Reglas de Negocio Clave

1. **Unicidad de Código y Nombre Exclusiva en Backend:**
   - La unicidad de código y de nombre aplica únicamente entre productos `ACTIVO` e `INACTIVO`.
   - Los productos `ELIMINADO` liberan su código y nombre para ser reutilizados sin conflicto.
   - En edición, el propio producto se auto-excluye de la validación.
   - El frontend **nunca** pre-valida duplicados ni hace peticiones previas; envía los datos directamente y muestra los mensajes del backend (HTTP 409 Conflict).
2. **Cálculo Automático de Utilidades:**
   - Se actualizan en tiempo real en el formulario al modificar cualquiera de los dos precios.
   - Campos de solo lectura, no editables manualmente.
3. **Selector Cromático de Categoría y Selector Múltiple de Etiquetas:**
   - En el selector de categoría y en la tabla se muestra el círculo cromático y el nombre.
   - En las etiquetas se permite seleccionar varias, verlas con sus colores y quitarlas de forma interactiva.
4. **Filtro de Estado Segmentado:**
   - `Activos/Inactivos` (`ACTIVO` + `INACTIVO`, seleccionado por defecto).
   - `Activos` (`ACTIVO`).
   - `Inactivos` (`INACTIVO`).
   - `Eliminados` (`ELIMINADO`).
5. **Comportamiento de Modales y Dirty-Checking:**
   - Modal de formulario con `closeOnBackdrop="false"`.
   - Si existen cambios sin guardar, solicitar confirmación al cerrar (Descartar cambios / Continuar editando).
   - Creación: Botón Guardar siempre habilitado; valida y marca campos al pulsar.
   - Edición: Botón Guardar inicia deshabilitado; se habilita con cambios y vuelve a deshabilitarse si se restauran los valores originales. Indicador visual `.field-modified-badge` en campos modificados.
6. **Acciones según Estado:**
   - `ACTIVO`: Consultar Auditoría, Editar, Desactivar (`INACTIVO`), Eliminar.
   - `INACTIVO`: Consultar Auditoría, Editar, Activar (`ACTIVO`), Eliminar.
   - `ELIMINADO`: Consultar Auditoría únicamente.
