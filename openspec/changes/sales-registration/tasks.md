# Tasks

## 1. Base de Datos y Persistencia

- [x] 1.1 Crear migración Flyway `V7__create_sales_registration_tables.sql` con extensión de `sales` (estados, subtotales, descuentos, desglose de pagos, anulación), tabla `sale_items`, y tablas `quick_product_groups` y `quick_product_group_items`. Verificar ejecución exitosa con `./gradlew flywayInfo` o arranque de Spring Boot.
- [x] 1.2 Actualizar el esquema canónico `database/schemas/schema.sql` incorporando las nuevas tablas, columnas e índices.

## 2. Backend - Modelos, Repositorios y DTOs

- [x] 2.1 Actualizar entidad `Sale`, incorporar enum `SaleStatus` (`COMPLETADA`, `ANULADA`), actualizar `PaymentMethod` (`EFECTIVO`, `QR`, `MIXTO`) y crear entidad `SaleItem`. Verificar compilación con `./gradlew compileJava`.
- [x] 2.2 Crear entidades `QuickProductGroup` y `QuickProductGroupItem` junto a sus repositorios `QuickProductGroupRepository` y `QuickProductGroupItemRepository`. Verificar compilación con `./gradlew compileJava`.
- [x] 2.3 Crear DTOs para venta detallada: `SaleDetailRequest`, `SaleItemRequest`, `SaleDetailResponse`, `SaleItemResponse` y `VoidSaleRequest`.
- [x] 2.4 Crear DTOs para productos rápidos: `QuickProductGroupRequest`, `QuickProductGroupResponse`, `ReorderGroupsRequest` y `ReorderItemsRequest`.

## 3. Backend - Servicios y Lógica Transaccional

- [x] 3.1 Implementar métodos en `SaleService` y `CashSessionService` para registrar ventas detalladas asociadas a la caja activa, calcular precios finales con descuento por unidad y descuento general.
- [x] 3.2 Implementar en `SaleService` la lógica de cobro por `EFECTIVO` (validando monto recibido y calculando cambio), `QR` y `MIXTO` (calculando remanente QR y cambio sobre efectivo), impactando los saldos de la sesión de caja abierta.
- [x] 3.3 Implementar en `SaleService` la anulación justificada de ventas: validar que la sesión de caja esté `ABIERTA`, registrar motivo obligatorio, fecha y usuario que anula, cambiar estado a `ANULADA` y revertir montos en los totales de caja.
- [x] 3.4 Implementar `QuickProductService` con operaciones para listar, crear, actualizar, eliminar grupos de productos rápidos y persistir el nuevo orden (`display_order`) resultante del drag-and-drop.
- [x] 3.5 Crear pruebas unitarias en `SaleServiceTest` y `QuickProductServiceTest` cubriendo registro de venta, cálculos de descuentos, cobro mixto, anulación en caja abierta y rechazo en caja cerrada. Verificar con `./gradlew test`.

## 4. Backend - Controladores REST y Pruebas de Integración

- [x] 4.1 Implementar `SaleController` exponiendo endpoints protegidos `POST /api/sales` (registro), `POST /api/sales/{id}/void` (anulación), `GET /api/sales` y `GET /api/sales/{id}`.
- [x] 4.2 Implementar `QuickProductController` exponiendo endpoints para administración de grupos y persistencia de reordenamiento drag-and-drop.
- [x] 4.3 Actualizar `CashSessionController` para retornar transacciones enriquecidas con estado de venta y desglose de método de pago.
- [x] 4.4 Desarrollar pruebas de integración en `SaleControllerTest` y `QuickProductControllerTest`. Verificar con `./gradlew test`.

## 5. Frontend - Dependencias, Modelos y Servicios

- [x] 5.1 Instalar dependencias necesarias para drag-and-drop (`@angular/cdk`) en `frontend/` y verificar instalación limpia.
- [x] 5.2 Definir interfaces y modelos TypeScript para ventas (`Sale`, `SaleItem`, `SaleRequest`, `VoidSaleRequest`) y productos rápidos (`QuickProductGroup`, `QuickProductItem`).
- [x] 5.3 Implementar servicios Angular `SaleService` y `QuickProductService` gestionando estado reactivo mediante Angular Signals.
- [x] 5.4 Escribir pruebas unitarias para `SaleService` y `QuickProductService`. Verificar con `npm test`.

## 6. Frontend - Catálogo POS, Buscador y Carrito

- [x] 6.1 Implementar buscador predictivo de productos en `PosComponent` con autocompletado y selección que añade el ítem al carrito o incrementa la cantidad en `1` si ya existe.
- [x] 6.2 Implementar tabla interactiva del carrito en `PosComponent`: edición directa de cantidad, input de descuento por unidad (monto fijo), precio unitario final y subtotal por ítem.
- [x] 6.3 Implementar resumen financiero del carrito: subtotal sin descuentos, total descuentos por ítem, input de descuento general de la venta, total descuentos acumulados y total neto a pagar.

## 7. Frontend - Productos Rápidos y Modal de Configuración Drag & Drop

- [x] 7.1 Implementar visualización de grupos y tarjetas de productos rápidos en el POS con adición al carrito en un clic.
- [x] 7.2 Implementar modal `QuickProductsConfigModalComponent` permitiendo crear grupos, asignar productos activos y reordenar visualmente grupos y productos mediante `@angular/cdk/drag-drop`.
- [x] 7.3 Conectar la persistencia del reordenamiento drag-and-drop hacia los endpoints de backend.

## 8. Frontend - Modal de Cobro (Checkout) y Métodos de Pago

- [x] 8.1 Implementar modal `CheckoutModalComponent` en el POS con pestañas para `Efectivo`, `QR` y `Mixto`.
- [x] 8.2 Implementar lógica de `Efectivo`: input de monto recibido, validación de monto suficiente y cálculo reactivo de cambio.
- [x] 8.3 Implementar lógica de `QR`: confirmación directa con monto fijado igual al total de venta y sin cambio.
- [x] 8.4 Implementar lógica de `Mixto`: input de efectivo recibido, cálculo automático de saldo QR restante y cálculo de cambio sobre el efectivo entregado.

## 9. Frontend - Anulación de Ventas y Trazabilidad en POS

- [x] 9.1 Actualizar la tabla de transacciones de la sesión de caja en `PosComponent` para mostrar distintivos de estado (`COMPLETADA` / `ANULADA`), método de pago desglosado y botón "Anular" visible solo si la caja está abierta.
- [x] 9.2 Implementar modal `VoidSaleModalComponent` con validación de motivo obligatorio (mínimo 5 caracteres) antes de confirmar.
- [x] 9.3 Conectar la anulación con el backend y actualizar reactivamente las transacciones del turno y los KPIs financieros de la caja abierta.

## 10. Verificación Integral, Documentación y Cierre de Planificación

- [x] 10.1 Ejecutar batería completa de pruebas backend con `./gradlew test` (0 fallos).
- [x] 10.2 Ejecutar suite completa de pruebas frontend con `npm test -- --watch=false` (0 fallos).
- [x] 10.3 Ejecutar compilación de producción en frontend con `npm run build` verificando cero errores.
- [x] 10.4 Actualizar documentación de contexto en `backend/.../_context.md` y `frontend/_context.md`.
