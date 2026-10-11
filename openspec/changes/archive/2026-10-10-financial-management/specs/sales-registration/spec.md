# Spec Delta: sales-registration

## MODIFIED Requirements

### Requirement: Modalidades de pago con cálculo automático inline en pantalla
El sistema SHALL procesar el cobro de la venta mediante el modal ergonómico de cobro bajo las modalidades de `EFECTIVO`, `QR` o `MIXTO` (efectivo + QR), validando los montos ingresados, calculando el cambio según corresponda y generando automáticamente las transacciones financieras de ingreso asociadas bajo la categoría de ventas activa.

#### Scenario: Pago exclusivo en efectivo con cálculo de cambio
- **WHEN** la venta totaliza 85.00 Bs, el método seleccionado es `EFECTIVO` y el usuario ingresa 100.00 Bs recibidos
- **THEN** el sistema calcula automáticamente un cambio de 15.00 Bs, habilita la confirmación de la venta y genera automáticamente un movimiento financiero de tipo `INGRESO` por 85.00 Bs en la cuenta «Caja» bajo la categoría configurada como categoría de ventas

#### Scenario: Rechazo de pago en efectivo insuficiente
- **WHEN** el método es `EFECTIVO` y el monto recibido ingresado es menor al total de la venta
- **THEN** el sistema bloquea la confirmación e indica que el monto recibido en efectivo no puede ser menor al total

#### Scenario: Pago exclusivo mediante QR
- **WHEN** el método seleccionado es `QR` para una venta de 85.00 Bs
- **THEN** el sistema fija el monto QR en exactamente 85.00 Bs, deshabilita el ingreso de monto recibido, establece el cambio en 0.00 Bs y registra automáticamente un ingreso financiero por 85.00 Bs en la cuenta configurada como cuenta de pagos QR

#### Scenario: Pago mixto con efectivo y QR
- **WHEN** una venta de 100.00 Bs se paga con método `MIXTO` y el usuario ingresa 40.00 Bs en efectivo
- **THEN** el sistema calcula automáticamente que el monto por QR es 60.00 Bs, procesa la transacción desglosando ambos importes y genera dos movimientos financieros de ingreso: 40.00 Bs en la cuenta «Caja» y 60.00 Bs en la cuenta configurada como pagos QR, ambos bajo la categoría de ventas

---

### Requirement: Anulación auditada de ventas en caja abierta
El sistema SHALL permitir la anulación justificada de una venta únicamente si la sesión de caja compartida a la que pertenece se encuentra actualmente en estado `ABIERTA`, requiriendo obligatoriamente el motivo de anulación y anulando automáticamente las transacciones financieras generadas por dicha venta.

#### Scenario: Anulación exitosa de venta en caja abierta
- **WHEN** un usuario solicita anular una venta de la sesión activa ingresando el motivo obligatorio "Error en digitación de productos"
- **THEN** el sistema cambia el estado de la venta a `ANULADO`, registra la fecha/hora actual, el usuario que anula, el motivo, genera la transacción de tipo `ANULACIÓN` en la caja, revierte los montos en los saldos de caja y anula automáticamente los movimientos financieros generados por la venta restituyendo los saldos de las cuentas involucradas con el mismo motivo y usuario de anulación

#### Scenario: Rechazo estricto de anulación en caja cerrada
- **WHEN** se intenta solicitar la anulación de una venta cuya sesión de caja ya fue cerrada
- **THEN** el sistema rechaza la operación indicando que no se permite anular ventas de cajas cerradas
