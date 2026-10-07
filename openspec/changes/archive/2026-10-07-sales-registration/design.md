# Design: Registro de Ventas

## Context

El POS cuenta actualmente con un formulario provisional de registro rápido de ventas que únicamente registra un monto manual y una descripción libre (`SaleRequest` con `amount`, `description`, `paymentMethod`). Con la migración a un punto de venta comercial real, se requiere sustituir este mecanismo por una arquitectura completa de carrito, catálogo de productos activos, grupos de productos frecuentes configurables con drag-and-drop, políticas de descuento por línea y descuento general, cobro multimetodal (`EFECTIVO`, `QR`, `MIXTO`) con cálculo de cambio, y anulación auditada restringida a cajas abiertas.

## Goals / Non-Goals

**Goals:**
- Proporcionar una experiencia POS ágil y de alta productividad: búsqueda instantánea de productos y rejilla de productos rápidos organizados por grupos temáticos.
- Permitir la configuración interactiva de grupos y productos rápidos desde el POS con drag-and-drop para reordenar grupos y productos, con persistencia centralizada en base de datos PostgreSQL.
- Soportar cálculo preciso de subtotales, descuentos por unidad de producto (monto fijo) y descuento global de la venta en monto fijo.
- Gestionar cobros en Efectivo (con validación de monto recibido y cálculo automático de cambio), QR directo, y Mixto (efectivo + QR con remanente automático y cambio).
- Implementar anulación auditada de ventas vinculadas a cajas abiertas con motivo obligatorio, fecha, usuario y reversión en tiempo real de los acumuladores financieros de la caja.
- Mantener inmutabilidad histórica: las ventas anuladas no se eliminan físicamente y quedan reflejadas en consultas y auditoría.

**Non-Goals:**
- Asociación o gestión de clientes (facturación nominal / clientes queda fuera de alcance según requerimiento).
- Control de stock o inventario (se excluye explícitamente en esta fase; el stock no se descuenta ni se valida).
- Anulación de ventas pertenecientes a cajas que ya han sido cerradas.

## Decisions

### 1. Modelo de Datos y Migración Flyway (`V7__create_sales_registration_tables.sql`)
- **Extensión de tabla `sales`**: Se agregan columnas para trazabilidad detallada sin romper compatibilidad existente:
  - `status VARCHAR(20) NOT NULL DEFAULT 'COMPLETADA'` (`COMPLETADA`, `ANULADA`).
  - `subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00` (suma de `cantidad × precio_unitario`).
  - `discount_items_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00` (suma de descuentos por ítem).
  - `global_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `amount_cash NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `amount_qr NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `amount_received NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `change_given NUMERIC(12, 2) NOT NULL DEFAULT 0.00`.
  - `voided_at TIMESTAMP WITH TIME ZONE`, `voided_by VARCHAR(100)`, `void_reason VARCHAR(500)`.
- **Nueva tabla `sale_items`**: Almacena el detalle de ítems de cada venta con `sale_id`, `product_id`, `product_name`, `product_code`, `unit_price`, `discount_per_unit`, `final_unit_price`, `quantity`, `subtotal`.
- **Tablas de Productos Rápidos (`quick_product_groups`, `quick_product_group_items`)**: Almacenan los grupos y su asignación de productos con un campo `display_order INT` para persistir la ordenación resultante del drag-and-drop.
  - *Alternativa considerada*: LocalStorage en navegador. *Descartada* porque no permitiría sincronización consistente entre cajeros y turnos de trabajo compartidos en el terminal.

### 2. Afectación Financiera y Anulación en `CashSession`
- Al registrar una venta:
  - `total_sales_cash` incrementa por `amount_cash`.
  - `total_sales_qr` incrementa por `amount_qr`.
  - `total_sales` incrementa por `total_amount`.
  - `expected_cash` incrementa por `amount_cash`.
- Al anular una venta:
  - Se valida bajo bloqueo transaccional que `cash_session.status == 'ABIERTA'`. Si la caja está cerrada, se lanza `IllegalStateException`.
  - La venta cambia su estado a `ANULADA` y registra motivo y usuario.
  - Se deducen `amount_cash` de `total_sales_cash` y `expected_cash`, y `amount_qr` de `total_sales_qr`, recalculando `total_sales`.
  - Se registra la transacción correspondiente para auditoría.

### 3. Separación de Pantallas y Cobro Inline (ADR: Navegación Dedicada)
- Se traslada toda la funcionalidad operativa de registro de venta a una pantalla dedicada `RegisterSaleComponent` en la ruta `/pos/sale`.
- El POS principal (`PosComponent` en `/pos`) conserva su propósito de supervisión de caja (KPIs en tiempo real, apertura/cierre, y tabla de transacciones de la sesión activa). El botón «Registrar venta» en la cabecera de transacciones navega hacia `/pos/sale`.
- El cobro se integra **directamente en pantalla (inline)** dentro de `RegisterSaleComponent`, suprimiendo la necesidad de modales auxiliares para el pago (`CheckoutModalComponent`) y permitiendo completar todo el flujo en una única vista interactiva.
- La pantalla de ventas incluye:
  - Buscador predictivo de productos activos y rejilla de productos rápidos organizados por grupos con botón de configuración drag-and-drop (`QuickProductsConfigModalComponent`).
  - Carrito interactivo con edición de cantidad, descuentos por unidad, subtotal por ítem y descuento general.
  - Sección inferior de cobro inline con tabs (`Efectivo`, `QR`, `Mixto`), inputs de monto recibido, cálculo reactivo de cambio o remanente QR y botón de confirmación de venta con retorno al POS.
  - Botón «← Volver al POS» / «Cancelar venta».
- Se mantiene el modal de anulación (`VoidSaleModalComponent`) en el POS principal para revertir transacciones de cajas abiertas con motivo obligatorio.

## Risks / Trade-offs

- **[Riesgo: Concurrencia al anular venta mientras se cierra la caja]** → *Mitigación*: Validación estricta a nivel de servicio Spring Boot dentro de `@Transactional` consultando el estado fresco de `cash_session` antes de mutar saldos.
- **[Riesgo: Discrepancias por redondeo en descuentos o pago mixto]** → *Mitigación*: Uso estricto de `BigDecimal` con escala a 2 decimales y redondeo `RoundingMode.HALF_UP` en backend, y cálculos tipados con redondeo financiero en Angular.
- **[Riesgo: Descuento general mayor al total]** → *Mitigación*: Validación cruzada tanto en frontend (bloqueo reactivo) como en backend (`IllegalArgumentException`).

## Migration Plan

1. Ejecutar migración Flyway `V7__create_sales_registration_tables.sql` para actualizar la base de datos PostgreSQL.
2. Sincronizar el modelo JPA y crear DTOs de venta y productos rápidos.
3. Desplegar endpoints REST en backend y ejecutar batería de tests unitarios/integración.
4. Actualizar frontend Angular integrando `@angular/cdk/drag-drop`, nuevos servicios y componentes del POS.
5. Ejecutar tests de frontend y verificar compilación de producción.
