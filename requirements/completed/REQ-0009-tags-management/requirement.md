# Requerimiento: REQ-0009 - Gestión de Etiquetas

## 1. Información General
- **ID:** REQ-0009
- **Nombre:** Gestión de Etiquetas (Tags Management)
- **Módulos Afectados:** `database/`, `backend/`, `frontend/`
- **Estado:** `SPECIFIED`
- **Fecha de Creación:** 2026-09-28

---

## 2. Objetivo del Requerimiento
Implementar la gestión completa de **Etiquetas** del sistema en la ruta `/tags` bajo el grupo **Catálogo** del menú superior.
El módulo debe lucir y comportarse de forma idéntica a **Categorías**, reutilizando todos los componentes, estilos y patrones de interacción (modales, confirmaciones, auditoría, selector visual de color, orden inicial A-Z, filtros segmentados Activos/Eliminados, búsqueda en tiempo real, dirty-checking e indicadores de modificación).

---

## 3. Modelo de Datos de la Etiqueta
| Campo | Tipo | Obligatorio | Descripción |
| :--- | :--- | :---: | :--- |
| `id` | `UUID` | Sí (Auto) | Identificador único del registro |
| `name` | `VARCHAR(150)` | Sí | Nombre de la etiqueta |
| `color` | `VARCHAR(50)` | Sí | Color identificador (código hexadecimal) seleccionado visualmente |
| `status` | `VARCHAR(20)` | Sí | Estado del registro (`ACTIVO`, `ELIMINADO`) |
| `created_by` | `VARCHAR(100)` | Sí (Auto) | Usuario que creó la etiqueta |
| `created_at` | `TIMESTAMPTZ` | Sí (Auto) | Fecha/hora de creación |
| `updated_by` | `VARCHAR(100)` | No | Usuario que realizó la última edición |
| `updated_at` | `TIMESTAMPTZ` | No | Fecha/hora de última edición |
| `deleted_by` | `VARCHAR(100)` | No | Usuario que realizó la eliminación lógica |
| `deleted_at` | `TIMESTAMPTZ` | No | Fecha/hora de eliminación lógica |

*Nota:* A diferencia de Categorías, Etiquetas no posee el campo `description`.

---

## 4. Reglas de Negocio Clave
1. **Estados Exclusivos:** Únicamente `ACTIVO` y `ELIMINADO`. No existe estado `INACTIVO`.
2. **Unicidad de Nombre en Backend:** El nombre debe ser único entre etiquetas con estado `ACTIVO` (insensible a mayúsculas/minúsculas). Las etiquetas `ELIMINADO` no bloquean el nombre.
3. **Validación exclusiva en Backend:** El frontend no pre-valida duplicados; envía la solicitud y procesa el error 409 Conflict.
4. **Eliminación Lógica:** La acción de eliminar marca `status = 'ELIMINADO'`, `deleted_by` y `deleted_at`. Nunca se elimina físicamente el registro.
5. **Acciones según Estado:**
   - `ACTIVO`: Consultar Auditoría, Editar, Eliminar.
   - `ELIMINADO`: Consultar Auditoría únicamente.
6. **Selector Visual de Color:** Reutiliza `ColorPickerComponent` sin ingreso manual de texto hexadecimal.
7. **Comportamiento de Modales:**
   - Modal de Formulario: `closeOnBackdrop="false"`. Al cerrar por `X` o Cancelar con cambios sin guardar, dispara modal de confirmación de descarte.
   - Botón Guardar en Creación: Siempre habilitado; ejecuta validaciones y marca campos requeridos al presionar.
   - Botón Guardar en Edición: Inicia deshabilitado y se habilita únicamente ante cambios; vuelve a deshabilitarse si se restauran los valores originales.
   - Modal de Auditoría: `closeOnBackdrop="true"` con icono unificado idéntico a Proveedores/Categorías.
