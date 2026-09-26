# REQ-0006: Gestión de Proveedores y Componentes Reutilizables

- **Identificador:** `REQ-0006`
- **Título:** Gestión de Proveedores y Componentes Reutilizables
- **Complejidad:** `LARGE`
- **Estado:** `REFINEMENT`
- **Fecha de Creación:** 2026-09-25
- **Módulos Afectados:** `backend/` y `frontend/`
- **Rama Asociada:** `main`

---

## 🎯 1. Objetivo

1. Incorporar el módulo **Proveedores** dentro del nuevo grupo **Compras** en la barra de navegación horizontal existente.
2. Desarrollar la gestión completa de proveedores (CRUD con búsqueda, consulta, creación, edición, borrado lógico y auditoría).
3. Construir una biblioteca de **componentes reutilizables** (Modal de Confirmación y Modal de Auditoría) y **estilos centralizados** (tablas, formularios y estados) para que sirvan de base para los módulos posteriores del sistema (Productos, Categorías, Usuarios, etc.).

---

## 📋 2. Requisitos de Navegación

- Agregar el grupo **Compras** a la navegación superior existente.
- Dentro de Compras, incluir la opción **Proveedores** apuntando a la ruta `/suppliers`.
- Comportamiento idéntico al menú actual:
  - Despliegue de opciones en hover.
  - Al hacer clic en Proveedores, redirige a `/suppliers` y cierra el menú inmediatamente.
  - El grupo Compras y la opción Proveedores se identifican como activos cuando la URL es `/suppliers`.

---

## 💾 3. Modelo de Datos y Estados del Proveedor

### 3.1 Datos del Proveedor
| Campo | Tipo | Obligatorio | Descripción |
| :--- | :--- | :---: | :--- |
| `id` | `UUID` | Sí | Identificador único |
| `name` | `VARCHAR(150)` | Sí | Nombre del proveedor (único entre no eliminados) |
| `code` | `VARCHAR(50)` | Sí | Código de proveedor (único entre no eliminados) |
| `description` | `VARCHAR(500)` | No | Información adicional |
| `phone` | `VARCHAR(30)` | No | Teléfono de contacto |
| `status` | `ENUM` | Sí | `ACTIVO`, `INACTIVO`, `ELIMINADO` |

### 3.2 Campos de Auditoría
| Campo | Tipo | Descripción |
| :--- | :--- | :--- |
| `createdBy` | `VARCHAR(100)` | Usuario autenticado que creó el registro |
| `createdAt` | `TIMESTAMP WITH TIME ZONE` | Fecha y hora de creación |
| `updatedBy` | `VARCHAR(100)` | Usuario autenticado que realizó la última modificación |
| `updatedAt` | `TIMESTAMP WITH TIME ZONE` | Fecha y hora de la última modificación |
| `deletedBy` | `VARCHAR(100)` | Usuario autenticado que ejecutó la eliminación lógica |
| `deletedAt` | `TIMESTAMP WITH TIME ZONE` | Fecha y hora de la eliminación lógica |

### 3.3 Reglas de Estado y Eliminación Lógica
- Todo proveedor nuevo se crea con estado `ACTIVO`.
- Un proveedor puede alternar entre `ACTIVO` e `INACTIVO`.
- La eliminación es **estrictamente lógica**: cambia el estado a `ELIMINADO` y registra `deletedBy` y `deletedAt`. Nunca física (`DELETE`).
- Los proveedores con estado `ELIMINADO` **no aparecen en el listado normal**.

---

## 🔒 4. Reglas de Unicidad y Responsabilidad Exclusiva del Backend

- El **nombre** y el **código** deben ser únicos de forma independiente.
- No puede existir más de un proveedor `ACTIVO` o `INACTIVO` con el mismo nombre.
- No puede existir más de un proveedor `ACTIVO` o `INACTIVO` con el mismo código.
- Los registros `ELIMINADO` **no participan en la validación de unicidad** (un nombre o código previamente eliminado puede volver a utilizarse).
- **Responsabilidad:** La validación de unicidad recae **exclusivamente en el backend**. El frontend no realiza consultas previas de existencia ni valida duplicados; envía la solicitud y procesa la respuesta del backend mostrando el error de negocio en caso de conflicto (HTTP 409 Conflict o BadRequest estructurado).

---

## 🖥️ 5. Vistas y Operaciones de Proveedores

### 5.1 Listado y Búsqueda
- Tabla con columnas: `Código`, `Nombre`, `Teléfono`, `Estado` (badge `ACTIVO` / `INACTIVO`) y `Acciones`.
- Orden inicial: **Nombre ascendente (A-Z)**.
- Búsqueda en tiempo real por `Nombre` o `Código` (coincidencias parciales, insensible a mayúsculas/minúsculas).
- Filtra automáticamente registros `ELIMINADO`.

### 5.2 Creación
- Formulario con campos: `Nombre` (*), `Código` (*), `Descripción`, `Teléfono`.
- Estado se establece automáticamente como `ACTIVO`.
- Auditoría automática: `createdBy` (usuario logueado en JWT) y `createdAt` (timestamp actual).

### 5.3 Edición
- Disponible únicamente para proveedores `ACTIVO` e `INACTIVO`.
- No disponible para `ELIMINADO`.
- Preserva datos originales de creación y registra `updatedBy` y `updatedAt`.
- Valida unicidad en backend excluyendo al propio registro.

### 5.4 Eliminación Lógica
- Disponible únicamente para proveedores `ACTIVO` e `INACTIVO`.
- Muestra el **componente reutilizable de confirmación**.
- Al confirmar: estado pasa a `ELIMINADO`, registra `deletedBy` y `deletedAt`.
- Al cancelar: no se altera el registro.

### 5.5 Auditoría
- Disponible para **todos** los proveedores (`ACTIVO`, `INACTIVO`, `ELIMINADO`).
- Muestra únicamente los campos que tengan valor (`null`, vacíos o `undefined` se omiten).
- Utiliza el **componente reutilizable de auditoría**.

### 5.6 Matriz de Acciones por Estado
| Estado | Editar | Eliminar | Auditoría |
| :--- | :---: | :---: | :---: |
| `ACTIVO` | Sí | Sí | Sí |
| `INACTIVO` | Sí | Sí | Sí |
| `ELIMINADO` | No | No | Sí |

---

## 🧩 6. Componentes y Estilos Reutilizables (Shared)

1. **Componente de Confirmación Reutilizable (`ConfirmModalComponent`):**
   - Configurable: Título, mensaje, texto de confirmación, texto de cancelación, callback de confirmación y cancelación.
   - Genérico e independiente del módulo de proveedores.
2. **Componente de Auditoría Reutilizable (`AuditModalComponent`):**
   - Recibe objeto de auditoría genérico y renderiza solo los campos con valor presente.
   - Reutilizable para futuras entidades (Productos, Categorías, Usuarios, etc.).
3. **Estilos Reutilizables de Tablas y Formularios:**
   - Centralizados en `src/styles.css` o utilities compartidas para inputs, labels, validaciones, botones, cabeceras de tabla, filas, badges de estado y estados de carga.
