# Especificación de Diseño UI/UX - REQ-0006: Proveedores y Componentes Reutilizables

## 🎨 1. Concepto y Filosofía Visual

La interfaz de Proveedores y los nuevos componentes compartidos continúan la identidad **Corporate Organic Glassmorphism** de **Akhana**: superficies translúcidas, contrastes nítidos, tipografía Space Grotesk / Inter, y la paleta de identidad:
- **Verde Bosque:** `#1B3B18` y `#2E5B27`
- **Dorado Zen:** `#F5B800`
- **Acento Botánico:** `#7BB142`
- **Alerta / Eliminación:** `#BA1A1A` con fondos en `rgba(186, 26, 26, 0.08)`

---

## 📸 1.1 Diseños Generados en Google Stitch
- **Pantalla Principal de Proveedores (`/suppliers`):**
  - **ID de Pantalla Stitch:** `0db49bb3de8e40ce9ccd524cf3c5b5e9`
  - **Captura:** ![Pantalla de Proveedores en Stitch](/Users/joaquin/.gemini/antigravity-ide/brain/e8f51b3f-df45-4f1b-a308-2162f14c8b88/stitch_suppliers_screen.png)
- **Suite de Modales Reutilizables (Confirmación, Auditoría y Formulario):**
  - **ID de Pantalla Stitch:** `af73b941fe734098b6fa3744868ae185`
  - **Captura:** ![Modales Reutilizables en Stitch](/Users/joaquin/.gemini/antigravity-ide/brain/e8f51b3f-df45-4f1b-a308-2162f14c8b88/stitch_suppliers_modals.png)

---

## 🧩 2. Componentes Reutilizables

### 2.1 Modal de Confirmación (`ConfirmModalComponent`)
- **Backdrop:** `rgba(22, 40, 20, 0.45)` con `backdrop-filter: blur(8px)`.
- **Contenedor:** Ancho máximo 480px, esquinas `16px`, borde `1px solid rgba(46, 91, 39, 0.16)`.
- **Estructura:**
  - Ícono circular de advertencia/peligro en tonos suaves.
  - Título jerárquico `<h3>` en Space Grotesk.
  - Mensaje descriptivo con énfasis en las consecuencias de la acción.
  - Botonera con botón "Cancelar" (estilo secundario) y botón de confirmación ("Eliminar" en rojo o "Confirmar" en verde).

### 2.2 Modal de Auditoría (`AuditModalComponent`)
- **Estructura:**
  - Encabezado con título del recurso auditado y botón de cierre (`✕`).
  - Tarjetas o lista cronológica de eventos:
    - **Registro de Creación:** Usuario y fecha formateada con ícono verde.
    - **Última Modificación:** Usuario y fecha formateada con ícono dorado/azul (solo si existe).
    - **Eliminación Lógica:** Usuario y fecha con ícono rojo (solo si existe).
  - Regla estricta: Los campos con valor `null`, vacíos o `undefined` se omiten totalmente.

---

## 📋 3. Pantalla de Proveedores (`/suppliers`)

```text
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│  Proveedores                                                                                     │
│  Gestión y catálogo de proveedores comerciales                                                   │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│  [🔍 Buscar por nombre o código...]                              [+ Nuevo Proveedor]            │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│  Código      Nombre                       Teléfono         Estado       Acciones                 │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│  PROV-001    AgroInsumos del Valle S.A.   +56 9 1234 5678  [ACTIVO]     [✏️ Editar] [🔍] [🗑️]   │
│  PROV-002    Botánica Zen Ltda.           +56 9 8765 4321  [INACTIVO]   [✏️ Editar] [🔍] [🗑️]   │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Badges de Estado
- `ACTIVO`: Píldora verde translúcida (`rgba(123, 177, 66, 0.16)`) con texto `#2E5B27` y micro-punto.
- `INACTIVO`: Píldora gris/ámbar suave (`rgba(100, 116, 139, 0.16)`) con texto `#475569`.
- `ELIMINADO`: Píldora roja (`rgba(186, 26, 26, 0.14)`) con texto `#BA1A1A` (solo visible en auditoría).

### 3.2 Formulario de Proveedor (Modal / Drawer)
- Campos:
  - `Nombre` (*): Input con foco en verde bosque.
  - `Código` (*): Input para código único de negocio.
  - `Teléfono`: Input para contacto.
  - `Descripción`: Textarea opcional.
  - `Estado`: Solo visible al editar (`ACTIVO` / `INACTIVO`).
- Manejo de error de duplicidad: Alerta integrada sobre el formulario mostrando el mensaje devuelto por el backend.
