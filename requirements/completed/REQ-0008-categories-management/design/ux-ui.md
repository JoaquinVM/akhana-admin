# Especificación UI/UX - REQ-0008: Gestión de Categorías

## 1. Guía de Interfaz y Experiencia de Usuario

La pantalla de Categorías `/categories` conserva la identidad visual "Organic Glassmorphism & Zen Minimalist" desarrollada en el sistema.

### Estructura de la Pantalla:
```text
┌────────────────────────────────────────────────────────────────────────┐
│ Catálogo / Categorías                                                  │
│ Gestión de Categorías                                 [+ Nueva Categoría]│
│ Catálogo de familias y clasificación cromática                         │
├────────────────────────────────────────────────────────────────────────┤
│ [ Activos ] [ Eliminados ]           [🔍 Buscar por nombre... ] 12 cats│
├────────────────────────────────────────────────────────────────────────┤
│ COLOR     │ NOMBRE               │ DESCRIPCIÓN         │ ESTADO │ ACC. │
├───────────┼──────────────────────┼─────────────────────┼────────┼──────┤
│ 🟢 #164312│ Sustratos y Tierras  │ Mezclas orgánicas...│ Activo │👁️✏️🗑️ │
│ 🟡 #fabd0d│ Fertilizantes Eco    │ Nutrientes bio      │ Activo │👁️✏️🗑️ │
│ 🔵 #0f766e│ Riego y Aspersión    │ Microgoteo          │ Activo │👁️✏️🗑️ │
│ 🔴 #b91c1c│ Control de Plagas    │ Biopreparados       │ Activo │👁️✏️🗑️ │
└───────────┴──────────────────────┴─────────────────────┴────────┴──────┘
```

---

## 2. Componente Reutilizable: `ColorPickerComponent`

El selector de color reemplaza cualquier input manual de código hexadecimal por una cuadrícula elegante de botones cromáticos:

```text
Color Identificador *
┌───────────────────────────────────────────────────────────┐
│ (●) [✓] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]  │
│  ↑ Seleccionado con anillo e icono de confirmación        │
│  Muestra activa: [ 🟢 #164312 - Bosque Profundo ]        │
└───────────────────────────────────────────────────────────┘
```

- **Muestra Activa:** Badge visual con el color seleccionado y su código en texto legible.
- **Cuadrícula de Swatches:** 18 botones de 28x28px redondeados (`rounded-full`), con borde sutil y transición `transform 0.15s ease`. Al pasar el cursor (`:hover`), se eleva con `scale(1.1)`.
- **Estado Activo:** El botón seleccionado tiene un anillo exterior (`ring-2 ring-offset-2 ring-primary`) y un icono de check central blanco.
- **Validación Visual:** Si el usuario no selecciona color y el campo es requerido al guardar, muestra el mensaje de error estándar `.invalid-feedback`.

---

## 3. Renderizado de Color en la Tabla

En la columna **Color** de la tabla:
- Se dibuja una píldora visual que incluye un círculo cromático de 12px con sombra sutil y el código del color (ej. `● #164312`).
- No se muestra un input ni un texto plano desnudo, sino una ficha estética integrada en el diseño del sistema.

---

## 4. Modales y Confirmaciones

- **Modal de Formulario (`ModalComponent`):**
  - Ancho medio (`size="md"`).
  - Encabezado con badge e icono de categoría.
  - Campos: Nombre (*), Descripción, Color (* con `app-color-picker`).
  - `closeOnBackdrop="false"`.
  - Disparador de confirmación si `hasFormChanges()` al cerrar por `X` o Cancelar.
- **Modal de Descarte (`ConfirmModalComponent`):**
  - Variante warning.
  - Título: *"¿Descartar cambios?"*.
  - Acciones: *"Descartar cambios"* (peligro) vs *"Continuar editando"* (neutro).
- **Modal de Eliminación (`ConfirmModalComponent`):**
  - Variante danger.
  - Título: *"¿Eliminar Categoría?"*.
  - Mensaje detallando el nombre y explicando que pasará a estado ELIMINADO de forma lógica.
- **Modal de Auditoría (`AuditModalComponent`):**
  - Cierra al hacer clic en el backdrop.
  - Muestra únicamente datos de creación, edición o eliminación con valor.
