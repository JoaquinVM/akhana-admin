# Diseño UX/UI — REQ-0010: Gestión de Productos

## 1. Lenguaje Visual y Consistencia
El módulo adopta rigurosamente el diseño **Corporate Organic Glassmorphism** de Akhana:
- **Encabezado y Acciones:** Título de módulo "Productos", subtítulo descriptivo, barra de búsqueda reactiva por código/nombre y botón primario `+ Nuevo Producto`.
- **Filtros Segmentados:** Píldoras de estado (`Activos/Inactivos`, `Activos`, `Inactivos`, `Eliminados`).
- **Tabla de Productos:**
  - `Código`: Fuente monoespaciada o estilizada (`.product-code`).
  - `Nombre`: Texto principal destacado y descripción secundaria si existe.
  - `Categoría`: Pastilla visual estilizada (`.category-color-pill`) con el círculo de color y el nombre de la categoría.
  - `Proveedor`: Nombre del proveedor.
  - `Etiquetas`: Lista horizontal de pastillas compactas (`.tag-pill`) que muestran el color y el nombre de cada etiqueta asociada. Si no tiene etiquetas, muestra `—`.
  - `Precio Compra`: Formato monetario (ej. `Bs 40.00`).
  - `Precio Venta`: Formato monetario (ej. `Bs 50.00`).
  - `Utilidad`: Columna dual mostrando la utilidad fija y el porcentaje con badge positivo o neutral (ej. `Bs 10.00 (+25.0%)`).
  - `Estado`: Badges consistentes (`badge-active`, `badge-inactive`, `badge-deleted`).
  - `Acciones`: Botones de icono (Auditoría con reloj, Editar con lápiz, Cambio de estado Activar/Desactivar, y Eliminar con papelera).

---

## 2. Componentes de Interacción en el Formulario

### Selector Cromático de Categoría
- Un desplegable accesible donde cada opción presenta la pastilla con su color correspondiente y el nombre de la categoría.
- Al seleccionarse, el campo cerrado refleja visualmente tanto el color como el texto de la categoría elegida.

### Selector Múltiple de Etiquetas
- Permite seleccionar una o más etiquetas desde una lista de etiquetas activas.
- Las etiquetas seleccionadas se renderizan en una caja contenedora como chips/pills con el color de fondo/borde de la etiqueta, su nombre y un botón `×` para desvincularla fácilmente.
- Las etiquetas eliminadas no aparecen en el catálogo seleccionable.

### Campos de Precio y Cálculo de Utilidades
- Dos inputs numéricos: `Precio de compra` y `Precio de venta` con validación de valores positivos.
- Dos campos de solo lectura para `Utilidad fija` y `Utilidad porcentual`, calculados reactivamente en tiempo real:
  - Fondo sutil diferenciado (`background: rgba(0,0,0,0.03)`).
  - Indicador de estado de solo lectura.
  - Actualización instantánea ante cambios en cualquiera de los dos precios.

---

## 3. Modales y Flujos de Confirmación
- **Modal de Formulario:** Ancho mediano/grande (`max-width: 680px`), `[closeOnBackdrop]="false"`.
- **Confirmación al Descartar:** `ConfirmModalComponent` con "¿Deseas descartar los cambios sin guardar?".
- **Confirmación al Cambiar Estado:** `ConfirmModalComponent` con "¿Estás seguro de que deseas desactivar/activar este producto?".
- **Confirmación al Eliminar:** `ConfirmModalComponent` con "¿Estás seguro de eliminar este producto? La acción realizará una eliminación lógica.".
- **Modal de Auditoría:** `AuditModalComponent` con `[closeOnBackdrop]="true"`, icono de reloj unificado y visualización de fechas y usuarios formateados.
