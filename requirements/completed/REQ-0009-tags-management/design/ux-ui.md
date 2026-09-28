# Especificación UI/UX — REQ-0009: Gestión de Etiquetas

## 1. Identidad Visual y Paridad con Categorías
El módulo de Etiquetas `/tags` adopta exactamente la misma identidad visual *Corporate Organic Glassmorphism & Zen Minimalist* que Categorías:

```text
┌────────────────────────────────────────────────────────────────────────┐
│ Catálogo / Etiquetas                                                   │
│ Gestión de Etiquetas                                   [+ Nueva Etiqueta]│
│ Catálogo de descriptores y atributos cromáticos                        │
├────────────────────────────────────────────────────────────────────────┤
│ [ Activos ] [ Eliminados ]            [🔍 Buscar por nombre... ] 15 tags│
├────────────────────────────────────────────────────────────────────────┤
│ COLOR     │ NOMBRE               │ ESTADO │ ACCIONES                   │
├───────────┼──────────────────────┼────────┼────────────────────────────┤
│ 🟢 #164312│ Vegano               │ Activo │ 🕒 ✏️ 🗑️                    │
│ 🟡 #fabd0d│ Sin Gluten           │ Activo │ 🕒 ✏️ 🗑️                    │
│ 🔵 #0f766e│ Biodegradable        │ Activo │ 🕒 ✏️ 🗑️                    │
│ 🔴 #b91c1c│ Promoción Especial   │ Activo │ 🕒 ✏️ 🗑️                    │
└───────────┴──────────────────────┴────────┴────────────────────────────┘
```

---

## 2. Componentes Reutilizados Directamente

1. **`app-color-picker` (`ColorPickerComponent`):**
   - Selector visual de 18 tonos orgánicos.
   - Botones redondeados con micro-animaciones al cursor.
   - Anillo exterior y check blanco en color seleccionado.
   - Previsualización activa con punto cromático, código hexadecimal y nombre del tono.

2. **`app-modal` (`ModalComponent`):**
   - Modal de formulario con `size="md"` y `[closeOnBackdrop]="false"`.
   - Cierre controlado exclusivamente por `X` o botón Cancelar.

3. **`app-confirm-modal` (`ConfirmModalComponent`):**
   - Diálogo de descarte de cambios con advertencia (`variant="warning"`).
   - Diálogo de eliminación lógica (`variant="danger"`).

4. **`app-audit-modal` (`AuditModalComponent`):**
   - Icono unificado idéntico al de Proveedores y Categorías:
     `<circle cx="12" cy="12" r="10" /> <polyline points="12 6 12 12 16 14" />`.
   - `[closeOnBackdrop]="true"`.
   - Formato automático de fechas y filtrado de campos nulos.

---

## 3. Comportamiento de Formulario y Dirty Checking
- **Creación:**
  - Botón "Crear Etiqueta" siempre activo.
  - Al pulsar, valida que el nombre no esté vacío y que se haya seleccionado un color.
- **Edición:**
  - Botón "Guardar Cambios" inicia deshabilitado.
  - Se habilita en tiempo real al modificar nombre o color.
  - Se re-deshabilita si ambos valores vuelven al estado original.
  - Muestra etiqueta visual `.field-modified-badge` ("Modificado") junto al label del campo editado.
