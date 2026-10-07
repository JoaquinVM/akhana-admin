# Tasks: POS — Detalle de venta y acciones del listado

## 1. Backend & Data Models

- [x] 1.1 Incorporar `Long sessionNumber` en `SaleResponse.java` extrayéndolo de `CashSession` y verificar que la suite de pruebas del backend pase con `./gradlew test`
- [x] 1.2 Actualizar el modelo `Sale` en `frontend/src/app/core/cash/models/cash.models.ts` con `sessionNumber?: number` y verificar compatibilidad de tipos

## 2. Modal Reutilizable de Detalle de Venta (`SaleDetailModalComponent`)

- [x] 2.1 Crear el componente standalone desacoplado `frontend/src/app/shared/components/sale-detail-modal/sale-detail-modal.component.{ts,html,css}` que realice la carga bajo demanda desde el backend (`SaleService.getSaleById`) al consultar una venta (manteniendo únicamente en memoria la venta activa y mostrando estado de carga/spinner), con:
  - Cabecera con título e icono de cierre
  - Información general: N.º de venta, fecha y hora, usuario, sesión (`Sesión #X`) y estado (`Completada` / `Anulada`)
  - Tabla de productos: Producto, cantidad, precio unitario, descuento (monto y % si aplica) y subtotal
  - Resumen financiero: Monto sin descuento, descuentos por productos, descuento por venta, total de descuentos acumulados y total neto
  - Desglose de métodos de pago (Efectivo, QR, etc.)
  - Sección condicional de auditoría de anulación (fecha/hora, usuario, motivo) para ventas en estado `ANULADA`
  - Restricción estricta de solo lectura (sin edición ni botón de anulación interno)
- [x] 2.2 Implementar pruebas unitarias completas en `sale-detail-modal.component.spec.ts` verificando renderizado de venta completada, desglose de descuentos, visualización de auditoría en venta anulada y evento de cierre, comprobando con `npm test`

## 3. Integración en POS y Estandarización Visual del Listado

- [x] 3.1 Estandarizar la columna de acciones de la tabla de transacciones en `pos.component.html` adoptando el diseño de Productos y Proveedores (`.table-actions` y `.btn-icon`), con el orden: 1. "Ver detalle" y 2. "Anular"
- [x] 3.2 Integrar `SaleDetailModalComponent` en `pos.component.ts` y su plantilla vinculando el estado y señales para abrir y cerrar el modal
- [x] 3.3 Actualizar las pruebas unitarias de `pos.component.spec.ts` para cubrir la apertura y cierre del modal de detalle y la preservación del flujo de anulación, comprobando con `npm test`

## 4. Verificación y Validación de Calidad

- [x] 4.1 Ejecutar compilación y validación integral del frontend con `npm run build` y backend con `./gradlew test`
- [x] 4.2 Validar la especificación en OpenSpec con `openspec validate pos-sale-detail --strict`
