# Supuestos y Decisiones Técnicas Iniciales - REQ-0008: Gestión de Categorías

1. **Persistencia y Base de Datos:**
   - La tabla se denominará `categories`.
   - La clave primaria será de tipo `UUID` con valor por defecto `gen_random_uuid()`.
   - Se creará un índice único parcial `uq_categories_name_active` sobre `LOWER(name)` con cláusula `WHERE status != 'ELIMINADO'`.

2. **Color de Categoría:**
   - Se almacenará como un string hexadecimal de formato `#RRGGBB`.
   - `ColorPickerComponent` proporcionará una paleta curada de 16 a 20 colores estéticos, armoniosos con el diseño orgánico y botánico de Akhana, además de tonos distintivos para clasificación (verdes botánicos, esmeraldas, oros zen, terracotas, azules profundos, violetas suaves, ámbar, etc.).
   - En la tabla de categorías, el color se visualizará con una muestra circular/píldora con borde y sombra suave, acompañada opcionalmente del nombre tonal o swatch.

3. **Restricción de Estados:**
   - A diferencia de Proveedores (que tiene `INACTIVO`), Categorías solo tiene `ACTIVO` y `ELIMINADO`.
   - El selector de estado no es necesario en el formulario de creación ni de edición: las categorías creadas o editadas son siempre `ACTIVO`.
   - El filtro en la cabecera tendrá exactamente dos pestañas: `Activos` (`ACTIVO`) y `Eliminados` (`ELIMINADO`).

4. **Reutilización Estricta:**
   - Se importarán directamente `ModalComponent`, `ConfirmModalComponent` y `AuditModalComponent` sin duplicar ningún elemento HTML o CSS.
   - Las clases globales `.table`, `.form-grid`, `.form-control`, `.is-modified`, `.field-modified-badge`, `.filter-pill-group`, `.status-badge` serán utilizadas directamente desde `styles.css`.
