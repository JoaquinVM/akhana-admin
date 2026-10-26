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

### Requirement: Número de venta único (T-06)
El sistema SHALL numerar cada venta con una secuencia de la base de datos, única en todo el sistema y no por caja, con formato `VTA-000001`; la base impide números repetidos con un índice único sobre `sale_number`.

#### Scenario: Ventas simultáneas
- **WHEN** dos ventas se registran al mismo tiempo en la misma caja
- **THEN** cada una recibe un número distinto de la secuencia

---

### Requirement: Costo por línea de venta (POS-05, T-07)
El sistema SHALL guardar en cada línea de venta (`sale_items.unit_cost`) el costo unitario del producto al momento de vender, con escala 2, para que la utilidad histórica no cambie. El costo no se modifica después de registrada la venta y no se muestra en el detalle de venta; lo usarán los reportes de utilidad (FIN-10). Las líneas que existían antes de la migración tomaron el costo actual del producto (no había datos de producción); las ventas históricas importadas (FIN-11) traen su propio costo.

#### Scenario: Cambio de costo del producto después de vender
- **WHEN** se vende un producto con costo Bs 12,50 y luego su costo cambia a Bs 14,00
- **THEN** la línea de esa venta conserva Bs 12,50 y las ventas nuevas usan Bs 14,00

#### Scenario: Anulación de una venta
- **WHEN** se anula una venta
- **THEN** el costo guardado en sus líneas no cambia

---

### Requirement: Cantidades decimales para venta por peso (CAT-08, T-11)
El sistema SHALL aceptar en cada línea de venta una cantidad mayor a 0 con hasta 3 decimales (`sale_items.quantity`, `NUMERIC(12,3)`), para productos vendidos por peso (100 g = 0,100; 1/2 libra). Los montos de la línea siguen en escala 2 con `HALF_UP`: subtotal = precio final × cantidad, bruto = precio × cantidad, y el descuento de la línea es bruto − subtotal, así las líneas siempre suman el total de la venta. La entrada de cantidades decimales y las presentaciones rápidas en el POS son POS-10.

#### Scenario: Venta de una cantidad decimal
- **WHEN** se venden 0,375 de un producto de Bs 20,00 con descuento por unidad de Bs 0,05
- **THEN** el bruto es Bs 7,50, el subtotal Bs 7,48 (7,48125 redondeado) y el descuento de la línea Bs 0,02

#### Scenario: Cantidad inválida
- **WHEN** la cantidad es 0, negativa o tiene más de 3 decimales
- **THEN** la venta se rechaza con «La cantidad de '<producto>' debe ser mayor a 0.» o «… admite hasta 3 decimales.» y no se guarda nada

#### Scenario: Producto por unidad con decimales
- **WHEN** la cantidad de un producto con `sale_unit = UNIT` tiene decimales (ej. 0,5)
- **THEN** la venta se rechaza con «La cantidad de '<producto>' debe ser entera: se vende por unidad.» y no se guarda nada

---

### Requirement: Venta por peso en el carrito (POS-10, CAT-08)
El sistema SHALL permitir en `/pos/sale` vender en kilos los productos con `sale_unit = KILOGRAM`, cuyo precio de venta es Bs por kilo, con cantidades de hasta 3 decimales. Desde el 26-10-2026 las presentaciones son configurables por producto (CAT-11, `specs/product-presentations.md`): los botones fijos «100 g» y «1/2 libra» de la línea se reemplazaron por un selector al agregar el producto. Los productos por unidad siguen con cantidades enteras.

#### Scenario: Agregar un producto por peso
- **WHEN** se agrega al carrito un producto por kilo de Bs 80,00 sin presentaciones activas, o se elige «Otro peso» en el selector
- **THEN** la línea entra con 0,100 kg y subtotal Bs 8,00, el precio se muestra «Bs 80,00 /kg» y la línea cuenta como un ítem

#### Scenario: Sumar y restar de a 100 g
- **WHEN** se pulsa + dos veces sobre esa línea
- **THEN** la cantidad pasa a 0,300 kg (sin errores de redondeo) y el subtotal a Bs 24,00; con − la cantidad baja 100 g y la línea se quita al llegar a 0

#### Scenario: Elegir una presentación (CAT-11)
- **WHEN** se agrega un producto por kilo con presentaciones activas (Nuez pelada a Bs 210,00 /kg con 1/2 libra a precio fijo Bs 45,00)
- **THEN** se abre el selector con las presentaciones de menor a mayor peso y «Otro peso…»; al elegir «1/2 libra» entra la línea «Nuez pelada · 1/2 libra», 1 × Bs 45,00, cantidad entera, y elegirla otra vez suma 1

#### Scenario: Cantidad escrita a mano
- **WHEN** se escribe 0,3755 en la cantidad de un producto por peso
- **THEN** se toma 0,376 kg (3 decimales); en un producto por unidad se toma el entero más cercano, mínimo 1

#### Scenario: Sin botones fijos en la línea
- **WHEN** el carrito tiene un producto por peso en «Otro peso»
- **THEN** la línea no muestra botones «100 g» ni «1/2 libra»: los pesos fijos son presentaciones del producto (CAT-11)

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

#### Scenario: Venta sin cuenta o categoría de ventas (T-17)
- **WHEN** falta la categoría Venta, o el cobro tiene parte en efectivo y no existe la cuenta «Caja», o tiene parte QR y no hay cuenta marcada para pagos QR
- **THEN** el sistema rechaza la venta indicando qué falta y no guarda la venta, ni toca la caja, ni genera movimientos (RG-05)

---

### Requirement: Anulación auditada de ventas en caja abierta
El sistema SHALL permitir la anulación justificada de una venta únicamente si la sesión de caja compartida a la que pertenece se encuentra actualmente en estado `ABIERTA`, requiriendo obligatoriamente el motivo de anulación y anulando automáticamente las transacciones financieras generadas por dicha venta.

#### Scenario: Anulación exitosa de venta en caja abierta
- **WHEN** un usuario solicita anular una venta de la sesión activa ingresando el motivo obligatorio "Error en digitación de productos"
- **THEN** el sistema cambia el estado de la venta a `ANULADO`, registra la fecha/hora actual, el usuario que anula, el motivo, genera la transacción de tipo `ANULACIÓN` en la caja, revierte los montos en los saldos de caja y anula automáticamente los movimientos financieros generados por la venta restituyendo los saldos de las cuentas involucradas con el mismo motivo y usuario de anulación

#### Scenario: Rechazo estricto de anulación en caja cerrada
- **WHEN** se intenta solicitar la anulación de una venta cuya sesión de caja ya fue cerrada
- **THEN** el sistema rechaza la operación indicando que no se permite anular ventas de cajas cerradas

### Requirement: Trazabilidad y consulta de ventas
El sistema SHALL presentar en las consultas de ventas y en el panel de transacciones de la caja las ventas registradas con su estado (`COMPLETADA` o `ANULADA`), conservando el detalle histórico íntegro sin eliminación física. El listado de transacciones SHALL ofrecer una columna de acciones que siga el patrón visual estándar del sistema (`.table-actions` y botones `.btn-icon` de Productos y Proveedores) con las acciones ordenadas en: 1. "Ver detalle" (inspección de sólo lectura) y 2. "Anular" (si la venta no está anulada y la caja está abierta).

#### Scenario: Consulta de venta anulada
- **WHEN** el usuario visualiza el listado de transacciones de ventas del turno o consulta general
- **THEN** la venta anulada se visualiza con distintivo visual `ANULADA`, conservando sus datos originales sin exponer el motivo de anulación en la fila del listado, con la acción "Ver detalle" habilitada para consultar dicho motivo y auditoría, y la acción "Anular" deshabilitada para prevenir una segunda anulación

#### Scenario: Acciones estándar en el listado de ventas
- **WHEN** el usuario revisa cualquier fila de venta en la tabla del POS
- **THEN** la columna de acciones muestra el botón "Ver detalle" en primer lugar y el botón "Anular" en segundo lugar, adoptando el diseño consistente con las tablas de Productos y Proveedores

---

### Requirement: Modal reutilizable y desacoplado de detalle de venta
El sistema SHALL proveer un componente modal reutilizable e independiente (`SaleDetailModalComponent`) para visualizar el desglose exhaustivo de una venta en modo estricto de solo lectura, tanto desde el POS como posteriormente desde el Historial de ventas.

#### Scenario: Visualización general de la venta
- **WHEN** el usuario pulsa "Ver detalle" sobre una venta
- **THEN** el sistema abre el modal mostrando N.º de venta, fecha y hora, usuario que registró la venta, sesión de caja asociada en formato `Sesión #X` y el estado (`Completada` o `Anulada`)

#### Scenario: Desglose de productos vendidos y descuentos por ítem
- **WHEN** se visualiza el modal de detalle de una venta
- **THEN** se presenta una tabla con las columnas Producto, Cantidad, Precio unitario (original), Descuento (monto y porcentaje si corresponde) y Subtotal de cada ítem

#### Scenario: Resumen de totales y descuentos acumulados
- **WHEN** se visualiza el resumen económico en el modal de detalle
- **THEN** se exhibe el Monto sin descuento, Descuentos por productos, Descuento por venta, el Total descuentos acumulados (`Descuentos por productos + Descuento por venta`) y el Total neto final

#### Scenario: Desglose de métodos de pago utilizados
- **WHEN** se consulta el modal de detalle de una venta
- **THEN** se listan todos los métodos de pago aplicados (`Efectivo`, `QR` o ambos) junto con sus importes exactos cobrados

#### Scenario: Información de auditoría para ventas anuladas
- **WHEN** se abre el detalle de una venta cuyo estado es `ANULADA`
- **THEN** el modal mantiene visibles todos los productos, montos y métodos originales y despliega adicionalmente una sección destacada con fecha/hora de anulación, usuario que realizó la anulación y motivo registrado

#### Scenario: Restricción de solo consulta
- **WHEN** el modal de detalle se encuentra abierto
- **THEN** no se presentan controles de edición de productos, cantidades, precios o métodos de pago, ni botones de anulación dentro del modal, permitiendo únicamente cerrar el modal y regresar a la vista de origen

---

### Requirement: Historial de ventas (POS-09)
El sistema SHALL ofrecer en `/sales` (permiso `SALES_HISTORY`) el historial de ventas de todas las cajas, de la más reciente a la más antigua, paginado en el servidor (`GET /api/sales`, 20 por página, hasta 100) y filtrable por fecha, estado, tipo de venta, método de pago y usuario. El detalle se abre con `SaleDetailModalComponent`.

#### Scenario: Filtro por fechas
- **WHEN** se filtra desde el 01/10/2026 hasta el 25/10/2026
- **THEN** se listan las ventas registradas entre las 00:00 del 1 y las 23:59 del 25 en hora de Bolivia; si «desde» es posterior a «hasta» se muestra «La fecha desde no puede ser posterior a la fecha hasta.»

#### Scenario: Filtro por método de pago
- **WHEN** se filtra por Efectivo, QR o Crédito
- **THEN** se listan las ventas que usaron ese método aunque sea en parte: Efectivo y QR incluyen las mixtas y Crédito incluye las ventas al costo pagadas en parte y el resto a crédito; Mixto lista solo las mixtas

#### Scenario: Filtros por estado, tipo y usuario
- **WHEN** se elige Anuladas, «Al costo» y un usuario de la lista (usuarios con al menos una venta)
- **THEN** se listan solo las ventas anuladas al costo registradas por ese usuario, y la paginación vuelve a la página 1

#### Scenario: Fila de una venta a crédito
- **WHEN** una venta al costo quedó toda a crédito o en parte a crédito
- **THEN** la fila muestra el distintivo «Al costo», el método «Crédito» (o el método pagado y «+ Crédito Bs X») y el cliente

#### Scenario: Paginación
- **WHEN** hay 41 ventas con los filtros elegidos
- **THEN** se muestran 20 por página, «41 ventas · Página 1 de 3» y los botones Anterior y Siguiente

#### Scenario: Detalle desde el historial
- **WHEN** se pulsa «Ver detalle» en una fila
- **THEN** se abre el detalle de solo lectura con sus líneas, pagos y, si está anulada, la auditoría; el historial no ofrece «Anular»

