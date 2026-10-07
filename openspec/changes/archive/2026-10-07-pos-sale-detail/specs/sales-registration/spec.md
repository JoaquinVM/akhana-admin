# Spec Delta: sales-registration

## MODIFIED Requirements

### Requirement: Trazabilidad y consulta de ventas
El sistema SHALL presentar en las consultas de ventas y en el panel de transacciones de la caja las ventas registradas con su estado (`COMPLETADA` o `ANULADA`), conservando el detalle histórico íntegro sin eliminación física. El listado de transacciones SHALL ofrecer una columna de acciones que siga el patrón visual estándar del sistema (`.table-actions` y botones `.btn-icon` de Productos y Proveedores) con las acciones ordenadas en: 1. "Ver detalle" (inspección de sólo lectura) y 2. "Anular" (si la venta no está anulada y la caja está abierta).

#### Scenario: Consulta de venta anulada
- **WHEN** el usuario visualiza el listado de transacciones de ventas del turno o consulta general
- **THEN** la venta anulada se visualiza con distintivo visual `ANULADA`, conservando sus datos originales sin exponer el motivo de anulación en la fila del listado, con la acción "Ver detalle" habilitada para consultar dicho motivo y auditoría, y la acción "Anular" deshabilitada para prevenir una segunda anulación

#### Scenario: Acciones estándar en el listado de ventas
- **WHEN** el usuario revisa cualquier fila de venta en la tabla del POS
- **THEN** la columna de acciones muestra el botón "Ver detalle" en primer lugar y el botón "Anular" en segundo lugar, adoptando el diseño consistente con las tablas de Productos y Proveedores

---

## ADDED Requirements

### Requirement: Modal reutilizable y desacoplado de detalle de venta
El sistema SHALL proveer un componente modal reutilizable e independiente (`SaleDetailModalComponent`) para visualizar el desglose exhaustivo de una venta en modo estricto de solo lectura, tanto desde el POS como posteriormente desde el Historial de ventas.

#### Scenario: Visualización general de la venta
- **WHEN** el usuario pulsa "Ver detalle" sobre una venta
- **THEN** el sistema abre el modal mostrando N.º de venta, fecha y hora, usuario que registró la venta, sesión de caja asociada en formato `Sesión #X` y el estado (`Completada` o `Anulada`)

#### Scenario: Desglose de productos vendidos y descuentos por ítem
- **WHEN** se visualiza el modal de detalle de una venta
- **THEN** se presenta una tabla con las columnas Producto, Cantidad, Precio unitario (original), Descuento (monto y porcentaje si corresponde) y Subtotal de cada ítem

#### Scenario: Resumen de totales y descuentos acumulados
- **WHEN** se visualiza el resumen económico en el modal de detalle
- **THEN** se exhibe el Monto sin descuento, Descuentos por productos, Descuento por venta, el Total descuentos acumulados (`Descuentos por productos + Descuento por venta`) y el Total neto final

#### Scenario: Desglose de métodos de pago utilizados
- **WHEN** se consulta el modal de detalle de una venta
- **THEN** se listan todos los métodos de pago aplicados (`Efectivo`, `QR` o ambos) junto con sus importes exactos cobrados

#### Scenario: Información de auditoría para ventas anuladas
- **WHEN** se abre el detalle de una venta cuyo estado es `ANULADA`
- **THEN** el modal mantiene visibles todos los productos, montos y métodos originales y despliega adicionalmente una sección destacada con fecha/hora de anulación, usuario que realizó la anulación y motivo registrado

#### Scenario: Restricción de solo consulta
- **WHEN** el modal de detalle se encuentra abierto
- **THEN** no se presentan controles de edición de productos, cantidades, precios o métodos de pago, ni botones de anulación dentro del modal, permitiendo únicamente cerrar el modal y regresar a la vista de origen
