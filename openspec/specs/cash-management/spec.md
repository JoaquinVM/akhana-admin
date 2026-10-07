# Cash Management Specification

## Purpose

Permite la administración integral de sesiones de caja compartida en el punto de venta, controlando aperturas con monto inicial, registro concurrente de ventas asociadas a la sesión activa, arqueo por denominaciones monetarias (cortes de billetes y monedas en caja y reserva), cálculo de diferencias frente al efectivo esperado y consulta detallada del historial de cajas.

## Requirements

### Requirement: Regla de sesión única de caja abierta
El sistema SHALL permitir únicamente una sesión de caja con estado `ABIERTA` de forma simultánea en toda la aplicación.

#### Scenario: Intento de apertura cuando ya existe una caja abierta
- **WHEN** un usuario intenta abrir una nueva caja y ya existe una sesión en estado `ABIERTA`
- **THEN** el sistema rechaza la solicitud con un mensaje de error indicando que ya existe una caja activa compartida

### Requirement: Apertura de caja con monto inicial y comentario opcional
El sistema SHALL registrar la apertura de una nueva sesión de caja requiriendo obligatoriamente un monto inicial mayor o igual a cero y permitiendo opcionalmente un comentario de apertura.

#### Scenario: Apertura exitosa con datos válidos
- **WHEN** el usuario ingresa un monto inicial de 150.00 Bs y confirma la apertura
- **THEN** el sistema registra la sesión con estado `ABIERTA`, fecha y hora actual, el usuario autenticado como responsable de apertura, y la almacena en el sistema

#### Scenario: Apertura con comentario de apertura
- **WHEN** el usuario ingresa monto inicial y añade un comentario "Inicio de turno de fin de semana con sencillo"
- **THEN** el sistema almacena el comentario asociado a la información de apertura

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

### Requirement: Pantalla unificada POS y gestión de caja
El sistema SHALL consolidar el terminal de punto de venta (POS) y la gestión operativa de caja en una única pantalla denominada "POS" (`/pos`), adaptando su contenido al estado de la caja compartida y sin pantallas independientes para la caja actual.

#### Scenario: Visualización de POS con caja cerrada
- **WHEN** el usuario accede a la pantalla "POS" y la caja compartida está cerrada
- **THEN** el sistema muestra el estado de caja cerrada, presenta el botón para abrir nueva caja (solicitando monto inicial obligatorio y comentario opcional), y bloquea/no permite el registro de ventas

#### Scenario: Visualización de POS con caja abierta
- **WHEN** el usuario accede a la pantalla "POS" y la caja compartida está abierta
- **THEN** el sistema muestra conjuntamente la interfaz de registro de ventas, el resumen financiero de la caja actual (monto inicial, ventas efectivo, ventas QR, efectivo esperado), las transacciones del turno, los datos de apertura y la opción de cerrar la caja

#### Scenario: Registro de ventas dentro del POS
- **WHEN** un usuario registra una venta desde la pantalla POS abierta con método `EFECTIVO` o `QR`
- **THEN** la venta se asocia automáticamente a la sesión de caja activa, se agrega a la lista de transacciones del turno y actualiza los indicadores financieros en tiempo real

#### Scenario: Restricción estricta de navegación hacia el historial
- **WHEN** el usuario se encuentra en la pantalla POS (sea con caja abierta o cerrada)
- **THEN** la pantalla no muestra ningún botón, enlace ni acceso directo hacia la pantalla de historial de cajas

### Requirement: Cierre de caja con monto obligatorio y comentario opcional
El sistema SHALL permitir el cierre de la sesión de caja activa requiriendo obligatoriamente el monto de cierre y permitiendo opcionalmente un comentario de cierre.

#### Scenario: Cierre exitoso de sesión
- **WHEN** el usuario proporciona un monto de cierre y confirma la acción
- **THEN** el sistema actualiza el estado de la sesión a `CERRADA`, guarda la fecha y hora de cierre, el usuario que ejecutó el cierre, el monto de cierre, y bloquea el registro de nuevas ventas sobre dicha sesión

#### Scenario: Inmutabilidad de caja cerrada
- **WHEN** una caja pasa a estado `CERRADA`
- **THEN** el sistema no permite reabrir la sesión ni asociarle nuevas transacciones bajo ninguna circunstancia

### Requirement: Conteo opcional mediante cortes de denominaciones
El sistema SHALL permitir calcular el monto de cierre a través de una matriz de cortes de billetes y monedas bolivianas (Bs 200, 100, 50, 20, 10, 5, 2, 1, 0.50, 0.20, 0.10), desglosando cantidades en "Caja" y "Reserva".

#### Scenario: Cálculo automático por denominación
- **WHEN** el usuario registra en la denominación Bs 10 una cantidad de 10 en "Caja" y 40 en "Reserva"
- **THEN** el sistema calcula automáticamente `(10 + 40) × 10 = 500.00 Bs` para esa denominación y suma su contribución al total de cortes

#### Scenario: Traslado de total de cortes al monto de cierre
- **WHEN** el usuario utiliza la herramienta de cortes y selecciona aplicar el total
- **THEN** el monto calculado se asigna automáticamente al campo de monto de cierre y los cortes registrados se guardan para su posterior auditoría

#### Scenario: Ingreso directo de monto de cierre sin cortes
- **WHEN** el usuario ingresa manualmente el monto de cierre sin llenar la tabla de cortes
- **THEN** el sistema acepta el monto de cierre directo sin exigir el desglose de denominaciones

### Requirement: Cálculo y visualización de diferencia de caja
El sistema SHALL calcular y mostrar automáticamente la diferencia entre el monto de cierre y el monto esperado en efectivo antes de confirmar el cierre de la caja.

#### Scenario: Presentación de balance con diferencia
- **WHEN** el usuario se encuentra en el diálogo de confirmación de cierre
- **THEN** el sistema muestra el monto esperado en efectivo, el monto de cierre y la diferencia calculada (`monto de cierre - monto esperado en efectivo`), permitiendo cerrar la caja aún si existe diferencia (positiva o negativa)

### Requirement: Historial y consulta detallada en pantalla "Cajas"
El sistema SHALL listar el historial cronológico de sesiones de caja y permitir consultar el detalle integral de cada sesión seleccionada.

#### Scenario: Listado de historial de cajas
- **WHEN** el usuario navega a la sección "Cajas"
- **THEN** el sistema presenta la tabla de sesiones mostrando identificador, fechas/horas de apertura y cierre, usuarios de apertura y cierre, monto inicial, monto de cierre, diferencia y estado

#### Scenario: Consulta de detalle de una sesión cerrada
- **WHEN** el usuario selecciona una caja del historial
- **THEN** el sistema muestra información de apertura (usuario, fecha, monto inicial, comentario si existe), ventas asociadas por método de pago (`EFECTIVO` y `QR`), información de cierre (usuario, fecha, monto de cierre, comentario si existe, diferencia) y el desglose de cortes de denominación si fueron utilizados

### Requirement: Navegación y estado de caja en el menú principal
El sistema SHALL mantener la opción principal "POS" con una etiqueta reactiva de estado (`Abierta` o `Cerrada`) correspondiente al estado real de la sesión compartida, e integrar la opción "Historial de cajas" dentro del menú desplegable "Ventas".

#### Scenario: Visualización de estado en opción POS
- **WHEN** un usuario autenticado visualiza la opción POS en la navegación
- **THEN** el sistema muestra una etiqueta de estado ("Abierta" en verde cuando hay sesión activa o "Cerrada" en gris/rojo cuando no hay sesión abierta)

#### Scenario: Acceso al historial desde el menú Ventas
- **WHEN** el usuario despliega el menú "Ventas" en la barra de navegación
- **THEN** se muestra la opción "Historial de cajas" permitiendo acceder a la pantalla de consulta y auditoría de sesiones históricas
