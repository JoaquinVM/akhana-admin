# Requerimiento — REQ-0008: Gestión de Categorías y Selector de Color Reutilizable

## 1. Objetivo

Implementar la gestión completa de **Categorías** del sistema (`/categories`), siguiendo los lineamientos funcionales, visuales y de reutilización establecidos en el módulo de Proveedores (`REQ-0006`).

Se reutilizarán los componentes, estilos y patrones ya implementados en el sistema:
* Modal de formulario (`ModalComponent` con `closeOnBackdrop=false`).
* Modal de confirmación (`ConfirmModalComponent`) para descarte de cambios y eliminación lógica.
* Modal de auditoría (`AuditModalComponent` con `closeOnBackdrop=true` y filtrado estricto de campos nulos/vacíos).
* Estilos globales de tablas (`.table`), formularios (`.form-grid`, `.form-control`, `.invalid-feedback`), badges de estado (`.badge-active`, `.badge-deleted`), segmented controls (`.filter-pill-group`) e indicadores visuales de campos modificados (`.is-modified`, `.field-modified-badge`).
* Componente reutilizable nuevo: Selector visual de color (`ColorPickerComponent`).

---

## 2. Navegación

- Ubicación: Menú horizontal superior -> Grupo **Catálogo** -> Opción **Categorías**.
- Ruta asociada: `/categories`.
- Acceso: Protegido por `authGuard`.

---

## 3. Modelo de Datos y Atributos

| Campo | Tipo | Obligatorio | Reglas / Restricciones |
| :--- | :--- | :---: | :--- |
| **ID** | UUID | Automático | Clave primaria generada por el sistema. |
| **Nombre** | String | Sí | Máximo 150 caracteres. Único entre categorías con estado `ACTIVO`. |
| **Descripción** | String | No | Máximo 500 caracteres. Notas o detalles adicionales. |
| **Color** | String | Sí | Código hexadecimal seleccionado visualmente a través del `ColorPickerComponent`. |
| **Estado** | Enum | Sí | Valores permitidos: `ACTIVO`, `ELIMINADO`. Por defecto `ACTIVO`. No existe `INACTIVO`. |
| **Auditoría** | Objeto | Automático | `createdBy`, `createdAt`, `updatedBy`, `updatedAt`, `deletedBy`, `deletedAt`. |

---

## 4. Reglas de Negocio Clave

1. **Selector Visual de Color Reutilizable:**
   - La selección de color es 100% visual mediante una paleta curada y armónica.
   - El usuario no escribe código hexadecimal manualmente.
   - Indica claramente el color activo/seleccionado.
   - Desarrollado como componente reutilizable en `frontend/src/app/shared/components/color-picker/`.
2. **Validación Exclusiva de Unicidad en Backend:**
   - El nombre de la categoría debe ser único entre categorías con estado `ACTIVO` (case-insensitive).
   - Los registros `ELIMINADO` quedan excluidos de la validación; un nombre eliminado puede reutilizarse.
   - El frontend no pre-valida duplicados; envía la solicitud al backend y muestra el error devuelto (`409 Conflict`).
   - Durante la edición, se excluye el ID de la propia categoría que se está modificando.
3. **Estados y Acciones Permitidas:**
   - `ACTIVO`: Permite **Editar**, **Eliminar** (soft delete con confirmación) y **Auditoría**.
   - `ELIMINADO`: Permite únicamente **Auditoría** (botones Editar y Eliminar no visibles ni permitidos).
   - No existe el estado `INACTIVO` para categorías.
4. **Filtro de Estados y Búsqueda:**
   - Filtro por estado con segmented control: **Activos** (`ACTIVO`, seleccionado por defecto) y **Eliminados** (`ELIMINADO`).
   - Búsqueda en tiempo real por coincidencia parcial en **Nombre** (case-insensitive).
   - Filtro y búsqueda operan conjuntamente.
5. **Comportamiento del Formulario en Modal:**
   - Creación y edición se abren dentro de `app-modal` con `closeOnBackdrop=false`.
   - Cierre permitido exclusivamente mediante botón `X` o botón `Cancelar`.
   - Si existen cambios sin guardar, al pulsar `X` o Cancelar se despliega `ConfirmModalComponent` (*"¿Descartar cambios?"*).
   - Si no existen cambios, cierra de inmediato sin confirmación.
   - **En creación:** Botón Guardar siempre habilitado; al hacer clic ejecuta validación `markAllAsTouched()`.
   - **En edición:** Botón Guardar deshabilitado si no hay cambios respecto a los valores iniciales; se habilita al modificar y vuelve a deshabilitarse si se revierten.
   - Campos modificados se identifican visualmente con `.is-modified` y `.field-modified-badge`.
