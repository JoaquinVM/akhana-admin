# Design: POS — Detalle de venta y acciones del listado

## Context

En el sistema actual, la tabla de transacciones de ventas en la pantalla de POS (`frontend/src/app/pages/pos/pos.component.html`) presenta una columna de acciones que solo incluye el botón de anulación con estilos particulares (`.btn-void-action`). Por otra parte, las tablas de Productos y Proveedores emplean el patrón canónico `.table-actions` con botones `.btn-icon` para acciones de fila (visualización y edición/eliminación).
Adicionalmente, no existe un visor detallado de venta, lo que impide consultar el desglose de productos, descuentos aplicados (unitarios y acumulados), métodos de pago y datos de auditoría de anulación sin modificar los registros. Este visor debe ser un componente independiente y reutilizable para operar tanto en el POS como en el futuro Historial de ventas.

## Goals / Non-Goals

**Goals:**
- Crear un componente desacoplado y reutilizable `SaleDetailModalComponent` en `frontend/src/app/shared/components/sale-detail-modal/`.
- Presentar en el modal: N.º de venta, fecha/hora, usuario, sesión (`Sesión #X`), estado (`Completada` / `Anulada`), tabla completa de productos con descuentos detallados, resumen financiero de descuentos acumulados, desglose de métodos de pago y sección de auditoría de anulación cuando aplique.
- Estandarizar la columna de acciones en la tabla de ventas del POS usando `.table-actions` y botones `.btn-icon` consistentes con Productos y Proveedores.
- Ordenar las acciones de cada fila: 1. "Ver detalle" (inspección) y 2. "Anular" (manteniendo intacto su flujo y validaciones existentes).
- Enriquecer `SaleResponse` en el backend con `sessionNumber` extraído de `CashSession` para soportar la visualización `Sesión #X`.

**Non-Goals:**
- No se permiten modificaciones ni anulaciones desde el interior del modal de detalle (modo estrictamente de sólo lectura).
- No se altera la lógica, validaciones ni reglas de negocio del flujo de anulación existente.
- No se crea una pantalla nueva de Historial de Ventas en este requerimiento (se deja el modal listo y documentado para su consumo directo posterior).

## Decisions

### 1. Ubicación y desacoplamiento del componente `SaleDetailModalComponent`
- **Decisión:** Implementar el modal como componente standalone en `frontend/src/app/shared/components/sale-detail-modal/`.
- **Razón:** El requerimiento estipula que el modal debe ser el único componente oficial para la visualización del detalle de una venta tanto en POS como en el futuro Historial de ventas, sin duplicación de código ni lógica.
- **Alternativa descartada:** Crear un modal interno embebido en `pos.component.ts`. Se descarta porque violaría el desacoplamiento y obligaría a duplicar o refactorizar código cuando se construya el Historial de ventas.

### 2. Carga bajo demanda directa desde el backend (On-Demand Fetch)
- **Decisión:** Cada vez que el usuario hace clic en "Ver detalle", el sistema llama directamente al backend (`GET /api/sales/{id}`) a través de `SaleService.getSaleById(id)`. No se conservan en memoria los detalles de todas las ventas; únicamente se mantiene en memoria la venta que se está consultando en ese momento.
- **Razón:** Optimiza el consumo de memoria en el cliente, asegura datos siempre actualizados (por ejemplo, si otra terminal o proceso alteró o anuló la transacción) y desacopla al 100% el componente modal de cualquier estructura previa de la tabla que lo invoque (sea POS o Historial de ventas). El modal mostrará un estado de carga (skeleton/spinner) mientras se obtiene la información.
- **Alternativa descartada:** Reutilizar indiscriminadamente los datos cacheados en memoria de la lista de transacciones. Se descarta según la indicación del usuario para no saturar memoria con detalles completos de cada venta del listado.

### 3. Exposición de `sessionNumber` en `SaleResponse`
- **Decisión:** Incorporar el campo `Long sessionNumber` en `SaleResponse.java` (backend) y `sessionNumber?: number` en `Sale` (frontend).
- **Razón:** El requerimiento exige mostrar `Sesión #X` en la información general del modal. Actualmente `Sale` tiene `cashSessionId: UUID`, pero `CashSession` posee el número correlativo de sesión `sessionNumber` (ej. 1, 2, 5). Poblándolo en `SaleResponse.fromEntity(...)` mediante `sale.getCashSession() != null ? sale.getCashSession().getSessionNumber() : null`, el frontend recibe directamente el número sin consultas adicionales.
- **Alternativa descartada:** Concatenar strings en el backend o buscar la sesión activa en el frontend. Se descarta porque una venta histórica puede pertenecer a una sesión anterior que no es la activa.

### 4. Estandarización visual de la columna de acciones en la tabla POS
- **Decisión:** Sustituir la clase `.btn-void-action` por la estructura `<div class="table-actions" style="justify-content: flex-end">` conteniendo:
  1. `<button class="btn-icon" (click)="openSaleDetail(sale)" title="Ver detalle">` con icono SVG de ojo / detalle.
  2. `<button class="btn-icon btn-delete" (click)="confirmVoidSale(sale)" [disabled]="isSaleVoided(sale) || !isSessionOpen()" title="Anular venta">` con icono SVG de anulación / cancelación.
- **Razón:** Satisface estrictamente la directriz de identidad visual del sistema, imitando las tablas de Productos y Proveedores sin introducir lenguajes visuales disidentes.

## Risks / Trade-offs

- **[Riesgo: Sesión de caja nula en ventas antiguas de migración]** → Mitigación: Uso de `sale.sessionNumber ?? '-'` para evitar caídas o cadenas vacías si alguna entidad histórica carece de sesión vinculada.
- **[Riesgo: Descuentos unitarios en cero o sin porcentaje aplicable]** → Mitigación: Si `unitDiscount` es cero, mostrar `Bs 0,00`; si es mayor a cero, calcular el porcentaje relativo `(unitDiscount / unitPrice) * 100` formateado a 0 o 2 decimales según corresponda.
