# Supuestos de Ingeniería — REQ-0010: Gestión de Productos

1. **Moneda y Precisión Decimal:**
   - Los precios de compra y venta se manejan con precisión `NUMERIC(12, 2)` (2 decimales).
   - La utilidad fija se calcula como `sellPrice - buyPrice` redondeada a 2 decimales.
   - La utilidad porcentual se calcula como `((sellPrice - buyPrice) / buyPrice) * 100` redondeada a 2 decimales (o 1 decimal en visualización, e.g. `25.00%` o `25%`). Si `buyPrice <= 0`, la utilidad porcentual es `0.00%` para evitar división por cero.
   - Se admite que la utilidad fija sea negativa si el precio de venta es menor que el de compra (caso de venta a pérdida), reflejándose con signo negativo o advertencia visual no bloqueante.

2. **Unicidad de Código y Nombre:**
   - La comparación es insensible a mayúsculas y minúsculas (`LOWER(code)` y `LOWER(name)`).
   - Se validará en backend con consultas de repositorio y estará blindada a nivel de base de datos con índices únicos parciales `WHERE status != 'ELIMINADO'`.

3. **Disponibilidad de Entidades Relacionadas:**
   - Para asociar una categoría o proveedor a un producto nuevo o editado, la entidad debe existir y no estar en estado `ELIMINADO`.
   - Las etiquetas disponibles para selección deben ser etiquetas activas (`status = 'ACTIVO'`). Si un producto ya tenía una etiqueta que luego fue eliminada, el producto mantiene la referencia histórica en lectura, pero no se permite seleccionar etiquetas eliminadas en nuevas asociaciones.

4. **Componentes y Reutilización:**
   - Se reutilizarán directamente [ModalComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/modal/modal.component.ts), [ConfirmModalComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/confirm-modal/confirm-modal.component.ts) y [AuditModalComponent](file:///Users/joaquin/Documents/Akhana%20Admin/frontend/src/app/shared/components/audit-modal/audit-modal.component.ts).
   - Para el selector de categorías y el selector múltiple de etiquetas, se implementarán controles estilizados con la identidad Corporate Organic Glassmorphism, integrando pastillas cromáticas (`.category-color-pill` / `.tag-pill`) existentes.

5. **Soft Delete e Integridad:**
   - La eliminación de un producto es estrictamente lógica (`status = 'ELIMINADO'`).
   - La tabla intermedia `product_tags` tiene clave foránea hacia `products(id)` con `ON DELETE CASCADE` (para limpieza en caso de purge administrativo) y hacia `tags(id)` con `ON DELETE CASCADE`.
