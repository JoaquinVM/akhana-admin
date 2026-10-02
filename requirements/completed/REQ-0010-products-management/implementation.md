# Registro de Implementación — REQ-0010: Gestión de Productos

## 1. Resumen de Implementación
Se ha implementado de extremo a extremo el módulo de **Gestión de Productos** (`/products`) dentro del grupo **Catálogo**, cumpliendo con la paridad visual, técnica y de interacción establecida para Proveedores, Categorías y Etiquetas.

---

## 2. Componentes y Artefactos Construidos

### 2.1 Base de Datos (PostgreSQL 17)
- **Migración V5:** [V5__create_products_table.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/migrations/V5__create_products_table.sql)
  - Tabla `products` con columnas `code`, `name`, `category_id`, `supplier_id`, `description`, `buy_price`, `sell_price`, `fixed_profit`, `percentage_profit`, `status` y auditoría inmutable.
  - Índices únicos parciales `uq_products_code_active` y `uq_products_name_active` (`WHERE status != 'ELIMINADO'`).
  - Tabla intermedia `product_tags` para la relación Many-to-Many con clave compuesta y cascada referencial.
- **Esquema Canónico:** Sincronizado en [schema.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql).

### 2.2 Backend (Spring Boot 3.5 & Java 21)
- **Modelos:**
  - [ProductStatus.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/ProductStatus.java): `ACTIVO`, `INACTIVO`, `ELIMINADO`.
  - [Product.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/model/Product.java): Entidad JPA con `@ManyToOne` (Category, Supplier), `@ManyToMany` (Tag) y auditoría inmutable.
- **Repositorio:**
  - [ProductRepository.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/repository/ProductRepository.java): Búsquedas con `JOIN FETCH`, métodos de unicidad `existsBy...` y consultas JPQL para búsquedas por código o nombre y estado.
- **DTOs:**
  - [ProductRequest.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/ProductRequest.java): Validación con Bean Validation (`@NotBlank`, `@DecimalMin`).
  - [ProductResponse.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/dto/ProductResponse.java): Datos enriquecidos con categoría (nombre y color), proveedor, etiquetas y utilidades calculadas.
- **Servicio:**
  - [ProductService.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/ProductService.java) y [ProductServiceImpl.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/service/impl/ProductServiceImpl.java): Cálculo automático de utilidades fija y porcentual, validación estricta de unicidad en backend, validación de relaciones activas, conmutación de estado y soft-delete.
- **Controlador REST:**
  - [ProductController.java](file:///Users/joaquin/Documents/Akhana%20Admin/backend/src/main/java/com/akhana/akhana_admin/controller/ProductController.java): Endpoints REST `/api/products` (GET, GET /{id}, POST, PUT /{id}, PATCH /{id}/status, DELETE /{id}).

### 2.3 Frontend (Angular 21 Standalone & Signals)
- **Modelos y Servicio:**
  - [product.models.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/product/models/product.models.ts): Interfaces TypeScript con tipado estricto.
  - [product.service.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/core/product/product.service.ts): Servicio reactivo HTTP.
- **Vista `ProductsComponent`:**
  - [products.component.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/products/products.component.ts)
  - [products.component.html](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/products/products.component.html)
  - [products.component.css](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/pages/products/products.component.css)
- **Comportamientos Destacados:**
  - Tabla con orden inicial A-Z, visualización de colores en pastillas de categoría y etiquetas.
  - Filtros segmentados `Activos/Inactivos` (default), `Activos`, `Inactivos`, `Eliminados`.
  - Buscador insensible a mayúsculas por código y nombre.
  - Selector cromático de categoría con círculo de color y nombre.
  - Selector múltiple interactivo de etiquetas con pastillas removibles.
  - Cálculo reactivo automático en tiempo real de utilidades (solo lectura).
  - Dirty-checking e indicadores visuales `.field-modified-badge`.
  - Guardar siempre activo en creación; condicional en edición.
  - Modales de confirmación para descarte, conmutación de estado y eliminación lógica.
  - Modal de auditoría con icono de reloj unificado y cierre en backdrop.
- **Enrutamiento:** Conectado en [app.routes.ts](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/app.routes.ts) reemplazando la vista temporal.

---

## 3. Evidencias de Verificación Automatizada

- **Backend (Gradle / JUnit 5):**
  - Todas las pruebas pasaron exitosamente (93/93 tests).
  - Cobertura de cálculos de margen, unicidad backend, soft-delete, conmutación de estado y MockMvc.
- **Frontend (Vitest / Angular 21):**
  - **132/132 pruebas unitarias aprobadas al 100%** en 20 suites.
  - 13 pruebas específicas para `ProductsComponent` y 7 para `ProductService`.
- **Compilación de Producción:**
  - `npm run build` ejecutado exitosamente con 0 errores y 0 advertencias.

---

## 4. Ajustes de Feedback (Formulario y Acciones)

1. **Eliminación de Conmutación de Estado desde el Listado:**
   - Se removió el botón `btn-toggle-status` y sus estilos asociados de las acciones de la tabla de productos.
   - Se eliminaron las señales reactivas `isConfirmStatusOpen`, `productToToggleStatus`, `isTogglingStatus` y métodos asociados (`openToggleStatusModal`, `confirmToggleStatus`, `cancelToggleStatus`).
   - Se eliminó el modal `<app-confirm-modal>` para cambio de estado en el listado.
   - El estado de los productos se gestiona y visualiza exclusivamente dentro del formulario de edición según las reglas de negocio.
2. **Reorganización en una Sola Línea:**
   - En el formulario de edición, se colocaron **Categoría | Proveedor | Estado** en la misma fila (`.form-row`), manteniendo una proporción y espaciado idéntico (`flex-1`).
3. **Unificación Visual y de Comportamiento de Dropdowns:**
   - Se adoptó el componente visual y estructura del dropdown de Categoría como patrón de referencia para Proveedor y Estado.
   - Proveedor y Estado ahora implementan `.custom-select-trigger`, icono chevron SVG, estado `:focus` / `:focus-visible` idéntico con sombra verde corporativa, `.custom-dropdown-menu` flotante y `.dropdown-item`.
   - Altura unificada a `42px` con `box-sizing: border-box`, bordes, radio, padding y tipografía idénticos.
   - Cierre automático al hacer clic fuera mediante `@HostListener('document:click')`.

