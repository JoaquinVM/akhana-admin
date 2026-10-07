# sales-registration Specification

## Purpose
Permite el registro detallado de ventas en el punto de venta (POS), integrando búsqueda de productos, accesos rápidos organizados en grupos configurables con drag-and-drop, líneas de detalle con descuentos por unidad, descuento general sobre la venta, pasarela de pagos en efectivo con cambio, QR y mixto, y anulación auditada de transacciones mientras la caja permanezca abierta.

## Requirements

### Requirement: Buscador y selección de productos para venta
El sistema SHALL proveer un buscador interactivo en el POS que permita filtrar y seleccionar productos activos para incorporarlos al carrito de venta.

#### Scenario: Incorporación inicial de producto
- **WHEN** el usuario busca un producto activo y lo selecciona
- **THEN** el producto se agrega a las líneas de detalle con una cantidad inicial de `1`

#### Scenario: Incremento automático de cantidad por selección repetida
- **WHEN** el usuario selecciona un producto que ya existe en el detalle de la venta actual
- **THEN** el sistema incrementa la cantidad existente en `1` unidad en lugar de duplicar la fila

---

### Requirement: Productos rápidos organizados en grupos configurables
El sistema SHALL disponer de una sección de accesos directos ("Productos Rápidos") en el POS organizada por grupos, permitiendo agregar productos al carrito en un solo clic e incrementando la cantidad si ya existen en la venta.

#### Scenario: Selección de producto rápido
- **WHEN** el usuario hace clic sobre una tarjeta de producto rápido en el grupo activo
- **THEN** el producto se añade al carrito con cantidad `1`, o incrementa su cantidad en `1` si ya estaba presente

#### Scenario: Configuración de grupos y productos rápidos desde el POS
- **WHEN** el usuario abre el modal de configuración de productos rápidos
- **THEN** el sistema permite crear nuevos grupos, renombrarlos, asignar productos activos a cada grupo y eliminar grupos

#### Scenario: Reordenamiento visual mediante drag and drop
- **WHEN** el usuario arrastra y suelta grupos para modificar su orden o arrastra productos para cambiar su secuencia dentro de un grupo
- **THEN** el sistema actualiza la posición visual y persiste el nuevo ordenamiento en la base de datos de manera inmediata

---

### Requirement: Detalle de venta y descuentos por unidad
El sistema SHALL desglosar cada ítem de venta mostrando producto, cantidad editable, precio unitario, descuento por unidad editable en monto fijo, precio unitario final y subtotal de la línea.

#### Scenario: Cálculo de subtotal con descuento por unidad
- **WHEN** un ítem tiene precio unitario de 20.00 Bs, descuento por unidad de 2.00 Bs y cantidad de 3
- **THEN** el sistema calcula el precio unitario final en 18.00 Bs y el subtotal de la línea en 54.00 Bs

#### Scenario: Validación de descuento por unidad no negativo ni superior al precio
- **WHEN** el usuario ingresa un descuento unitario negativo o mayor al precio unitario del producto
- **THEN** el sistema impide el registro del valor y notifica el error de validación correspondiente

---

### Requirement: Descuento general sobre la venta
El sistema SHALL permitir la aplicación de un descuento general en monto fijo sobre el total resultante tras aplicar los descuentos individuales por producto.

#### Scenario: Aplicación simultánea de descuentos individuales y descuento general
- **WHEN** el subtotal tras descuentos por producto suma 100.00 Bs y el usuario aplica un descuento general de 15.00 Bs
- **THEN** el sistema muestra el total de descuentos y fija el total final a pagar en 85.00 Bs

#### Scenario: Restricción del monto de descuento general
- **WHEN** el usuario intenta aplicar un descuento general superior al subtotal acumulado de la venta
- **THEN** el sistema rechaza el descuento general por exceder el monto total de la venta

---

### Requirement: Navegación desacoplada entre POS y registro de ventas
El sistema SHALL mantener el POS principal como centro de control de caja y transacciones, disponiendo de un botón «Registrar venta» en la cabecera de transacciones que navega hacia la pantalla dedicada `/pos/sale`.

#### Scenario: Acceso a pantalla de registro de venta
- **WHEN** el usuario pulsa «Registrar venta» en el POS teniendo una caja abierta
- **THEN** la aplicación navega hacia la pantalla `/pos/sale` habilitando la selección de productos y cobro inline

#### Scenario: Bloqueo de acceso si la caja está cerrada
- **WHEN** un usuario intenta ingresar a `/pos/sale` sin una sesión de caja abierta
- **THEN** el sistema redirige automáticamente a `/pos` notificando que se requiere abrir caja para registrar ventas

---

### Requirement: Modalidades de pago con cálculo automático inline en pantalla
El sistema SHALL procesar el cobro de la venta directamente en la pantalla de registro de venta (sin abrir modales auxiliares) bajo las modalidades de `EFECTIVO`, `QR` o `MIXTO` (efectivo + QR), validando los montos ingresados y calculando el cambio según corresponda.

#### Scenario: Pago exclusivo en efectivo con cálculo de cambio
- **WHEN** la venta totaliza 85.00 Bs, el método seleccionado es `EFECTIVO` y el usuario ingresa 100.00 Bs recibidos
- **THEN** el sistema calcula automáticamente un cambio de 15.00 Bs y habilita la confirmación de la venta

#### Scenario: Rechazo de pago en efectivo insuficiente
- **WHEN** el método es `EFECTIVO` y el monto recibido ingresado es menor al total de la venta
- **THEN** el sistema bloquea la confirmación e indica que el monto recibido en efectivo no puede ser menor al total

#### Scenario: Pago exclusivo mediante QR
- **WHEN** el método seleccionado es `QR` para una venta de 85.00 Bs
- **THEN** el sistema fija el monto QR en exactamente 85.00 Bs, deshabilita el ingreso de monto recibido y establece el cambio en 0.00 Bs

#### Scenario: Pago mixto con efectivo y QR
- **WHEN** una venta de 100.00 Bs se paga con método `MIXTO` y el usuario ingresa 40.00 Bs en efectivo
- **THEN** el sistema calcula automáticamente que el monto por QR es 60.00 Bs y procesa la transacción desglosando ambos importes

---

### Requirement: Anulación auditada de ventas en caja abierta
El sistema SHALL permitir la anulación justificada de una venta únicamente si la sesión de caja compartida a la que pertenece se encuentra actualmente en estado `ABIERTA`, requiriendo obligatoriamente el motivo de anulación.

#### Scenario: Anulación exitosa de venta en caja abierta
- **WHEN** un usuario solicita anular una venta de la sesión activa ingresando el motivo obligatorio "Error en digitación de productos"
- **THEN** el sistema cambia el estado de la venta a `ANULADO`, registra la fecha/hora actual, el usuario que anula, el motivo, genera la transacción de tipo `ANULACIÓN` en la caja y revierte los montos correspondientes en los saldos de caja

#### Scenario: Rechazo estricto de anulación en caja cerrada
- **WHEN** se intenta solicitar la anulación de una venta cuya sesión de caja ya fue cerrada
- **THEN** el sistema rechaza la operación indicando que no se permite anular ventas de cajas cerradas

---

### Requirement: Trazabilidad y consulta de ventas
El sistema SHALL presentar en las consultas de ventas y en el panel de transacciones de la caja las ventas registradas con su estado (`COMPLETADA` o `ANULADA`), conservando el detalle histórico íntegro sin eliminación física.

#### Scenario: Consulta de venta anulada
- **WHEN** el usuario visualiza el listado de ventas del turno o consulta general
- **THEN** la venta anulada se visualiza con distintivo visual `ANULADO`, mostrando el motivo de anulación y permitiendo auditar la transacción de anulación asociada
