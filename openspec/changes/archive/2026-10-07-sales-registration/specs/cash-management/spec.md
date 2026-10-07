# Spec Delta: Cash Management

## MODIFIED Requirements

### Requirement: Vinculación obligatoria de ventas a la caja activa
El sistema SHALL vincular cualquier venta registrada a la sesión de caja actualmente abierta, conservando la referencia al usuario que ejecutó la transacción, actualizando los saldos de efectivo y QR según el método de pago (incluyendo pagos mixtos), y permitiendo generar transacciones de tipo `ANULACIÓN` que revierten los saldos de la sesión abierta. No se permitirá registrar ventas si no hay una caja abierta.

#### Scenario: Registro de venta con caja abierta
- **WHEN** un usuario autorizado registra una venta por 45.00 Bs mientras la caja está `ABIERTA`
- **THEN** la venta se asocia automáticamente a la sesión de caja activa y se registra con el identificador del usuario que realizó la venta

#### Scenario: Rechazo de venta sin caja abierta
- **WHEN** un usuario intenta registrar una venta y no existe ninguna sesión de caja en estado `ABIERTA`
- **THEN** el sistema deniega el registro de la venta indicando que es obligatorio abrir una caja previamente

#### Scenario: Venta con pago mixto impactando saldos de efectivo y QR
- **WHEN** se registra una venta de 100.00 Bs con método `MIXTO` desglosado en 40.00 Bs efectivo y 60.00 Bs QR
- **THEN** la sesión de caja activa incrementa sus ventas en efectivo por 40.00 Bs, sus ventas en QR por 60.00 Bs, y su efectivo esperado en 40.00 Bs

#### Scenario: Transacción de anulación revirtiendo saldos de la caja activa
- **WHEN** se anula una venta perteneciente a la caja abierta que contenía 40.00 Bs en efectivo y 60.00 Bs en QR
- **THEN** la sesión de caja registra una transacción de tipo `ANULACIÓN`, deduce 40.00 Bs del saldo de ventas en efectivo, 60.00 Bs de las ventas en QR, y actualiza el efectivo esperado consecuentemente
