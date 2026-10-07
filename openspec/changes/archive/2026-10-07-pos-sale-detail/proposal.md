# Proposal: POS — Detalle de venta y acciones del listado

## Why

Actualmente, el listado de transacciones de ventas en la pantalla de POS únicamente dispone de la acción de anulación ("Anular"). Los cajeros y administradores no disponen de un mecanismo para inspeccionar el desglose completo de una venta registrada (productos vendidos, cantidades, descuentos por ítem, descuento general, métodos de pago aplicados y datos de auditoría de anulación) sin alterar la venta.
Además, se requiere preparar un componente modal de visualización de venta completamente desacoplado y reutilizable que sirva como única fuente de verdad para el POS y para la futura pantalla de Historial de Ventas, garantizando consistencia visual y de comportamiento con las tablas maestras del sistema (Productos y Proveedores).

## What Changes

- **Estandarización visual de acciones en tabla POS:** Se alinea el diseño visual de la columna de acciones en la tabla de transacciones del POS al patrón oficial del sistema (`.table-actions` y botones `.btn-icon` utilizados en Productos y Proveedores), reemplazando los estilos ad-hoc previos.
- **Acción "Ver detalle":** Se añade el botón de acción "Ver detalle" con icono de inspección (ojo / visibilidad) antes del botón "Anular" en cada fila de venta.
- **Modal de Detalle Reutilizable (`SaleDetailModalComponent`):** Implementación de un componente modal autónomo y desacoplado en `shared/components/sale-detail-modal` que recibe una venta y presenta:
  - Información general: N.º de venta, fecha y hora, usuario vendedor, sesión de caja (`Sesión #X`) y estado (`Completada` / `Anulada`).
  - Tabla de productos: Producto, cantidad, precio unitario original, descuento (monto y porcentaje si aplica) y subtotal final de la línea.
  - Resumen de totales y descuentos acumulados: Monto sin descuento, descuentos por productos, descuento por venta, total de descuentos acumulados (`Descuentos por productos + Descuento por venta`) y total neto pagado.
  - Métodos de pago: Desglose de importes abonados por cada método utilizado (`Efectivo`, `QR`, `Mixto`).
  - Auditoría de anulación: Si la venta está en estado `ANULADA`, sección dedicada de sólo lectura con fecha/hora de anulación, usuario que realizó la anulación y motivo registrado.
  - Modo estricto de solo lectura: No permite ediciones, modificaciones de importes ni anulación desde el interior del modal.
- **Soporte en Backend / DTOs:** Exposición del número correlativo de sesión (`sessionNumber`) en `SaleResponse` para mostrar el formato `Sesión #X` en el detalle general de la venta.

## Capabilities

### New Capabilities
<!-- No new standalone capabilities; this extends sales registration and consultation. -->

### Modified Capabilities
- `sales-registration`: Se incorporan requerimientos para la consulta detallada de ventas mediante modal desacoplado reutilizable y la incorporación de la acción "Ver detalle" con diseño consistente en el listado de ventas.

## Impact

- **Frontend:**
  - Nuevo componente reutilizable: `frontend/src/app/shared/components/sale-detail-modal/sale-detail-modal.component.{ts,html,css}`.
  - Modificación en `frontend/src/app/pages/pos/pos.component.{ts,html,css}` para integrar el modal y adaptar la columna de acciones de la tabla de transacciones.
  - Modificación de modelos en `frontend/src/app/core/cash/models/cash.models.ts` para soportar `sessionNumber` en la interfaz `Sale`.
- **Backend:**
  - Actualización de `SaleResponse.java` para incluir `sessionNumber` a partir de `CashSession`.
  - Sin cambios destructivos ni modificaciones en el flujo o validaciones de anulación existente.
