# Financial Management Specification

## Purpose

Permite la administración integral de las cuentas financieras del negocio, categorías de ingresos y gastos, transferencias entre cuentas, movimientos manuales, imputación automática de ventas, anulación auditada y visualización del estado económico mediante dashboards y trazabilidad histórica.

## Requirements

### Requirement: Cuentas financieras y saldos iniciales
El sistema SHALL permitir la administración de cuentas financieras iniciando con las cuentas «Caja», «Bisa» (cuenta QR) y «Efectivo» (FIN-01), asignando nombre, color identificador discreto y registrando su saldo inicial como un movimiento formal en el historial de la cuenta. El saldo de cada cuenta al día del corte de las planillas se carga una sola vez, antes de operar.

#### Scenario: Creación de nueva cuenta financiera
- **WHEN** el usuario registra una cuenta con nombre, color y saldo inicial de 1000.00 Bs
- **THEN** el sistema crea la cuenta con saldo actual de 1000.00 Bs, registra un movimiento de tipo `SALDO_INICIAL` por 1000.00 Bs y genera la auditoría de creación

#### Scenario: Saldo inicial con más de dos decimales (T-18)
- **WHEN** el usuario registra una cuenta con saldo inicial de 100.005 Bs
- **THEN** el sistema guarda la cuenta y su movimiento `SALDO_INICIAL` por 100.01 Bs (escala 2, `HALF_UP`)

#### Scenario: Cuentas iniciales (FIN-01)
- **WHEN** se aplica la migración en una base nueva
- **THEN** existen Caja, Bisa y Efectivo, cada una con su color y un movimiento `SALDO_INICIAL` por 0.00 Bs; Bisa es la cuenta QR si no había otra, y una cuenta que ya se llame así se reutiliza

#### Scenario: Carga del saldo al corte (FIN-01)
- **WHEN** el administrador carga en Bisa un saldo al corte de 31562.575 Bs y la cuenta no tiene otros movimientos
- **THEN** el movimiento `SALDO_INICIAL` pasa a 31562.58 Bs con descripción «Saldo al corte» y auditoría de modificación, y el saldo inicial y actual de Bisa quedan en 31562.58 Bs

#### Scenario: Saldo al corte de una cuenta que ya operó (FIN-01)
- **WHEN** se intenta cargar el saldo al corte en una cuenta con cualquier otro movimiento (activo o anulado), o en Caja después de abrir la primera caja
- **THEN** el sistema lo rechaza indicando que el saldo al corte solo se carga antes de operar y que se corrija con un ingreso, gasto o transferencia; la pantalla ya no ofrece «Saldo al corte»

#### Scenario: Primera caja después del corte (FIN-01, CAJ-10)
- **WHEN** se abre la primera caja del sistema con Caja en 7637.90 Bs
- **THEN** la apertura propone 7637.90 Bs como monto inicial

#### Scenario: Edición de nombre y color de cuenta
- **WHEN** el usuario actualiza el nombre o la selección de color de una cuenta existente
- **THEN** el sistema persiste los cambios visuales y registra los datos de usuario y fecha de modificación en la auditoría sin alterar los saldos ni movimientos

---

### Requirement: Cuenta Caja protegida
El sistema SHALL mantener la cuenta «Caja» como una cuenta especial y fija del negocio, impidiendo la creación de cuentas adicionales de tipo caja y bloqueando su eliminación física o lógica.

#### Scenario: Intento de eliminación de cuenta Caja
- **WHEN** un usuario intenta eliminar la cuenta «Caja»
- **THEN** el sistema bloquea la acción notificando que la cuenta Caja es única y no puede ser eliminada

#### Scenario: Bloqueo de ingresos manuales directos en Caja
- **WHEN** un usuario intenta registrar un ingreso manual seleccionando la cuenta «Caja»
- **THEN** el sistema impide seleccionar Caja para ingresos manuales, reservando sus ingresos exclusivamente a ventas del POS o transferencias

---

### Requirement: Configuración exclusiva de cuenta de pagos QR
El sistema SHALL disponer de una única cuenta activa configurada como «cuenta de pagos QR», impidiendo desactivar dicha configuración sin asignar previamente otra cuenta y transfiriendo automáticamente la marca QR al seleccionar una cuenta distinta.

#### Scenario: Asignación de nueva cuenta de pagos QR
- **WHEN** el usuario activa el toggle de cuenta QR en la cuenta «Bisa» teniendo previamente configurada la cuenta «Efectivo»
- **THEN** la cuenta «Bisa» pasa a ser la cuenta QR activa, la cuenta «Efectivo» pierde la condición de cuenta QR y el sistema garantiza que existe exactamente una cuenta QR

#### Scenario: Intento de desmarcar cuenta QR sin sustituto
- **WHEN** el usuario intenta desactivar directamente el indicador de cuenta QR en la cuenta actualmente configurada
- **THEN** el sistema bloquea el desmarcado indicando que siempre debe existir una cuenta configurada para cobros QR

---

### Requirement: Reglas de eliminación de cuentas
El sistema SHALL permitir la eliminación lógica (soft-delete auditado) de una cuenta creada por el usuario únicamente si su saldo actual es exactamente `0.00` Bs y no está configurada como la cuenta de pagos QR ni corresponde a la cuenta Caja.

#### Scenario: Eliminación exitosa de cuenta con saldo cero
- **WHEN** el usuario solicita eliminar una cuenta que no es Caja, no es QR y su saldo actual es 0.00 Bs
- **THEN** el sistema ejecuta la baja lógica registrando usuario y fecha de eliminación en la auditoría

#### Scenario: Rechazo de eliminación de cuenta con saldo distinto de cero
- **WHEN** el usuario intenta eliminar una cuenta cuyo saldo actual es mayor o menor a 0.00 Bs
- **THEN** el sistema rechaza la eliminación e informa que la cuenta debe quedar en saldo 0.00 Bs antes de poder eliminarse

#### Scenario: Rechazo de eliminación de cuenta configurada para QR
- **WHEN** el usuario intenta eliminar una cuenta con saldo 0.00 Bs que actualmente es la cuenta de pagos QR
- **THEN** el sistema rechaza la eliminación e instruye configurar otra cuenta como QR previamente

---

### Requirement: Categorías financieras y categoría de ventas
El sistema SHALL permitir administrar categorías clasificadas obligatoriamente como `INGRESO` o `GASTO`, disponiendo de una categoría de ingreso predeterminada «Ventas» configurada como la única «Categoría de ventas» activa del sistema.

#### Scenario: Creación de categoría de ingreso o gasto
- **WHEN** el usuario crea una categoría especificando nombre y tipo `GASTO`
- **THEN** el sistema persiste la categoría con su clasificación y auditoría de creación

#### Scenario: Selección exclusiva de categoría de ventas
- **WHEN** el usuario configura una categoría de tipo `INGRESO` como la nueva categoría de ventas
- **THEN** la categoría seleccionada se establece como la categoría de ventas activa y la categoría anterior pierde dicha condición

#### Scenario: Protección contra eliminación de categoría de ventas
- **WHEN** el usuario intenta eliminar la categoría actualmente designada como categoría de ventas
- **THEN** el sistema bloquea la eliminación notificando que debe reasignarse otra categoría de ventas antes de proceder

---

### Requirement: Registro y edición de ingresos manuales
El sistema SHALL permitir registrar ingresos manuales en cualquier cuenta distinta de Caja, especificando cuenta, monto, categoría de ingreso, descripción y comentario opcional. La fecha del movimiento la pone el servidor al registrarlo (T-18). Un ingreso registrado solo admite editar descripción, comentario y categoría; el monto y la cuenta no cambian (T-05).

#### Scenario: Registro exitoso de ingreso manual
- **WHEN** el usuario registra un ingreso manual de 500.00 Bs en la cuenta «Bisa» con categoría «Aportes»
- **THEN** el sistema crea la transacción en estado `ACTIVO` con la fecha/hora actual del servidor, incrementa el saldo de la cuenta en 500.00 Bs y registra el usuario como auditoría de registro

#### Scenario: Monto con más de dos decimales (T-18)
- **WHEN** el usuario registra un ingreso, gasto o transferencia con un monto de 10.555 Bs
- **THEN** el sistema lo guarda como 10.56 Bs (escala 2, `HALF_UP`); un monto que redondea a 0.00 Bs se rechaza

#### Scenario: Edición de ingreso manual (T-05)
- **WHEN** el usuario modifica la descripción, el comentario o la categoría de un ingreso manual existente
- **THEN** el sistema actualiza esos datos sin tocar el saldo de la cuenta, valida que la categoría sea de tipo INGRESO, conserva el monto, la cuenta, la fecha original y la auditoría de registro, y registra usuario y fecha de modificación

#### Scenario: Corrección de monto o cuenta de un ingreso (T-05)
- **WHEN** el pedido de edición de un ingreso cambia el monto o la cuenta
- **THEN** el sistema lo rechaza indicando que el error se corrige anulando el movimiento y registrándolo de nuevo, sin tocar saldos

---

### Requirement: Registro de gastos manuales y gastos de caja
El sistema SHALL permitir registrar gastos manuales en cualquier cuenta (incluida Caja), y proveer un acceso específico desde la pantalla de Caja con la cuenta Caja preseleccionada y bloqueada. Un gasto registrado solo admite editar descripción, comentario y categoría (T-05).

#### Scenario: Registro de gasto en cuenta bancaria
- **WHEN** el usuario registra un gasto de 200.00 Bs en la cuenta «Efectivo» con categoría «Servicios»
- **THEN** el sistema deduce 200.00 Bs del saldo de la cuenta y persiste la transacción de gasto con su auditoría

#### Scenario: Registro de gasto directo desde terminal de Caja
- **WHEN** el usuario ejecuta «Registrar gasto de caja» desde la interfaz de Caja
- **THEN** el sistema abre el formulario de gasto con la cuenta «Caja» fijada sin posibilidad de cambio y registra la transacción afectando el efectivo de caja

#### Scenario: Edición de gasto manual (T-05)
- **WHEN** el usuario modifica la descripción, el comentario o la categoría de un gasto manual existente, incluido un gasto de la caja abierta
- **THEN** el sistema actualiza esos datos sin tocar el saldo de la cuenta ni el efectivo esperado de la caja, valida que la categoría sea de tipo GASTO y conserva monto, cuenta, caja y fecha original

#### Scenario: Corrección de monto o cuenta de un gasto (T-05)
- **WHEN** el pedido de edición de un gasto cambia el monto o la cuenta
- **THEN** el sistema lo rechaza indicando que el error se corrige anulando el movimiento y registrándolo de nuevo; un gasto de una caja cerrada tampoco se edita (T-16)

---

### Requirement: Transferencias entre cuentas
El sistema SHALL permitir registrar transferencias entre dos cuentas diferentes verificando que la cuenta origen cuente con saldo actual suficiente para cubrir el monto solicitado.

#### Scenario: Transferencia exitosa entre cuentas
- **WHEN** el usuario transfiere 300.00 Bs de «Bisa» (saldo 1000.00 Bs) a «Efectivo» (saldo 200.00 Bs)
- **THEN** el sistema disminuye el saldo de Bisa a 700.00 Bs, incrementa el saldo de Efectivo a 500.00 Bs y registra la transacción de transferencia vinculando origen y destino

#### Scenario: Rechazo de transferencia con saldo insuficiente
- **WHEN** el usuario intenta transferir un monto mayor al saldo actual disponible en la cuenta origen
- **THEN** el sistema bloquea la operación indicando saldo insuficiente en la cuenta origen

#### Scenario: Transferencia desde o hacia Caja
- **WHEN** el usuario transfiere desde o hacia la cuenta «Caja»
- **THEN** el sistema exige una caja abierta, vincula la transferencia a ella y la incluye en el efectivo esperado (CAJ-08, T-15, T-16)

---

### Requirement: Anulación auditada de transacciones financieras
El sistema SHALL permitir anular ingresos manuales, gastos y transferencias sin eliminación física, revirtiendo el impacto económico en los saldos de las cuentas y registrando usuario, fecha/hora y motivo obligatorio de anulación (RG-03). El saldo inicial de una cuenta no se anula ni se edita (T-18).

#### Scenario: Anulación de gasto manual
- **WHEN** el usuario anula un gasto de 150.00 Bs registrando el motivo «Factura cancelada por duplicidad»
- **THEN** el sistema cambia el estado de la transacción a `ANULADO`, repone los 150.00 Bs al saldo de la cuenta, conserva la fecha original de la transacción y guarda el usuario, timestamp y motivo en la auditoría

#### Scenario: Anulación de transferencia entre cuentas
- **WHEN** el usuario anula una transferencia de 300.00 Bs
- **THEN** el sistema revierte automáticamente los saldos (incrementa origen y disminuye destino) y marca la transferencia como `ANULADO`

#### Scenario: Movimiento de una caja cerrada
- **WHEN** el usuario intenta editar o anular un gasto o transferencia vinculado a una caja ya cerrada
- **THEN** el sistema lo rechaza indicando que se corrija en la caja actual (CAJ-06, T-16)

#### Scenario: Anulación sin motivo (T-18)
- **WHEN** el usuario intenta anular un movimiento desde Finanzas, o un gasto de caja desde el POS, sin indicar motivo
- **THEN** el sistema rechaza la anulación indicando que el motivo es obligatorio y no toca saldos

#### Scenario: Anulación del saldo inicial (T-18)
- **WHEN** el usuario intenta anular o editar el movimiento `SALDO_INICIAL` de una cuenta desde Transacciones
- **THEN** el sistema lo rechaza indicando que el saldo inicial no se puede anular ni editar; la única vía es el saldo al corte antes de operar (FIN-01)

---

### Requirement: Protección de transacciones originadas por ventas
El sistema SHALL proteger los movimientos financieros generados automáticamente a partir de ventas del POS, impidiendo su edición o anulación desde el módulo de Finanzas y permitiendo abrir directamente el detalle de la venta.

#### Scenario: Intento de modificar transacción proveniente de venta
- **WHEN** el usuario visualiza un ingreso financiero originado por una venta del POS
- **THEN** el sistema no presenta opciones de edición ni de anulación manual, mostrando una acción para ver el detalle de la venta mediante el modal estándar de detalle de venta

---

### Requirement: Filtros y consulta del historial de transacciones
El sistema SHALL proveer una vista de transacciones financieras con filtros por rango de fechas (por defecto desde el primer día del mes en curso a las 00:00:00 hasta la fecha actual a las 23:59:59), tipo de movimiento, cuenta, categoría, estado (`TODOS`, `ACTIVOS`, `ANULADOS`) y buscador textual por descripción, distinguiendo visualmente las cuentas mediante su color configurado.

#### Scenario: Consulta por rango por defecto
- **WHEN** el usuario ingresa a Finanzas → Transacciones sin aplicar filtros manuales
- **THEN** el sistema presenta los movimientos del mes actual ordenados cronológicamente, indicando para cada transacción su fecha económica original, tipo, cuenta con su color distintivo, categoría, monto y badge de estado

---

### Requirement: Resumen financiero y saldos consolidados
El sistema SHALL presentar en Finanzas → Resumen una vista ejecutiva con el saldo actual de Caja, Bisa, Efectivo, saldo total consolidado de todas las cuentas, indicador de la cuenta QR activa y los movimientos recientes del negocio.

#### Scenario: Visualización del panel de resumen
- **WHEN** el usuario accede a Finanzas → Resumen
- **THEN** el sistema exhibe tarjetas de saldo para cada cuenta principal con su color, el saldo consolidado total y una lista de los últimos movimientos registrados
