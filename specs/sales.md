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

#### Scenario: Enter agrega por código (pendientes de la segunda revisión, 29-10-2026)
- **WHEN** el operador escribe un código en el buscador y presiona Enter
- **THEN** se agrega el producto con ese código exacto (sin distinguir mayúsculas) aunque haya más resultados; sin código exacto, Enter agrega solo si hay un único resultado, y si hay varios no hace nada. Un producto por kilo con presentaciones abre su selector, igual que al hacer clic. Pruebas en `sale-catalog.component.spec.ts`.

#### Scenario: Estructura de la pantalla de venta y del POS
- **THEN** `register-sale` usa `app-sale-catalog` (buscador y productos rápidos) y `app-cart-line` (cada fila del carrito, con `CartItem` y sus funciones en `register-sale/cart-item.ts`); el POS usa `app-pos-kpis` (las cinco cifras) y `app-cash-expenses` (gastos de la caja y su anulación, como `app-cash-transfers`). Todos los componentes del POS usan `input()`/`output()`; el modal de cobro reinicia sus montos con un `effect` solo al abrirse. El POS ya no reemplaza la pantalla por «Cargando…» cada vez que refresca la caja abierta: eso destruía y recreaba el formulario de retiro, cuyo aviso de efectivo volvía a pedir la caja, en un bucle de pedidos a `/cash-sessions/current` al abrir la pestaña Ingresos / Retiros. En celular, la fila del carrito pasa a dos renglones y la pantalla de venta no tiene scroll horizontal. Pruebas en `pos.component.spec.ts`, `cash-expenses.component.spec.ts`, `pos-kpis.component.spec.ts` y `cart-line.component.spec.ts`.

#### Scenario: Interfaz común de todas las pantallas (mejoras de UX, 29-10-2026)
- **THEN** guardar o anular algo en Productos, Proveedores, Categorías, Etiquetas, Compras, Deudas, Consumos, Finanzas y Usuarios muestra un aviso abajo a la derecha por 4 segundos («Pedido PED-000029 registrado.», «Producto X actualizado.»; `app-toast` y `ToastService`), igual que el POS ya lo hacía con su banner; los errores siguen junto al formulario.
- **THEN** el proveedor se elige con un buscador (`app-search-select`: escribir filtra sin importar tildes, flechas y Enter eligen, Escape cierra, se maneja con teclado) en Productos y en el formulario de pedido; hay unos 100 proveedores.
- **THEN** Productos dibuja de a 100 filas con «Mostrar 100 más», como Pedidos; una búsqueda o un filtro vuelve a la primera página.
- **THEN** en celulares y tabletas (una columna) la pantalla de venta deja el botón «Cobrar (Bs …)» fijo abajo, para cobrar sin bajar hasta el final.
- **THEN** los títulos coinciden con el menú («Proveedores», «Usuarios», «Cuentas», «Transacciones», «Punto de venta», «Registrar venta») y los botones y etiquetas llevan mayúscula solo al inicio («Nuevo producto», «Registrar gasto»); los nombres propios (Caja como cuenta, Bisa, QR, POS) se mantienen.
- **THEN** los íconos son SVG del mismo estilo en todas las pantallas (`app-icon`); ya no hay emojis, que se ven distinto en cada teléfono.
- Pruebas en `toast.component.spec.ts`, `search-select.component.spec.ts`, `icon.component.spec.ts`, `products.component.spec.ts` y `purchase-form-modal.component.spec.ts`.

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

#### Scenario: Pago mixto con una sola parte (segunda revisión, 29-10-2026)
- **WHEN** una venta `MIXTO` lleva todo en efectivo o todo en QR
- **THEN** el servidor la rechaza con «En un pago mixto el efectivo y el QR deben ser mayores a Bs 0,00; si se paga todo de una forma, elija Efectivo o QR.» y el modal de cobro deshabilita Confirmar y muestra el mismo aviso. Pruebas en `SaleServiceTest` y `checkout-modal.component.spec.ts`.

#### Scenario: Venta sin cuenta o categoría de ventas (T-17)
- **WHEN** falta la categoría Venta, o el cobro tiene parte en efectivo y no existe la cuenta «Caja», o tiene parte QR y no hay cuenta marcada para pagos QR
- **THEN** el sistema rechaza la venta indicando qué falta y no guarda la venta, ni toca la caja, ni genera movimientos (RG-05)

---

### Requirement: Anulación auditada de ventas en caja abierta
El sistema SHALL permitir la anulación justificada de una venta únicamente si la sesión de caja compartida a la que pertenece se encuentra actualmente en estado `ABIERTA`, requiriendo obligatoriamente el motivo de anulación y anulando automáticamente las transacciones financieras generadas por dicha venta.

#### Scenario: Motivo de anulación (segunda revisión, 29-10-2026)
- **WHEN** se anula una venta sin motivo o con uno en blanco
- **THEN** se rechaza con un solo mensaje, «El motivo de anulación es obligatorio.»; el motivo no tiene largo mínimo, igual que en compras, deudas, consumos y finanzas (antes la venta pedía 5 caracteres y mostraba dos mensajes juntos). El modal de anulación del POS tampoco pide 5 caracteres: habilita Confirmar con cualquier motivo no vacío; el de anular un gasto de caja, igual. Pruebas en `SaleControllerTest`, `void-sale-modal.component.spec.ts` y `cash-expenses.component.spec.ts`.

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


---

## Línea libre, venta sin identificar y venta de terceros (POS-11 a POS-13)

Spec del 27-10-2026, escrita con lo decidido al revisar la migración (P-42). Lo que el dueño no dijo y se resolvió
aquí queda marcado como **supuesto** para confirmarlo al probar en la app.

### Requirement: Productos especiales del catálogo (POS-11)
El sistema SHALL distinguir en `products.special_type` los productos especiales: `UNIDENTIFIED` («Producto sin
identificar») y `THIRD_PARTY` (producto de tercero, con su dueña en `products.owner_creditor_id`, una persona de
Deudas, DEU-01). Los demás productos tienen `special_type` nulo. Un producto especial no lleva costo ni precio en el
catálogo (se guardan en 0), se vende por unidad, no tiene presentaciones (CAT-11) ni inventario (CAT-05), y no se usa
en productos rápidos, compras ni consumos. El tipo y la dueña se eligen al crear el producto y no cambian después
(**supuesto**). Solo puede haber un «Producto sin identificar» no eliminado; de tercero, uno o más por persona.

#### Scenario: Crear el producto sin identificar
- **WHEN** en Productos se crea «Producto sin identificar» (código `SIN-IDENT`) con tipo «Sin identificar»
- **THEN** se guarda con costo y precio 0, por unidad y sin presentaciones; el formulario oculta costo, precio,
  utilidad, «Se vende por» y presentaciones, y el listado lo marca «Especial»

#### Scenario: Crear el producto de Mami
- **WHEN** se crea «Producto de Mami» con tipo «De tercero» y dueña Mami
- **THEN** se guarda con `owner_creditor_id` de Mami; sin dueña activa se rechaza con «Elija la dueña del producto
  de tercero.»

#### Scenario: Segundo producto sin identificar
- **WHEN** ya existe un producto sin identificar no eliminado y se crea otro
- **THEN** se rechaza con «Ya existe un producto sin identificar.»

#### Scenario: Cambiar el tipo
- **WHEN** se edita un producto y se envía otro tipo u otra dueña
- **THEN** se rechaza con «El tipo de producto y su dueña no se cambian después de crearlo.»

#### Scenario: Usos que no aplican
- **WHEN** se intenta poner un producto especial en un grupo de productos rápidos, en un pedido o en un consumo
- **THEN** se rechaza con «'<producto>' es un producto especial: solo se vende en el POS con nombre y precio.»; las
  pantallas no lo ofrecen

#### Scenario: Dueña con productos de tercero
- **WHEN** se da de baja (inactiva o eliminada) a una persona dueña de un producto de tercero no eliminado
- **THEN** se rechaza con «<persona> es dueña de '<producto>'; dé de baja el producto primero.»

---

### Requirement: Línea libre en el POS (POS-11)
El sistema SHALL pedir nombre y precio de venta, ambos obligatorios, al agregar un producto especial al carrito. La
línea guarda el nombre escrito en `sale_items.product_name` y en `sale_items.free_name` (que no cambia aunque la línea
se asigne después), el precio en `unit_price` y el tipo en `sale_items.free_line_type`. Cada vez que se agrega es una
línea nueva (no suma a otra). La cantidad es entera.
**Decidido con el dueño (P-44, 28-10-2026)**: una línea libre no admite descuento por unidad (el precio escrito ya es
el acordado). Una venta con líneas libres **sí** admite descuento general, pero se reparte solo entre las líneas
normales: la línea libre queda con su monto exacto para la deuda con la dueña y los reportes. Hecho el 28-10-2026
(rama `feat/free-line-discount`): la venta no guarda el reparto; lo calcula el reporte por proveedor.

#### Scenario: Agregar una línea libre
- **WHEN** en `/pos/sale` se elige «Producto sin identificar» en el buscador
- **THEN** se abre un formulario con Nombre y Precio (Bs); al confirmar «Granola de coco», Bs 25,00 entra la línea
  «Granola de coco» 1 × Bs 25,00 con el distintivo «Sin identificar» (o «De Mami» si es de tercero)

#### Scenario: Nombre o precio faltante
- **WHEN** la venta llega con una línea de producto especial sin nombre, o con precio 0, negativo o con más de 2
  decimales
- **THEN** se rechaza con «Escriba el nombre de la línea libre.» o «El precio de '<nombre>' debe ser mayor a 0, con
  hasta 2 decimales.» y no se guarda nada

#### Scenario: Nombre o precio en un producto normal
- **WHEN** una línea de un producto normal trae nombre o precio libre
- **THEN** se rechaza con «Solo un producto especial lleva nombre y precio escritos.»

#### Scenario: Descuento por unidad en una línea libre
- **WHEN** una línea libre trae descuento por unidad
- **THEN** se rechaza con «Una línea libre no admite descuentos.»; el POS oculta ese descuento en la línea

#### Scenario: Descuento general con líneas libres (P-44, 28-10-2026)
- **WHEN** una venta tiene 2 Roquin a Bs 4,00 (Bs 8,00), 1 Aceite de coco a Bs 125,00 y la línea libre «Mermelada de
  Mami» Bs 30,00, con descuento general de Bs 5,00
- **THEN** se acepta: el descuento general no puede superar el subtotal de las líneas normales (Bs 133,00) y se
  reparte solo entre ellas en proporción a su subtotal (para los reportes por proveedor: Bs 0,30 y Bs 4,70); la línea
  de Mami queda en Bs 30,00, la deuda con Mami sube Bs 30,00 y el total de la venta es Bs 158,00. Una línea libre
  asignada después (POS-12) también conserva su monto exacto en su proveedor

#### Scenario: Descuento general mayor que las líneas normales
- **WHEN** el descuento general supera el subtotal de las líneas normales (o la venta solo tiene líneas libres)
- **THEN** se rechaza con «El descuento general no puede superar el subtotal de los productos normales (Bs <monto>).»;
  el POS lo limita a ese subtotal con el mismo aviso y, con solo líneas libres, oculta el descuento general

#### Scenario: Venta al costo con línea libre
- **WHEN** la venta es al costo (CON-02) y tiene una línea libre
- **THEN** se rechaza con «Una venta al costo no admite líneas libres.»; con el interruptor encendido el POS no deja
  agregarlas

#### Scenario: Cobro
- **WHEN** se cobra una venta con líneas libres
- **THEN** el dinero entra a Caja o QR como cualquier venta (categoría Venta) y la caja la suma igual

---

### Requirement: Venta sin identificar y su asignación (POS-12)
El sistema SHALL guardar la línea del «Producto sin identificar» con `sale_items.assignment_status = POR_ASIGNAR` y
costo 0 pendiente (los reportes no la cuentan en la utilidad mientras esté por asignar). Desde el detalle de la venta
en el historial (`/sales`, permiso `SALES_HISTORY`, P-33) se asigna una sola vez:
- **Asignar producto**: se elige un producto normal activo que se vende por unidad; la línea toma su id, código,
  nombre y costo actual; el monto vendido, la caja y los movimientos no cambian (única excepción a T-07).
  **Confirmado (P-44, 28-10-2026)**: no se asignan productos por kilo, porque la cantidad de la línea son unidades.
  Desde el 29-10-2026 la confirmación muestra la utilidad que tendrá la línea (lo cobrado − costo × cantidad) y,
  si es negativa, avisa que la línea queda a pérdida; no se bloquea.
- **Sin identificar definitivo**: la línea queda `SIN_IDENTIFICAR` con costo 0. **Decidido con el dueño
  (28-10-2026)**: no cuenta en la utilidad; los reportes la muestran en su propia columna «Sin identificar»
  (ver «Reportes con ventas por asignar, sin identificar y de terceros»).

Queda auditado quién y cuándo (`assigned_by`, `assigned_at`). Una venta anulada no se asigna. En la V2 la
asignación descuenta el stock del producto elegido.

#### Scenario: Asignar el producto real
- **WHEN** se vendió «Granola de coco» 2 × Bs 25,00 sin identificar, después se crea «Granola coco 250 g» con costo
  Bs 15,50 al registrar su pedido (COM-03), y en el historial se asigna la línea a ese producto
- **THEN** la línea queda `ASIGNADO` con ese producto, su código, su nombre y costo Bs 15,50 (utilidad Bs 19,00);
  conserva «Granola de coco» como nombre escrito, el total de la venta sigue en Bs 50,00 y la caja no cambia. Antes de
  asignar el detalle pide confirmar («Asignar a <código> · <producto>, costo Bs <costo>. No se puede deshacer.»), igual
  que el sin identificar definitivo (hallazgo de la prueba del 28-10-2026)

#### Scenario: Segunda asignación
- **WHEN** se intenta asignar una línea que ya está asignada o cerrada como sin identificar definitivo
- **THEN** se rechaza con «La línea ya fue asignada.»

#### Scenario: Producto no válido para asignar
- **WHEN** el producto elegido no está activo, es especial o se vende por kilo
- **THEN** se rechaza con «Elija un producto activo que se venda por unidad.» o «'<producto>' es un producto
  especial: solo se vende en el POS con nombre y precio.»

#### Scenario: Sin identificar definitivo
- **WHEN** se cierra la línea como sin identificar definitivo
- **THEN** queda `SIN_IDENTIFICAR` con costo 0, el producto sigue siendo el especial, ya no aparece en el filtro
  «Por asignar» y en los reportes pasa de la columna Por asignar a Sin identificar (fuera de la utilidad)

#### Scenario: Venta anulada
- **WHEN** se intenta asignar una línea de una venta anulada
- **THEN** se rechaza con «Una venta anulada no se asigna.»

#### Scenario: Ventas por asignar en el historial
- **WHEN** en `/sales` se marca el filtro «Por asignar»
- **THEN** se listan solo las ventas completadas con al menos una línea por asignar (una venta cuyas líneas libres
  están todas asignadas o cerradas como sin identificar definitivo no aparece); en el detalle cada línea por
  asignar muestra «Por asignar» y las acciones «Asignar producto» y «Sin identificar definitivo»; las asignadas
  y las sin identificar definitivo muestran el nombre escrito, quién y cuándo

---

### Requirement: Venta de productos de terceros (POS-13)
El sistema SHALL vender los productos de tercero con línea libre (POS-11) guardando en la línea su dueña
(`sale_items.creditor_id`, copia de la del producto) y costo igual al precio (utilidad 0). La deuda con la persona
(DEU-04) pasa a ser: pagos activos de pedidos hechos por ella + líneas de tercero suyas en ventas completadas −
devoluciones activas. Se le paga con una devolución (DEU-03) cuyo concepto es «Pago a tercero» (nueva categoría de
sistema de gasto, FIN-05) en vez de Pedido.

#### Scenario: Venta de un producto de Mami
- **WHEN** se vende «Mermelada de Mami» 2 × Bs 30,00 con el producto de Mami, en efectivo
- **THEN** la línea queda con dueña Mami y costo Bs 30,00, entran Bs 60,00 a Caja como Venta y la deuda con Mami
  sube Bs 60,00

#### Scenario: Anulación de la venta
- **WHEN** se anula esa venta (caja abierta)
- **THEN** la deuda con Mami baja Bs 60,00; si ya se le devolvió de más y quedaría negativa se rechaza con «Ya se
  devolvió a Mami parte de esta venta; anule primero la devolución.»

#### Scenario: Historial de la persona
- **WHEN** se abre el detalle de Mami en Deudas
- **THEN** el historial muestra, junto a pagos de pedidos y devoluciones, cada venta con productos suyos («Venta
  VTA-000123», monto de sus líneas) con el saldo después; las ventas anuladas se ven marcadas y no cuentan

#### Scenario: Pago semanal a Mami
- **WHEN** se le devuelven Bs 60,00 a Mami desde Caja con concepto «Pago a tercero»
- **THEN** se crea la devolución y un gasto de Caja con la categoría Pago a tercero («Pago a tercero a Mami»), la
  deuda baja Bs 60,00 y el efectivo esperado también; con concepto «Pedido» sigue como hasta hoy (P-34). El
  concepto propuesto es «Pago a tercero» mientras quede pendiente de sus ventas, por ese monto (P-48, `debts.md`)

---

### Requirement: Reportes con ventas por asignar, sin identificar y de terceros (POS-12, POS-13)
El sistema SHALL mostrar aparte, en ventas por día y en totales por mes, tres columnas:
- **Por asignar**: lo vendido en líneas por asignar. Es venta de la tienda (suma al total), pero no entra en ventas
  normales, costo ni utilidad hasta que se asigne; al asignarse pasa a las normales con su costo.
- **Sin identificar** (28-10-2026): lo vendido en líneas cerradas como sin identificar definitivo. Suma al total de
  la tienda, pero queda siempre fuera de ventas normales, cantidad, costo, utilidad y margen (no se sabe su costo,
  así que no se inventa una utilidad).
- **Ventas de terceros**: lo vendido en líneas de tercero. No suma a las ventas ni a la utilidad de la tienda.

Las normales son el total de la venta menos esas tres partes; su cantidad y costo no cuentan esas líneas. Una venta
que solo tiene líneas libres no asignadas (por asignar, sin identificar o de tercero) no cuenta como venta normal.
En ventas por proveedor no entran las líneas por asignar, sin identificar ni las de tercero (una asignada cuenta en
el proveedor del producto asignado). Movimientos por categoría no cambia: la venta entra como Venta y el pago a la
dueña como Pago a tercero. La exportación a Excel lleva las mismas columnas.

#### Scenario: Día con las tres clases
- **WHEN** un día hay una venta de Bs 100,00 con una línea normal de Bs 50,00 (costo Bs 30,00), una por asignar de
  Bs 20,00 y una de Mami de Bs 30,00
- **THEN** ventas normales Bs 50,00, costo Bs 30,00, utilidad Bs 20,00, por asignar Bs 20,00, ventas de terceros
  Bs 30,00 y total del día (tienda) Bs 70,00

#### Scenario: Después de asignar
- **WHEN** la línea por asignar de Bs 20,00 se asigna a un producto de costo Bs 12,00
- **THEN** el mismo día muestra ventas normales Bs 70,00, costo Bs 42,00, utilidad Bs 28,00 y por asignar Bs 0,00

#### Scenario: Después de cerrar como sin identificar definitivo
- **WHEN** en cambio la línea por asignar de Bs 20,00 se cierra como sin identificar definitivo
- **THEN** el mismo día muestra ventas normales Bs 50,00, costo Bs 30,00, utilidad Bs 20,00, por asignar Bs 0,00,
  sin identificar Bs 20,00, ventas de terceros Bs 30,00 y total del día (tienda) Bs 70,00; en totales por mes la
  utilidad y la utilidad menos consumos tampoco la cuentan

---

### Tareas POS-11 a POS-13
- [x] Migración `V17__free_line_sales.sql`: `products.special_type` y `owner_creditor_id` con sus `CHECK` e índice
  único del sin identificar; `sale_items.free_line_type`, `free_name`, `assignment_status`, `assigned_by`,
  `assigned_at`, `creditor_id` con sus `CHECK`; categoría de sistema `THIRD_PARTY_PAYMENT` «Pago a tercero».
- [x] Productos: tipo y dueña al crear, sin cambio después, precios en 0; rechazo en productos rápidos, presentaciones,
  compras y consumos; baja de la dueña bloqueada.
- [x] Ventas: nombre y precio libres en `SaleItemRequest`, validaciones, costo según tipo, deuda al anular.
- [x] Asignación: `POST /api/sales/{id}/items/{itemId}/assign` y `/close-unidentified` (`SALES_HISTORY`), filtro
  `pendingAssignment` en `GET /api/sales`.
- [x] Deudas: deuda e historial con las ventas de tercero; concepto de la devolución.
- [x] Reportes: columnas por asignar y de terceros en ventas por día, totales y Excel; proveedor sin esas líneas.
- [x] Frontend: formulario de Productos, línea libre en `/pos/sale`, detalle de venta con asignación, filtro en
  `/sales`, historial y devolución en Deudas, columnas en Reportes.
- [x] Pruebas: `SaleServiceTest`, `ProductServiceTest`, `CreditorServiceTest`, `SaleRepositoryTest` (reportes),
  `CreditorRepositoryTest` y las de frontend que se toquen. Hechas también `SaleServiceFreeLineTest` y
  `SaleItemRepositoryTest` (asignación una sola vez).
- [x] Probar en la app (28-10-2026): «Producto sin identificar» y «Producto de Mami» creados; venta VTA-000133 con
  Mami 2 × Bs 30,00, «Granola de coco» sin identificar Bs 25,00 y un producto normal; filtro «Por asignar» y
  asignación desde el historial; deuda con Mami Bs 60,00 y devolución desde Caja con concepto «Pago a tercero»
  (gasto de la categoría Pago a tercero, deuda Bs 0,00); reportes del día: normales Bs 52,00 con la línea asignada,
  de terceros Bs 60,00 fuera del total, proveedor igual a las normales, movimientos Venta 112 y Pago a tercero −60.
  Supuestos de P-44 confirmados por el dueño el 28-10-2026.
  Hallazgos: (1) asignar se hacía con un clic sin confirmar: corregido, ahora pide confirmar; (2) un producto
  especial pide categoría y proveedor, que no usa (P-50); (3) la devolución propone el concepto Pedido aunque la
  deuda venga de ventas de tercero (P-48). La tabla del detalle tiene alto fijo con scroll: al asignar, la última
  línea queda abajo del panel (no es un error).
  Respondidas el 28-10-2026 (rama `feat/open-questions-v1`): P-50, los productos especiales siguen con categoría y
  proveedor obligatorios y se les ponen reales (los de Mami: Inciensos y Sagrada Madre), sin cambio de código;
  P-48, la devolución propone «Pago a tercero» por lo pendiente de sus ventas (`creditor-detail-modal`, prueba en su
  spec). Falta probar P-48 en la app.
- [x] Descuento general con líneas libres (P-44, 28-10-2026, rama `feat/free-line-discount`): `SaleServiceImpl`
  limita el descuento al subtotal de las líneas normales (tras sus descuentos por unidad); `findReportLines` trae si
  la línea es libre y `ReportServiceImpl` reparte el descuento solo entre las demás (la libre, asignada o no, queda
  exacta); ventas por día ya calculaba las normales como total − por asignar − terceros. En el POS el descuento
  general se muestra salvo que todas las líneas sean libres y se limita al subtotal de las normales. Probado en la
  app (VTA-000134: Kefir Bs 27,00 + Mami Bs 30,00 − Bs 5,00 = Bs 52,00, deuda +Bs 30,00, proveedor = normales).
  Pruebas en `SaleServiceFreeLineTest`, `ReportServiceTest`, `SaleRepositoryTest`, `register-sale` y
  `sale-detail-modal`.
- [x] Sin identificar definitivo fuera de la utilidad (decidido el 28-10-2026, hecho el 29-10-2026, rama
  `feat/unidentified-final`): en `SaleRepository` la línea normal es solo la no libre o la `ASIGNADO`;
  `sumByDayAndType` devuelve además el total `SIN_IDENTIFICAR` y cuenta como venta normal solo la que tiene alguna
  línea normal (antes contaba también la que solo tenía líneas por asignar); `findReportLines` deja esas líneas
  fuera del proveedor. `unidentifiedTotal` en ventas por día y totales (suma al total, no a la utilidad), columna
  «Sin identificar» en su Excel y en la pantalla Reportes. El filtro «Por asignar» ya buscaba solo `POR_ASIGNAR`.
  La migración histórica cierra las filas de Ventas sin código como `SIN_IDENTIFICAR` con quien importa y la hora
  de la importación; Revisar muestra «Líneas sin identificar» y la columna Sin identificar de los totales por mes
  (`unidentifiedLines`, `unidentifiedTotal`, `MonthTotals.unidentified`). Pruebas en `SaleRepositoryTest`
  (consultas y filtro), `ReportServiceTest`, `ReportExportServiceTest`, `MigrationIntegrationTest`,
  `sales-by-day-report`, `totals-report` y `migration.component`. Probado en la app el 29-10-2026: VTA-000139 con
  «Prueba POS-12 granola» Bs 20,00 sale del filtro «Por asignar» al cerrarla y pasa de Por asignar a Sin
  identificar en ventas por día, totales y el Excel, sin tocar la utilidad. La confirmación decía todavía «costo 0:
  toda la venta cuenta como utilidad»; ahora dice que suma al total y no a la utilidad.


## Notas de implementación

Detalle de cómo se construyó cada requerimiento, movido desde `requirements.md` el 29-10-2026 sin cambios.

- **POS-06** Numeración de venta única y correlativa. Hallazgo del 28-10-2026: en desarrollo los números de ventas, pedidos, consumos y cajas saltan (PED-000025 a PED-000071) porque las pruebas de integración usan la misma base del `.env` y sus rollbacks consumen las secuencias. No afecta a producción, donde no se corren pruebas.
- **POS-09** Historial de ventas con filtros por fecha, estado, tipo de venta, método de pago y usuario. Hecho el 26-10-2026: `GET /api/sales` (permiso `SALES_HISTORY`) es paginado (`page`, `size` de 20 por defecto y hasta 100, del más reciente al más antiguo) y filtra por `from`/`to` (días de la tienda, America/La_Paz, `to` incluido), `status`, `saleType`, `paymentMethod` y `createdBy`; `GET /api/sales/sellers` da los usuarios con ventas para el filtro. Un método incluye toda venta que lo usó: Efectivo y QR incluyen las mixtas y Crédito incluye las ventas pagadas en parte y el resto a crédito; Mixto, solo las mixtas. Las filas vienen sin líneas y el detalle se carga por id en `sale-detail-modal`. La pantalla `/sales` (carga diferida) y el listado de ventas de la sesión en el POS usan el listado compartido `shared/components/sales-table`, que muestra Crédito, la parte a crédito, «Al costo», el cliente y, en el historial, la caja; en el POS además «Anular» (POS-07). Ver `specs/sales.md`.
- **POS-10** Venta por peso: el POS acepta cantidades decimales y ofrece las presentaciones del producto (desde el 26-10-2026, configurables por producto con CAT-11; antes, botones fijos 100 g y 1/2 libra). Hecha el 25-10-2026 en `register-sale`: un producto por kilo entra al carrito con 100 g (0,100 kg), los botones +/− suman o restan 100 g, la cantidad se escribe en kilos con hasta 3 decimales, y la línea ofrece «100 g» y «1/2 libra» (0,227 kg); el precio se muestra «/kg» y la línea cuenta como un ítem. Los productos por unidad siguen con enteros. Ver `specs/sales.md`.
- **T-06** · V1 — Hallazgo: `sale_number` sin restricción de unicidad y calculado como cantidad de ventas de la caja + 1 (dos ventas simultáneas reciben el mismo número); estados como texto libre sin `CHECK`; nada impide en la base dos cajas abiertas. Desde T-01, `V1__init.sql` ya tiene los `CHECK` de estados y el índice único parcial de caja ABIERTA; falta lo de `sale_number`. Requerimiento: Unicidad y dominios de estado garantizados en la base: número de venta por secuencia, índice único de `sale_number` e índice único parcial de caja con estado ABIERTA. Hecha el 25-10-2026: migración `V4__sale_number_sequence.sql` crea la secuencia `sale_number_seq` y el índice único `uq_sales_sale_number`; los números repetidos existentes conservan el primero y los demás reciben sufijo `-2`, `-3`… Las ventas nuevas se numeran `VTA-000001`, `VTA-000002`… (global, ya no por caja), formato que no choca con el anterior `VTA-<caja>-<n>`.
- **T-07** · V1 — Hallazgo: `sale_items` no guarda el costo del producto al vender. Requerimiento: Snapshot de costo por línea (POS-05). Hecha el 25-10-2026: migración `V5__sale_item_unit_cost.sql` agrega `sale_items.unit_cost` (`NUMERIC(12,2)`, obligatorio, `CHECK >= 0`); `SaleServiceImpl` lo llena con el `buy_price` del producto al registrar la venta, y la entidad lo marca `updatable = false`, así que ni un cambio de costo (CAT-07, COM-03) ni la anulación lo alteran. Ventas ya existentes: como no hay datos de producción (T-01), las líneas de desarrollo toman el costo actual del producto; las ventas históricas de las planillas traen su propio costo al importarse (FIN-11). El costo no se expone en la respuesta del detalle de venta: el operador ve ventas y no necesita el costo; los reportes de utilidad (FIN-10) lo leerán en el servidor cuando se construyan. Pruebas en `SaleServiceTest` y `SaleItemRepositoryTest` (contra PostgreSQL).
- **T-11** · V1 — Hallazgo: Cantidades enteras (`quantity INT`). Requerimiento: Cantidades decimales para venta por peso (P-03, CAT-08). Hecha el 25-10-2026 para ventas: migración `V6__sale_item_decimal_quantity.sql` cambia `sale_items.quantity` a `NUMERIC(12,3)` (las enteras existentes se convierten sin pérdida) con `CHECK quantity > 0`. `SaleItem`, `SaleItemRequest` y `SaleItemResponse` usan `BigDecimal`; la API acepta cantidades mayores a 0 con hasta 3 decimales y `SaleServiceImpl` las guarda en escala 3 (rechaza 0, negativas o con más decimales). Subtotal, bruto y descuento de línea siguen en escala 2 `HALF_UP`; el descuento de línea se calcula como bruto − subtotal para que las líneas sumen el total de la venta. El frontend ya trataba la cantidad como `number`; la entrada decimal y las presentaciones rápidas en el POS se hicieron en POS-10, con `products.sale_unit` (CAT-08) y la regla de que un producto por unidad solo acepta enteros. Compras usa la misma escala desde el 25-10-2026 (`purchase_items.quantity`, COM-02); consumos la usarán cuando existan (CON). Pruebas en `SaleServiceTest` y `SaleItemRepositoryTest` (contra PostgreSQL).
- **T-17** · V1 — Hallazgo: Si no existe la cuenta Caja, la cuenta QR o la categoría Venta, la venta se guarda sin su movimiento financiero, sin error. El endpoint heredado `POST /api/cash-sessions/current/sales` registra ventas sin ítems ni movimientos financieros. Requerimiento: La venta falla si falta su cuenta o categoría (RG-05); se elimina el endpoint heredado. Hecha el 25-10-2026: `SaleServiceImpl` resuelve la categoría Venta y bloquea las cuentas de venta antes de guardar nada (mismo orden de T-14: caja, luego cuentas); sin categoría Venta responde «No existe la categoría Venta; no se puede registrar la venta.», un cobro en efectivo sin cuenta Caja «No existe la cuenta Caja; …» y un cobro QR sin cuenta QR «No hay una cuenta marcada para cobros QR; …». Solo se exige la cuenta que usa el método de pago (una venta QR no necesita Caja). Se eliminó `POST /api/cash-sessions/current/sales` (`registerSaleInCurrentSession`, `SaleRequest`, sus pruebas y su fila en `EndpointAuthorizationTest`) y en el frontend `CashService.registerSale` y el modal sin uso `register-sale-modal`. Pruebas en `SaleServiceTest`.
- **POS-11 a POS-13 hechos** el 27-10-2026 (spec en `specs/sales.md`, rama `feat/free-line-sales`, migración `V17__free_line_sales.sql`): `products.special_type` (`UNIDENTIFIED`, `THIRD_PARTY`) y `owner_creditor_id`, elegidos al crear y sin cambio después, con costo y precio 0, por unidad, fuera de productos rápidos, compras y consumos; un solo «Producto sin identificar». La línea guarda `free_line_type`, `free_name` (nombre escrito) y, la de tercero, `creditor_id` con costo = precio. Supuestos (P-44, confirmados el 28-10-2026 salvo el descuento general, que se construyó después): una línea libre no admite descuento por unidad, una venta con líneas libres no puede ser al costo, y solo se asigna un producto normal activo que se vende por unidad. La asignación (`POST /api/sales/{id}/items/{itemId}/assign` y `/close-unidentified`, permiso `SALES_HISTORY`) se hace con una actualización explícita de la línea por asignar (`SaleItemRepository.assign`); la entidad sigue sin poder reescribir el costo (T-07). La deuda con la persona suma sus líneas de tercero de ventas completadas; anular la venta se rechaza si ya se le devolvió ese dinero. La devolución elige concepto: Pedido (por defecto) o «Pago a tercero» (categoría de sistema `THIRD_PARTY_PAYMENT`, `creditor_repayments.concept`). Ventas por día y totales tienen las columnas Por asignar y Sin identificar (suman al total, no a la utilidad) y De terceros (fuera del total); ventas por proveedor no las cuenta. Probados en la app el 28-10-2026 (productos especiales, venta, asignación, deuda y pago a Mami, reportes); la asignación pide confirmar desde entonces.
