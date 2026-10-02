# Proposal

## Why

El POS actual solo cuenta con un registro preliminar de ventas basado en un monto manual y una descripción libre. Para operar comercialmente en el punto de venta, se requiere un flujo integral de registro de ventas que permita seleccionar productos del catálogo, organizar productos rápidos frecuentes en grupos configurables con drag-and-drop, aplicar descuentos unitarios y globales, procesar múltiples modalidades de pago (Efectivo con cambio, QR y Mixto Efectivo+QR), y anular ventas justificadamente con impacto directo en los balances de la sesión de caja compartida.

## What Changes

- **Buscador y Selección de Productos**: Búsqueda ágil de productos activos. Al seleccionar un producto se agrega al detalle de venta con cantidad `1`; si ya existe en la venta, incrementa automáticamente su cantidad en `1`.
- **Productos Rápidos con Drag & Drop**: Panel configurable de accesos directos organizado en grupos de productos. Modal de configuración desde el POS para crear/editar grupos, asignar productos, reordenar grupos y reordenar productos dentro de cada grupo mediante drag-and-drop, persistido en base de datos.
- **Líneas de Detalle y Descuentos Unitarios**: Cada línea de venta especifica producto, cantidad editable, precio unitario, descuento fijo por unidad editable, precio unitario final y subtotal (`cantidad × (precio unitario - descuento por unidad)`).
- **Descuento General de Venta**: Monto fijo de descuento aplicable al total de la venta post-descuentos individuales, con desglose transparente de subtotales y descuentos acumulados.
- **Métodos de Pago Flexibles**:
  - `EFECTIVO`: Requiere ingresar monto recibido (mínimo igual al total) y calcula automáticamente el cambio.
  - `QR`: Asigna el total exacto de la venta sin ingreso manual de monto ni cálculo de cambio.
  - `MIXTO`: El usuario ingresa el monto pagado en efectivo; el sistema calcula automáticamente la diferencia correspondiente a QR y determina el cambio si el efectivo entregado supera la porción asignada a efectivo.
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
- `sales-registration`: Registro comercial de ventas en el POS, catálogo de productos rápidos con grupos y drag & drop persistido, líneas de venta con descuentos unitarios y globales, pasarela de pago (Efectivo, QR, Mixto con cálculo de cambio), y anulación auditada de ventas en cajas abiertas.

### Modified Capabilities
- `cash-management`: Incorporación de transacciones de tipo `ANULACIÓN` para reversión de balances en la caja activa, soporte de pagos mixtos (Efectivo + QR) en los acumuladores de sesión, y reemplazo del modal de venta simple por el terminal de ventas completo.

## Impact

- **Base de Datos**: Migración Flyway `V7__create_sales_registration_tables.sql` para actualizar la tabla `sales` (estados, motivo anulación, usuario anulación, fecha anulación, desglose efectivo/QR/cambio/descuentos), crear `sale_items`, y tablas `quick_product_groups` y `quick_product_group_items`.
- **Backend (Spring Boot)**: Nuevas entidades JPA, repositorios, DTOs y servicios `SaleService` y `QuickProductService`, actualización de `CashSessionService` para registrar transacciones compuestas de venta y anulación.
- **Frontend (Angular 21)**: Actualización de `PosComponent` con layout de terminal POS (panel de catálogo/productos rápidos y panel de carrito/checkout), instalación y uso de `@angular/cdk/drag-drop` para la reordenación visual, modal de configuración de productos rápidos, modal de anulación con motivo obligatorio, y actualización reactiva de KPIs de caja.
