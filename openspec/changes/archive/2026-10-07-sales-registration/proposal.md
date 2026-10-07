# Proposal

## Why

El POS actual solo cuenta con un registro preliminar de ventas basado en un monto manual y una descripción libre. Para operar comercialmente en el punto de venta, se requiere un flujo integral de registro de ventas que permita seleccionar productos del catálogo, organizar productos rápidos frecuentes en grupos configurables con drag-and-drop, aplicar descuentos unitarios y globales, procesar múltiples modalidades de pago (Efectivo con cambio, QR y Mixto Efectivo+QR), y anular ventas justificadamente con impacto directo en los balances de la sesión de caja compartida.

## What Changes

- **Buscador y Selección de Productos**: Búsqueda ágil de productos activos en la nueva pantalla de ventas. Al seleccionar un producto se agrega al detalle de venta con cantidad `1`; si ya existe en la venta, incrementa automáticamente su cantidad en `1`.
- **Productos Rápidos con Drag & Drop**: Panel configurable de accesos directos organizado en grupos de productos en la pantalla de ventas. Modal de configuración para crear/editar grupos, asignar productos, reordenar grupos y reordenar productos dentro de cada grupo mediante drag-and-drop, persistido en base de datos.
- **Líneas de Detalle y Descuentos Unitarios**: Cada línea de venta especifica producto, cantidad editable, precio unitario, descuento fijo por unidad editable, precio unitario final y subtotal (`cantidad × (precio unitario - descuento por unidad)`).
- **Descuento General de Venta**: Monto fijo de descuento aplicable al total de la venta post-descuentos individuales, con desglose transparente de subtotales y descuentos acumulados.
- **Cobro Integrado en Pantalla (Sin Modal)**: La selección del método de pago (`EFECTIVO`, `QR`, `MIXTO`), ingreso de efectivo recibido, botones rápidos de billetes, cálculo automático de cambio y remanente QR se realizan directamente en la misma pantalla de ventas, sin recurrir a ventanas emergentes.
- **Pantalla POS Principal Desacoplada**: El POS conserva su propósito de control de caja: resumen financiero (KPIs), botón «Registrar venta» para navegar a la pantalla de venta, y listado de transacciones con opción de anulación justificada de ventas en cajas abiertas.
- **Anulación Controlada de Ventas**:
  - Restricción: Solo se permite anular ventas pertenecientes a una caja que permanezca actualmente `ABIERTA`.
  - Registro de auditoría: Estado `ANULADO`, motivo obligatorio de anulación, fecha/hora y usuario que anula (sin eliminación física).
  - Impacto en caja: Genera una transacción de tipo `ANULACIÓN` en la sesión de caja y descuenta los montos correspondientes del efectivo esperado y ventas registradas.
- **Consultas y Trazabilidad**: Visualización diferenciada en el listado de transacciones de la caja y consulta de ventas, identificando ventas originales y sus contrapartes de anulación.
- **Non-Goals (Fuera de Alcance)**:
  - No se incorpora selección ni gestión de clientes en esta fase.
  - No se altera el stock de inventario de productos (se mantiene desacoplado para una fase posterior de control de stock).

## Capabilities

### New Capabilities
- `sales-registration`: Registro comercial de ventas en pantalla dedicada `/pos/sale`, catálogo de productos rápidos con grupos y drag & drop persistido, líneas de venta con descuentos unitarios y globales, pasarela de pago inline sin modal (Efectivo con cálculo de cambio, QR, Mixto), y anulación auditada de ventas en cajas abiertas desde el POS.

### Modified Capabilities
- `cash-management`: Navegación fluida entre el POS y la nueva pantalla de registro de venta, incorporación de transacciones de tipo `ANULACIÓN` para reversión de balances en la caja activa, soporte de pagos mixtos (Efectivo + QR) en los acumuladores de sesión, y limpieza de componentes obsoletos.

## Impact

- **Base de Datos**: Migración Flyway `V7__create_sales_registration_tables.sql` para actualizar la tabla `sales` (estados, motivo anulación, usuario anulación, fecha anulación, desglose efectivo/QR/cambio/descuentos), crear `sale_items`, y tablas `quick_product_groups` y `quick_product_group_items`.
- **Backend (Spring Boot)**: Nuevas entidades JPA, repositorios, DTOs y servicios `SaleService` y `QuickProductService`, actualización de `CashSessionService` para registrar transacciones compuestas de venta y anulación.
- **Frontend (Angular 21)**: Nueva pantalla `RegisterSaleComponent` (`/pos/sale`), simplificación de `PosComponent` restableciendo el botón «Registrar venta» en la cabecera de transacciones para navegación, integración inline de cobro sin modal, y modal de anulación `VoidSaleModalComponent`.
