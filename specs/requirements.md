# Akhana — Requerimientos y hoja de ruta

Fuente de verdad del proyecto. Se mantiene en este archivo; las specs por módulo (`specs/<modulo>.md`) usan los mismos IDs.
Origen: documento de claude.ai del 24–25 de octubre de 2026, exportado aquí al pasar la orquestación a Claude Code.

## 1. Lo que existe hoy

Akhana Admin ya resuelve bien la venta en POS, la caja y el dinero. **Ni el sistema ni las planillas actuales llevan stock**, así que la primera versión puede reemplazar las planillas sin inventario: lo que falta son compras (pedidos) con sus pagos, algunos movimientos de caja, consumos y reportes.

Stack: Spring Boot 4.1 (Java 21), Angular 21, PostgreSQL 17.

| Módulo | Qué hace hoy | Estado | Qué falta |
| --- | --- | --- | --- |
| Login y usuarios | JWT, BCrypt, baja lógica, roles Operador/Administrador con permisos por pantalla, protección del último admin | Completo | Permisos de las pantallas de compras, consumos y reportes cuando se construyan |
| Catálogo | Productos (código, nombre, categoría, proveedor, etiquetas, precio compra/venta, utilidad), categorías, etiquetas | Completo | Importación |
| Proveedores | ABM con baja lógica | Completo | Base para compras |
| Caja | Una sola caja abierta global, apertura, cierre con arqueo por cortes en Bs (caja + reserva), diferencia | Completo | Ingresos/retiros de caja, ajuste por sobrante |
| POS / ventas | Carrito, productos rápidos por grupos, descuento por ítem y general, pago efectivo/QR/mixto, cambio, anulación solo con caja abierta | Completo | Costo por línea, venta al costo |
| Historial de ventas | Ruta `/sales` existe pero es una página vacía | Pendiente | Construir |
| Finanzas | Cuentas, cuenta QR única, categorías ingreso/gasto, ingresos/gastos manuales, transferencias, anulación, resumen | Completo | Cuentas Caja/Bisa/Efectivo, reportes |
| Compras | Pedidos con líneas (cantidades de 3 decimales), envío como gasto de Transporte, pagos desde Caja u otra cuenta, estado de pago, alerta de costo y precio sugerido, entrega, vista por pagar y anulación de pedidos y pagos (COM-01 a COM-09, `specs/purchases.md`) | Completo para la V1 | Pago por persona (DEU-02) |
| Inventario | Nada: sin stock, sin movimientos, sin kardex | No existe | V2 |
| Reportes | Solo el resumen de finanzas | Mínimo | Construir |

### 1.1 Las planillas que hay que reemplazar

Hoy la operación vive en dos Google Sheets: **Akhana Recaudaciones** (12 hojas) y **Akhana Pedidos**. Ventas de unos Bs 2.000–4.000 por día, ~1.200 productos y ~420 pedidos desde abril.

| Hoja | Qué registra hoy | En el sistema |
| --- | --- | --- |
| Productos | Grupo, proveedor, etiqueta, código, costo, precio, utilidad | Existe (categoría, proveedor, etiquetas, precios). Falta importar. |
| Ventas | Una fila por producto y día, con costo y utilidad | Existe: el POS registra cada venta como ticket. Falta guardar el costo de la línea. |
| Cierre de Caja + Conteo | Caja inicial, otro ingreso, ventas efectivo/QR, gastos, salida interna, caja final, sobrante; conteo por billetes | Existe apertura, cierre, cortes y diferencia. Faltan ingresos/retiros de caja y el ajuste por sobrante. |
| Transacc. + Resumen | Movimientos de Caja, Bisa y Efectivo por categoría (Pedido, Consumo, Comida, Transporte, Retiro caja, Depósito, Utilidad…) | Existe en Finanzas. Las cuentas iniciales pasan a Caja, Bisa y Efectivo. |
| Pedidos + Detalle Pedidos | Pedido a proveedor: monto, envío, forma y estado de pago, entrega, líneas con costo y precio | No existe. Es el principal faltante. |
| Consumos | Productos consumidos en la tienda | No existe. |
| Resumen Ventas / Diario / Transacc. | Tablas dinámicas por día, proveedor, mes y categoría | No existe; el historial de ventas está vacío. |
| HOJITA | Borrador diario de consumos, pedidos por pagar y otros gastos | Desaparece cuando existan Pedidos y Consumos. |

## 2. Versión 1: reemplazo de planillas

La V1 sale a producción cuando reemplaza las dos planillas, sin inventario. El stock llega en la V2 y se calcula a partir de las compras, ventas y consumos que la V1 ya registra por producto, sin recapturar nada.

### 2.1 Alcance

Entra en la V1:

- Catálogo con los ~1.200 productos importados de la planilla, incluida la venta por peso (100 g, 1/2 libra) con cantidades decimales.
- POS y caja como hoy, más ingresos y retiros de caja, ajuste automático por sobrante o faltante, y caja inicial propuesta desde el cierre anterior.
- Compras (pedidos) con líneas, envío, pagos parciales, estado de pago y de entrega, y pago desde la caja.
- Deudas con personas que pagan pedidos por el negocio (ej. "Mami pagó") y cuentas por cobrar de la tienda amiga.
- Consumos internos y ventas al costo a la tienda amiga, separados de las ventas normales.
- Finanzas con las cuentas Caja, Bisa y Efectivo.
- Historial de ventas y los reportes que hoy dan las tablas dinámicas.
- Dos roles: operador y administrador.
- Migración de productos, saldos, pedidos por pagar, y ventas y movimientos desde julio para los reportes.

No entra en la V1: stock y kardex, conteo físico, variantes de producto, lector de códigos e impresora de tickets, facturación y varias tiendas.

### 2.2 Roles

Dos usuarios iniciales. El administrador puede hacer todo; el operador, lo del día a día de la tienda. Los permisos son por pantalla: quien entra a una pantalla puede hacer todo en ella, incluidas sus anulaciones (P-33).

| Acción | Operador | Administrador |
| --- | --- | --- |
| Abrir y cerrar caja | Sí | Sí |
| Registrar ventas | Sí | Sí |
| Registrar pedidos (compras) | Sí | Sí |
| Pagar pedidos desde la caja | Sí | Sí |
| Crear productos y cambiar su costo (precio de compra) | Sí | Sí |
| Cambiar el precio de venta (catálogo o compra) | Sí | Sí |
| Registrar consumos y ventas al costo | Sí | Sí |
| Otros gastos de caja (comida, transporte…) | Sí | Sí |
| Anular ventas y gastos de caja (en el POS) | Sí | Sí |
| Anular pedidos o pagos (en Compras) | Sí | Sí |
| Finanzas, reportes, usuarios | No | Sí |

### 2.3 Criterio para dejar las planillas

- Datos migrados: productos, saldos de cuentas al día del corte, pedidos por pagar, y ventas y movimientos desde julio. No hay deudas iniciales con personas. Referencia al 23 de octubre: Caja Bs 7.637,90; Bisa Bs 31.562,58; Efectivo Bs 11.352,04.
- Dos semanas en paralelo con las planillas: cada cierre diario da la misma caja final en ambos.
- Los reportes de esas dos semanas coinciden con las tablas dinámicas (ventas por día y por proveedor, movimientos por categoría).
- Respaldo diario de la base funcionando y con una restauración probada.

## 3. Requerimientos por versión

Cada requerimiento indica en qué versión entra; los marcados **V1** son los necesarios para dejar las planillas. Moneda: bolivianos (Bs), 2 decimales, redondeo HALF_UP. Las preguntas pendientes están en la sección 5.

### 3.1 Principios transversales · V1

- **RG-01** Nada se borra físicamente: productos, ventas, pedidos, movimientos y usuarios usan baja lógica o anulación.
- **RG-02** Todo registro guarda auditoría: quién y cuándo creó, modificó, eliminó o anuló. La genera el servidor, nunca el cliente.
- **RG-03** Toda anulación exige motivo y revierte su efecto en caja y cuentas (y en stock desde la V2).
- **RG-04** Las validaciones de negocio (unicidad, saldos) viven en el backend; el frontend solo muestra el error.
- **RG-05** Toda operación que mueve dinero es atómica: todo o nada, en una sola transacción de base de datos.

### 3.2 Catálogo

- **CAT-01** · V1 — Producto con código y nombre únicos (sin distinguir mayúsculas, excluyendo eliminados), categoría, proveedor, descripción, etiquetas, costo y precio de venta.
- **CAT-02** · V1 — Utilidad fija y porcentual calculadas, de solo lectura; se permite venta a pérdida con advertencia.
- **CAT-03** · V1 — Estados: Activo, Inactivo, Eliminado. Solo los activos se venden.
- **CAT-04** · V1 — Categorías y etiquetas con color, nombre único y baja lógica.
- **CAT-06** · V1 — Importación de productos desde la planilla (Grupo → categoría, Proveedor, Etiqueta, Código, Costo, Precio), con listado de filas rechazadas y por qué.
- **CAT-07** · V1 — El operador puede crear productos y cambiar el costo (precio de compra) de un producto existente. También puede cambiar el precio de venta, desde el catálogo o al aceptar el precio sugerido en una compra (COM-03). Todo cambio de costo o precio queda auditado. Desde el 25-10-2026 los cambios desde una compra se registran en `product_price_changes` (origen `PURCHASE`); falta que la edición desde el catálogo registre los suyos (origen `CATALOG`, ya previsto en la tabla).
- **CAT-08** · V1 — Productos vendidos por peso: precio por kilo, para frutos secos y productos del proveedor Granel (P-31), y presentaciones rápidas (100 g = 0,100 kg; 1/2 libra = 0,227 kg); cantidades con hasta 3 decimales en ventas, compras y consumos. Cada producto indica cómo se vende en `products.sale_unit` (`UNIT` o `KILOGRAM`, por defecto `UNIT`; migración `V7__product_sale_unit.sql`), editable en el formulario de Productos («Se vende por»). Si es `KILOGRAM`, el precio de venta es Bs por kilo y la cantidad son kilos; un producto por unidad solo acepta cantidades enteras (el servidor rechaza los decimales con «La cantidad de '<producto>' debe ser entera: se vende por unidad.»).
- **CAT-09** · V2 — Productos con variantes (ej. sabor, tamaño) agrupados bajo un producto base; cada variante con su código, costo y precio (P-33).
- **CAT-10** · V2 — Código de barras por producto y lectura con lector en el POS; impresión de ticket (P-11).
- **CAT-05** · V2 — Cada producto indica si controla stock (sí/no), para admitir servicios o ítems sin inventario.

### 3.3 Compras (pedidos) · V1

- **COM-01** Proveedores con código y nombre únicos, teléfono, descripción y baja lógica (como hoy).
- **COM-02** Pedido con número correlativo, proveedor, fecha, líneas (producto, cantidad, costo unitario), costo de envío, total y comentario. El envío se paga como gasto de Transporte vinculado al pedido, para reportar el costo de cada pedido con y sin envío (P-24). Hecha el 25-10-2026 (migración `V8__purchases.sql`): número `PED-000001` por secuencia (`purchase_number_seq`, índice único); líneas con cantidad `NUMERIC(12,3)` y las reglas de CAT-08 (un producto por unidad solo acepta enteros), copia de nombre, código y costo registrado del producto; `items_total` (lo que se debe al proveedor), `shipping_cost` y `total_amount`. La fecha la elige el usuario (por defecto hoy, hora de Bolivia; no futura). El envío exige la cuenta que lo pagó y genera un `GASTO_MANUAL` con la categoría de sistema Transporte y `financial_transactions.purchase_id`; desde Caja exige caja abierta y baja el efectivo esperado. Proveedor inactivo o producto eliminado se rechazan.
- **COM-03** Desde una línea del pedido se puede crear un producto nuevo. Si el costo de la línea difiere del costo registrado del producto, el sistema muestra una alerta con ambos valores. Si el usuario confirma, el costo del producto se actualiza al de la línea, con auditoría. El cambio no altera la utilidad de las ventas anteriores, que usan el costo guardado en cada línea de venta (POS-05). Al cambiar el costo, el sistema propone un nuevo precio de venta que mantiene la utilidad porcentual actual: precio sugerido = nuevo costo × (1 + utilidad % actual), redondeado hacia arriba al boliviano entero. Quien registra la compra (operador o administrador) puede aceptar el precio sugerido, editarlo o mantener el precio actual, sin aprobación adicional; el cambio queda auditado. Hecha el 25-10-2026: el servidor calcula la sugerencia como `ceil(nuevo costo × precio actual ÷ costo actual)` (`GET /api/purchases/price-suggestion`; sin sugerencia si el costo actual es 0). La actualización viaja en la línea (`updateProductCost`, `newSellPrice`; nulo mantiene el precio) y se aplica en la misma transacción del pedido, con una fila en `product_price_changes` (costo y precio anterior y nuevo, origen `PURCHASE`, pedido, usuario, fecha). El producto nuevo se crea desde el formulario del pedido con la API de productos, que acepta el permiso de Compras.
- **COM-04** Estado de pago calculado a partir de los pagos: Por pagar, A cuenta (pago parcial) o Pagado. Consignación es un estado aparte: queda por pagar hasta que se registre el pago (P-21). Hecha el 25-10-2026: se calcula con lo pagado frente al total de las líneas (el envío no cuenta: se paga al transportista); un pedido en consignación sin pagos se muestra como Consignación. El listado filtra por proveedor, estado de pago y texto.
- **COM-05** Un pedido admite varios pagos. Cada pago tiene fecha, monto y origen: Caja (sesión abierta), Bisa u otra cuenta, o una persona que pagó por el negocio. Hecha el 25-10-2026 para Caja y cuentas (`purchase_payments`): fecha del servidor (T-18), monto mayor a 0 y no mayor al saldo; cada pago genera un `GASTO_MANUAL` con la categoría de sistema Pedido vinculado al pedido. El pedido se bloquea antes que la caja y la cuenta (T-14). Las cuentas se leen con `GET /api/purchases/payment-accounts`. El origen «persona» queda para DEU-02, que crea el registro de personas.
- **COM-06** Un pago desde la caja genera un gasto en Caja con categoría Pedido, vinculado al pedido, y descuenta del efectivo esperado de la sesión. Hecha el 25-10-2026: se vincula a la caja abierta y `ExpectedCashCalculator` lo resta como cualquier gasto de Caja; aparece entre los gastos del POS. Los movimientos de un pedido (pago o envío) no se editan ni anulan desde Finanzas ni desde el POS («Los movimientos de un pedido se gestionan desde Compras.»); su anulación llega con COM-09. Pedido y Transporte son categorías de sistema (`financial_categories.system_key`) que Finanzas no elimina.
- **COM-07** Entrega con tipo (en puerta, terminal, en tienda), estado (pendiente, entregado) y fecha. Hecha el 25-10-2026 (migración `V9__purchase_delivery.sql`): `purchases.delivery_type` (por defecto En tienda), `delivery_status` (por defecto Pendiente) y `delivery_date`. Pendiente: la fecha es la estimada, opcional y puede ser futura. Entregado: la fecha es la de entrega, obligatoria en la base (sin fecha el servidor pone hoy, hora de Bolivia), no futura. En ambos casos no puede ser anterior a la fecha del pedido. Se elige al registrar y se cambia con `PUT /api/purchases/{id}/delivery` (auditoría de modificación, no mueve dinero; un pedido anulado no cambia). El listado filtra por estado de entrega.
- **COM-08** Vista de pedidos por pagar, con saldo pendiente por pedido y por proveedor. Hecha el 25-10-2026: `GET /api/purchases/payables` devuelve los pedidos registrados con saldo (Por pagar, Consignación, A cuenta) agrupados por proveedor (por nombre, pedidos del más antiguo al más reciente), con el saldo de cada proveedor y el total general; filtra por proveedor. Pestaña «Por pagar» en Compras, desde donde se abre el pedido para pagarlo.
- **COM-09** Un pedido o un pago confirmado solo se corrige anulándolo con motivo, lo que revierte el dinero. Lo anula quien entra a Compras, operador o administrador (tabla 2.2, P-33). Hecha el 25-10-2026: `POST /api/purchases/{id}/payments/{paymentId}/void` anula un pago y `POST /api/purchases/{id}/void` el pedido con todos sus pagos activos y su envío. Los gastos vinculados (`financial_transactions.purchase_id`) quedan `ANULADO` con motivo, usuario y fecha, la cuenta recupera el monto y, si eran de Caja, se recalcula el efectivo esperado (`ExpectedCashCalculator`). Bloqueo en orden pedido → caja → cuentas (T-14). Un movimiento de Caja de una caja cerrada no se anula (CAJ-06, T-16): «El pago se hizo en una caja cerrada; no se puede anular.» o «El pedido tiene movimientos de una caja cerrada; no se puede anular.»; en ese caso no cambia nada. La anulación del pedido no revierte el costo ni el precio de los productos actualizados con COM-03: se corrigen desde el catálogo. Un pedido anulado se ve en el listado (filtro Anulado) con su motivo y no admite pagos ni cambios de entrega.

### 3.4 Deudas y cuentas por cobrar · V1

- **DEU-01** Registro de personas a las que el negocio puede deber dinero (ej. Mami).
- **DEU-02** Un pago de pedido hecho por una persona marca el pedido como pagado y aumenta la deuda con esa persona.
- **DEU-03** La devolución a la persona se registra desde Caja o desde una cuenta, y baja la deuda.
- **DEU-04** Saldo adeudado por persona e historial de lo que pagó y de lo que se le devolvió.
- **DEU-05** Una venta al costo a la tienda amiga puede quedar total o parcialmente a crédito; el saldo pendiente queda como cuenta por cobrar a ese cliente.
- **DEU-06** Los cobros posteriores se registran en Caja o en la cuenta QR y bajan el saldo por cobrar.
- **DEU-07** Vista de saldos por cobrar por cliente, con historial de ventas a crédito y cobros.

### 3.5 Consumos y ventas al costo · V1

- **CON-01** Consumo interno: productos y cantidades, valorados al costo del momento. No mueve dinero y se reporta como gasto de la categoría Consumo.
- **CON-02** Venta al costo (tienda amiga): venta desde el POS con precio igual al costo y utilidad cero, marcada como tipo distinto. Se paga en el momento (Caja o QR) o queda total o parcialmente a crédito (DEU-05).
- **CON-03** Los reportes muestran por separado ventas normales, ventas al costo y consumos.
- **CON-04** · V2 — Consumos y ventas al costo generan salidas de stock.

### 3.6 Caja · V1

- **CAJ-01** No se puede vender sin una caja abierta.
- **CAJ-02** Apertura con monto inicial ≥ 0 y comentario opcional.
- **CAJ-03** Efectivo esperado = monto inicial + ventas en efectivo + ingresos de caja − gastos y pagos de pedidos desde caja − retiros.
- **CAJ-04** Cierre con monto contado obligatorio; arqueo opcional por cortes (Bs 200, 100, 50, 20, 10, 5, 2, 1, 0.50, 0.20, 0.10) en "caja" y "reserva".
- **CAJ-05** Se muestra la diferencia (contado − esperado) antes de confirmar; se permite cerrar con diferencia.
- **CAJ-06** Una caja cerrada es inmutable: no se reabre ni recibe movimientos.
- **CAJ-07** Historial de cajas con detalle de apertura, ventas por método, gastos, pagos de pedidos, retiros, cierre y cortes.
- **CAJ-08** Ingreso y retiro de caja ("salida interna") como transferencia entre Caja y otra cuenta (ej. Efectivo), con comentario.
- **CAJ-09** Al cerrar, la diferencia se registra automáticamente como ajuste (sobrante o faltante) en la cuenta Caja, para que su saldo coincida con lo contado.
- **CAJ-10** La apertura propone como monto inicial el cierre anterior; si se cambia, pide comentario.

### 3.7 Punto de venta · V1

- **POS-01** Buscador de productos activos; seleccionar uno repetido suma cantidad en lugar de duplicar la línea.
- **POS-02** Productos rápidos en grupos configurables con orden por arrastrar y soltar.
- **POS-03** Descuento por unidad (0 ≤ descuento ≤ precio) y descuento general (≤ subtotal).
- **POS-04** Pago en efectivo (con cambio y monto recibido ≥ total), QR (a la cuenta QR, hoy Bisa) o mixto.
- **POS-05** La venta guarda una copia del nombre, código, precio **y costo** de cada producto al vender, para que la utilidad histórica no cambie.
- **POS-06** Numeración de venta única y correlativa.
- **POS-07** Anulación con motivo mientras la caja de la venta esté abierta (operador y administrador, P-33).
- **POS-08** Detalle de venta de solo lectura (ítems, descuentos, pagos, auditoría de anulación), reutilizable en POS e historial.
- **POS-09** Historial de ventas con filtros por fecha, estado, tipo de venta, método de pago y usuario.
- **POS-10** Venta por peso: el POS acepta cantidades decimales y ofrece las presentaciones rápidas del producto (100 g, 1/2 libra). Hecha el 25-10-2026 en `register-sale`: un producto por kilo entra al carrito con 100 g (0,100 kg), los botones +/− suman o restan 100 g, la cantidad se escribe en kilos con hasta 3 decimales, y la línea ofrece «100 g» y «1/2 libra» (0,227 kg); el precio se muestra «/kg» y la línea cuenta como un ítem. Los productos por unidad siguen con enteros. Ver `specs/sales.md`.

### 3.8 Dinero (finanzas) · V1

- **FIN-01** Cuentas iniciales Caja, Bisa y Efectivo, cada una con color y saldo inicial registrado como movimiento.
- **FIN-02** La cuenta Caja es única, no se elimina y solo recibe ingresos por ventas, ajustes de cierre o transferencias.
- **FIN-03** Exactamente una cuenta marcada para cobros QR en todo momento.
- **FIN-04** Una cuenta solo se elimina con saldo 0 y si no es Caja ni la cuenta QR.
- **FIN-05** Categorías de ingreso y gasto, iniciando con las de la planilla: Venta, Otro ingreso, Pedido, Consumo, Comida, Transporte, Otro gasto, Retiro caja, Depósito, Utilidad (retiro del dueño) y Ajuste.
- **FIN-06** Ingresos y gastos manuales, transferencias con validación de saldo suficiente, anulación auditada.
- **FIN-07** Cada venta genera automáticamente sus ingresos (efectivo a Caja, QR a la cuenta QR); esos movimientos no se editan desde Finanzas.
- **FIN-08** El saldo de una cuenta es la suma de sus movimientos activos (ver T-04).
- **FIN-09** Resumen con saldo por cuenta y saldo consolidado.
- **FIN-10** Reportes: ventas por día; ventas, cantidad y utilidad por proveedor y mes; movimientos por categoría y mes; totales de ventas y utilidad. Exportables a Excel.
- **FIN-11** Migración histórica: las ventas (hoja Ventas) y los movimientos (hoja Transacc.) desde julio se importan como registros históricos marcados, que alimentan los reportes sin alterar saldos ni cajas. Cada día de la hoja Ventas se importa como una venta histórica con sus líneas por producto, costo y utilidad, sin caja ni pagos (P-32).

### 3.9 Usuarios y permisos · V1

- **USR-01** Usuario con nombre completo y nombre de usuario inmutable; contraseña con hash BCrypt.
- **USR-02** Permisos controlados en el frontend (menú, rutas) y en el backend (cada endpoint).
- **USR-03** Un usuario dado de baja pierde el acceso de inmediato, aunque tenga una sesión abierta.
- **USR-04** Nadie puede darse de baja a sí mismo ni eliminar al último administrador.
- **USR-05** Dos roles iniciales, Operador y Administrador, con los permisos de la tabla 2.2. Hecha el 25-10-2026: el rol se elige al crear o editar el usuario (ya no hay matriz por submenú) y agrupa permisos por pantalla definidos en código (`Permission`, `Role`; detalle en `specs/users.md`). Quien entra a una pantalla puede hacer todo en ella (P-33). El operador entra a POS, historial de ventas, historial de cajas, productos, categorías, etiquetas, proveedores y pedidos (`PURCHASES`, desde el 25-10-2026); el administrador, además, a finanzas y usuarios. No se puede quitar el rol ni dar de baja al último administrador activo.
- **USR-07** · V1 — El login se bloquea tras varios intentos fallidos por usuario o por IP dentro de una ventana de tiempo; durante el bloqueo se rechaza con 429 y un mensaje en español que no revela si el usuario existe, aunque la contraseña sea correcta. Un login exitoso reinicia el contador del usuario (T-19).
- **USR-06** · V1 — El primer administrador se crea desde variables de entorno solo si no hay usuarios, y debe cambiar la contraseña en su primer ingreso: el login lo informa (`mustChangePassword`), el backend rechaza con 403 toda otra llamada hasta el cambio y el frontend lo lleva a la pantalla de cambio. Cualquier usuario puede cambiar su contraseña (`POST /api/auth/change-password`) indicando la actual; la nueva tiene entre 8 y 100 caracteres y no puede ser igual a la actual ni al nombre de usuario (T-03).

### 3.10 Inventario · V2

- **INV-01** Existencia actual por producto con control de stock, calculada desde un libro de movimientos (kardex), nunca editada a mano.
- **INV-02** Movimientos: carga inicial, entrada por compra, salida por venta, venta al costo y consumo, reingreso por anulación, ajustes con motivo (merma, rotura, vencimiento).
- **INV-03** Kardex por producto y rango de fechas, con saldo después de cada movimiento.
- **INV-04** Stock mínimo por producto y listado de productos bajo mínimo.
- **INV-05** Conteo físico: se registra lo contado, se muestra la diferencia y, al confirmar, se generan los ajustes.

## 4. Requerimientos técnicos y de seguridad

Decisión: la V1 se construye sobre el proyecto actual, con el mismo stack, y estos problemas técnicos se corrigen en el camino, empezando por una revisión del código (T-00). Para salir a producción con la V1 bastan los marcados V1; el resto puede esperar sin riesgo para el dinero.

| ID | Hallazgo en el sistema actual | Requerimiento | Versión |
| --- | --- | --- | --- |
| T-00 | La revisión inicial cubrió documentación, esquema, specs y estructura, pero no la lógica de los servicios Java ni la ejecución de las pruebas. | Hecha el 25-10-2026: backend 181 pruebas y frontend 277 pruebas, todas pasan. Se revisaron caja, ventas, finanzas y seguridad; las correcciones son T-13 a T-19. | V1 |
| T-01 | No hay Flyway en `build.gradle` y `ddl-auto=update`: Hibernate altera la base solo y los scripts V1–V9 no son la fuente real. | Migraciones versionadas (Flyway) como única forma de cambiar el esquema; `ddl-auto=validate`. Como no hay datos de producción, las migraciones actuales pueden consolidarse en una migración inicial limpia. Hecha el 25-10-2026: V1–V9 consolidadas en `backend/src/main/resources/db/migration/V1__init.sql` (se eliminó `database/`). Se corrigió `session_number` (era `SERIAL` int; la entidad usa `Long`, ahora `BIGINT` identity) y se agregaron los `CHECK` de dominio de cada enum. Todo cambio de esquema futuro es una migración `V<n>__*.sql` nueva; nunca se edita una ya aplicada. | V1 |
| T-02 | `schema.sql` dice "actualizado tras V1" y le faltan finanzas y permisos. | El esquema se genera desde las migraciones; no se mantiene a mano. Hecha el 25-10-2026: `schema.sql` ya no existe; el esquema sale solo de las migraciones Flyway. | V1 |
| T-03 | Secreto JWT por defecto y contraseña de base escritos en `application.properties`. El README ya no documenta contraseñas, pero `DataInitializer` crea en cada arranque `admin`/`12345admin` y `seller`/`12345seller` si no existen, y el vendedor recibe todos los permisos. | Secretos solo por variables de entorno; el primer admin se crea con contraseña obligatoria de cambiar. Hecha el 25-10-2026: `DB_PASSWORD` y `JWT_SECRET` (mín. 32 caracteres) solo vienen del entorno; si faltan, el arranque falla antes de crear beans con un mensaje que nombra cada variable (`RequiredEnvironmentValidator`). Para desarrollo, `.env.example` en la raíz: copiado a `.env` (ignorado por git) lo leen `docker compose` y el backend (`spring.config.import`); las variables reales del entorno tienen prioridad. `DataInitializer` ya no crea `admin` ni `seller`: solo si no hay ningún usuario crea el primer admin con `ADMIN_USERNAME`/`ADMIN_PASSWORD` (mín. 8 caracteres; si faltan, el arranque falla) y queda con `must_change_password` (migración `V2__user_must_change_password.sql`). Ver USR-06. Las bases de desarrollo que ya tenían `admin`/`seller` los conservan: cambiar o dar de baja esas cuentas a mano. | V1 |
| T-05 | Los ingresos y gastos manuales se editan cambiando el monto y la cuenta (`updateTransaction`), lo que reescribe saldos sin rastro. | Movimientos de dinero inmutables: se corrigen con anulación + nuevo registro. Hecha el 25-10-2026: `updateTransaction` solo edita descripción, notas y categoría (validando INGRESO/GASTO) y ya no toca saldos de cuentas ni el efectivo esperado; no bloquea cuentas, y bloquea la caja del movimiento solo para rechazar los de una caja cerrada (orden de T-14: movimiento, luego caja). Si el pedido cambia el monto (comparado ya normalizado a escala 2) o la cuenta origen responde «El monto/La cuenta de un movimiento registrado no se puede editar; si hay un error, anúlelo y regístrelo de nuevo.». Siguen los rechazos de ventas, anulados, transferencias, saldo inicial y caja cerrada; por eso tampoco se mueve un gasto a Caja o fuera de ella al editar (reemplaza esa parte de T-16). El formulario de edición de Finanzas ya enviaba el monto y la cuenta originales. Pruebas en `FinancialTransactionServiceTest`. | V1 |
| T-06 | `sale_number` sin restricción de unicidad y calculado como cantidad de ventas de la caja + 1 (dos ventas simultáneas reciben el mismo número); estados como texto libre sin `CHECK`; nada impide en la base dos cajas abiertas. Desde T-01, `V1__init.sql` ya tiene los `CHECK` de estados y el índice único parcial de caja ABIERTA; falta lo de `sale_number`. | Unicidad y dominios de estado garantizados en la base: número de venta por secuencia, índice único de `sale_number` e índice único parcial de caja con estado ABIERTA. Hecha el 25-10-2026: migración `V4__sale_number_sequence.sql` crea la secuencia `sale_number_seq` y el índice único `uq_sales_sale_number`; los números repetidos existentes conservan el primero y los demás reciben sufijo `-2`, `-3`… Las ventas nuevas se numeran `VTA-000001`, `VTA-000002`… (global, ya no por caja), formato que no choca con el anterior `VTA-<caja>-<n>`. | V1 |
| T-07 | `sale_items` no guarda el costo del producto al vender. | Snapshot de costo por línea (POS-05). Hecha el 25-10-2026: migración `V5__sale_item_unit_cost.sql` agrega `sale_items.unit_cost` (`NUMERIC(12,2)`, obligatorio, `CHECK >= 0`); `SaleServiceImpl` lo llena con el `buy_price` del producto al registrar la venta, y la entidad lo marca `updatable = false`, así que ni un cambio de costo (CAT-07, COM-03) ni la anulación lo alteran. Ventas ya existentes: como no hay datos de producción (T-01), las líneas de desarrollo toman el costo actual del producto; las ventas históricas de las planillas traen su propio costo al importarse (FIN-11). El costo no se expone en la respuesta del detalle de venta: el operador ve ventas y no necesita el costo; los reportes de utilidad (FIN-10) lo leerán en el servidor cuando se construyan. Pruebas en `SaleServiceTest` y `SaleItemRepositoryTest` (contra PostgreSQL). | V1 |
| T-08 | Rol `ADMIN/SELLER` convive con la matriz de permisos; `UserController` acepta `hasRole('ADMIN')` como atajo. | Un solo modelo: roles Operador y Administrador que agrupan permisos. Hecha el 25-10-2026: `Role` (`OPERATOR`, `ADMINISTRATOR`) agrupa el enum `Permission`, con un permiso por pantalla (P-33); las autoridades de Spring son los permisos del rol y se quitó `hasRole('ADMIN')`. Migración `V3__operator_administrator_roles.sql`: `ADMIN` o quien tenía `SECURITY_USERS` pasa a Administrador, el resto a Operador, y se elimina `user_permissions`. Las bases de desarrollo con el `seller` heredado (tenía todos los permisos) lo dejan como Administrador: pasarlo a Operador o darlo de baja a mano. | V1 |
| T-10 | Cuentas "BISA" y "Cuenta Joaco" sembradas dentro de una migración. | Las cuentas Caja, Bisa y Efectivo se cargan en la migración de datos, no en el esquema. Desde T-01 el esquema ya no siembra BISA ni Joaco. La cuenta Caja y la categoría Ventas siguen en `V1__init.sql` como filas de sistema, porque sin ellas la venta falla (T-17). | V1 |
| T-11 | Cantidades enteras (`quantity INT`). | Cantidades decimales para venta por peso (P-03, CAT-08). Hecha el 25-10-2026 para ventas: migración `V6__sale_item_decimal_quantity.sql` cambia `sale_items.quantity` a `NUMERIC(12,3)` (las enteras existentes se convierten sin pérdida) con `CHECK quantity > 0`. `SaleItem`, `SaleItemRequest` y `SaleItemResponse` usan `BigDecimal`; la API acepta cantidades mayores a 0 con hasta 3 decimales y `SaleServiceImpl` las guarda en escala 3 (rechaza 0, negativas o con más decimales). Subtotal, bruto y descuento de línea siguen en escala 2 `HALF_UP`; el descuento de línea se calcula como bruto − subtotal para que las líneas sumen el total de la venta. El frontend ya trataba la cantidad como `number`; la entrada decimal y las presentaciones rápidas en el POS se hicieron en POS-10, con `products.sale_unit` (CAT-08) y la regla de que un producto por unidad solo acepta enteros. Compras usa la misma escala desde el 25-10-2026 (`purchase_items.quantity`, COM-02); consumos la usarán cuando existan (CON). Pruebas en `SaleServiceTest` y `SaleItemRepositoryTest` (contra PostgreSQL). | V1 |
| T-13 | Solo `UserController` tiene `@PreAuthorize`. Cualquier usuario autenticado puede llamar por API a finanzas, anulación de ventas, catálogo y caja, aunque el menú se lo oculte. | Cada endpoint exige el permiso de la tabla 2.2 (USR-02, USR-05), con pruebas de acceso denegado para el operador. Hecha el 25-10-2026: `@PreAuthorize` en todos los controllers con el permiso de la pantalla dueña del endpoint; los endpoints que otra pantalla solo lee también aceptan esa pantalla (Productos lee proveedores, categorías y etiquetas; el POS lee productos y categorías de gasto). Un 403 responde «No tiene permiso para realizar esta acción». El POS anula gastos con `POST /api/finances/transactions/cash-expense/{id}/void`, que solo acepta gastos de caja de la caja abierta; la anulación general de movimientos sigue siendo de Finanzas. `EndpointAuthorizationTest` recorre todos los endpoints: 2xx para el administrador, 403 para el operador en finanzas y usuarios, y falla si un endpoint no tiene `@PreAuthorize` o falta en la matriz. Frontend con las mismas claves (`PERMISSIONS` en `navigation.config.ts`, rutas y `permission.guard.ts`). | V1 |
| T-14 | Saldos de cuenta (`current_balance`) y totales de caja se actualizan leyendo y reescribiendo la entidad, sin bloqueo ni `@Version`. Dos ventas o movimientos simultáneos pueden perder una actualización y desalinear el saldo. La apertura de caja verifica "no hay otra abierta" sin bloqueo. | Toda operación de dinero bloquea la cuenta y la caja que toca (`SELECT … FOR UPDATE` o versión optimista con reintento) dentro de su transacción, con prueba concurrente. Hecha el 25-10-2026: bloqueo pesimista (`@Lock(PESSIMISTIC_WRITE)`) en los repositorios. Orden fijo para evitar deadlocks: primero el movimiento (anulación o edición), luego la caja, luego las cuentas ordenadas por id en una sola consulta (`findAllByIdForUpdate`, `findSaleAccountsForUpdate`). Lo aplican ventas, anulación de ventas, cierre de caja, el endpoint heredado de ventas de caja, y alta, edición y anulación de movimientos de finanzas (incluye gastos de caja y transferencias); los movimientos que tocan la cuenta Caja bloquean también la caja abierta. La apertura se apoya en el índice único parcial de caja ABIERTA: si dos aperturas pasan la verificación a la vez, la segunda falla al guardar y responde el mismo «Ya existe una caja abierta». Prueba `MoneyConcurrencyIntegrationTest` contra PostgreSQL: 8 ventas simultáneas (saldo de Caja, totales de caja y números distintos), 8 transferencias cruzadas entre dos cuentas, y 8 aperturas simultáneas (queda una sola); limpia sus datos y se omite si la base ya tiene una caja abierta. | V1 |
| T-15 | El efectivo esperado se calcula como inicial + ventas en efectivo − gastos manuales de Caja. Las transferencias desde Caja se vinculan a la caja pero la consulta las excluye, y las transferencias hacia Caja ni se vinculan: el saldo de la cuenta Caja y el efectivo esperado divergen. La fórmula está copiada en cinco lugares (caja, ventas, finanzas ×3). | Un único cálculo de efectivo esperado según CAJ-03, que incluye transferencias desde y hacia Caja (CAJ-08) y pagos de pedidos (COM-06), usado por todos los servicios y cubierto con pruebas. Hecha el 25-10-2026: `ExpectedCashCalculator` (inicial + ventas en efectivo + transferencias hacia Caja − gastos de Caja − transferencias desde Caja, solo movimientos activos de la caja) lo usan caja, ventas y finanzas; el POS muestra el valor del servidor y ya no lo recalcula, y su lista de gastos solo muestra `GASTO_MANUAL`. Las transferencias hacia Caja se vinculan a la caja abierta. Se mantiene el orden de bloqueo de T-14. Pruebas: `ExpectedCashCalculatorTest`, sumas contra PostgreSQL en `FinancialTransactionRepositoryTest` (excluye anulados, otras cuentas y otras cajas) y cierre con transferencias en `CashSessionServiceTest`. Desde el 25-10-2026 los pagos de pedidos y los envíos pagados desde Caja entran como gastos de Caja (COM-06). | V1 |
| T-16 | Un gasto o transferencia desde Caja sin caja abierta se registra igual, sin caja. El cliente puede enviar `cashSessionId` de una caja cerrada o de otra cuenta. Editar o anular un gasto de una caja cerrada recalcula el efectivo esperado de esa caja (rompe CAJ-06). | Los movimientos de la cuenta Caja exigen caja abierta y el servidor los vincula a ella; los movimientos de una caja cerrada no se editan ni anulan, se corrigen en la caja actual. Hecha el 25-10-2026: gasto o transferencia desde o hacia Caja sin caja abierta responde «Los movimientos de la cuenta Caja requieren una caja abierta.»; el `cashSessionId` del cliente se ignora (los movimientos fuera de Caja quedan sin caja). La edición ya no cambia la cuenta (T-05), así que un gasto no entra ni sale de Caja al editarse. Editar o anular un movimiento de una caja cerrada responde «No se pueden editar/anular movimientos de una caja cerrada; corríjalos en la caja actual.». Pruebas en `FinancialTransactionServiceTest`. | V1 |
| T-17 | Si no existe la cuenta Caja, la cuenta QR o la categoría Venta, la venta se guarda sin su movimiento financiero, sin error. El endpoint heredado `POST /api/cash-sessions/current/sales` registra ventas sin ítems ni movimientos financieros. | La venta falla si falta su cuenta o categoría (RG-05); se elimina el endpoint heredado. Hecha el 25-10-2026: `SaleServiceImpl` resuelve la categoría Venta y bloquea las cuentas de venta antes de guardar nada (mismo orden de T-14: caja, luego cuentas); sin categoría Venta responde «No existe la categoría Venta; no se puede registrar la venta.», un cobro en efectivo sin cuenta Caja «No existe la cuenta Caja; …» y un cobro QR sin cuenta QR «No hay una cuenta marcada para cobros QR; …». Solo se exige la cuenta que usa el método de pago (una venta QR no necesita Caja). Se eliminó `POST /api/cash-sessions/current/sales` (`registerSaleInCurrentSession`, `SaleRequest`, sus pruebas y su fila en `EndpointAuthorizationTest`) y en el frontend `CashService.registerSale` y el modal sin uso `register-sale-modal`. Pruebas en `SaleServiceTest`. | V1 |
| T-18 | Finanzas no normaliza montos a escala 2 HALF_UP (alta, edición y saldo inicial de cuenta). La anulación de movimientos acepta motivo vacío y permite anular el saldo inicial. La fecha del movimiento la envía el cliente. | Montos con escala 2 en el servidor, motivo obligatorio en toda anulación (RG-03), saldo inicial no anulable y fecha puesta por el servidor (salvo la importación histórica, FIN-11). Hecha el 25-10-2026: alta de movimientos y saldo inicial de cuenta se guardan con escala 2 (la edición ya no cambia el monto, T-05) `HALF_UP`; un monto que redondea a 0.00 se rechaza. La anulación de movimientos (Finanzas y gastos de caja del POS) y de ventas exige motivo: sin cuerpo, vacío o en blanco responde «El motivo de anulación es obligatorio.»; el POS y Finanzas ya no lo marcan como opcional. El saldo inicial no se anula ni se edita («El saldo inicial de una cuenta no se puede anular/editar.»). La fecha del movimiento es la hora del servidor al registrarlo y la edición la conserva: `transactionDate` salió de `FinancialTransactionRequest` y `CashExpenseRequest`, y los formularios de ingreso, gasto y transferencia ya no piden fecha. La importación histórica (FIN-11) tendrá su propia vía. Se mantiene el orden de bloqueo de T-14 y `ExpectedCashCalculator` (T-15). Pruebas en `FinancialTransactionServiceTest`, `FinancialAccountServiceTest` y `SaleServiceTest`. | V1 |
| T-19 | El login no limita intentos fallidos y el token dura 24 h. La baja de usuario sí corta el acceso al instante (el filtro consulta el usuario en cada pedido). | Límite de intentos de login por usuario e IP antes de exponer el VPS (NF-02, NF-05). Hecha el 25-10-2026: ver USR-07. Contador en memoria (`LoginAttemptServiceImpl`): hay un solo servidor en el VPS y perder los contadores al reiniciar solo acorta un bloqueo, así que no hace falta tabla. Por defecto 5 fallos por usuario o 20 por IP en 15 minutos bloquean 15 minutos (`security.login.*`, variables `LOGIN_MAX_ATTEMPTS_PER_USER`, `LOGIN_MAX_ATTEMPTS_PER_IP`, `LOGIN_WINDOW`, `LOGIN_LOCKOUT`); el límite por IP es mayor porque los equipos de la tienda comparten una IP pública. La IP es la de la conexión (`getRemoteAddr`); `X-Forwarded-For` se ignora salvo con `FORWARD_HEADERS_STRATEGY=native`, que se activa solo detrás del proxy inverso del VPS (Tomcat confía en el encabezado solo desde direcciones internas). El token pasa de 24 h a 12 h por defecto (`JWT_EXPIRATION`, acepta `12h` o milisegundos): cubre un turno completo sin pedir otro ingreso y vence durante la noche; al vencer, el frontend vuelve al login. | V1 |
| T-04 | `current_balance` y totales de caja se guardan aparte de los movimientos y pueden desalinearse (confirmado en T-00: ver T-14 a T-17). Mientras tanto, las pruebas de dinero comparan el saldo guardado con la suma de movimientos. | Saldos derivados del libro de movimientos, o caché con reporte de conciliación. | V2 |
| T-09 | Auditoría guarda el nombre de usuario como texto. | Auditoría con referencia al id del usuario. | V2 |
| T-12 | Una sola caja abierta en todo el sistema. | Caja por punto de venta si hay más de uno (P-01). | V3 |

Requerimientos no funcionales, todos para la V1:

- **NF-01** Respaldo diario automático de la base, con una restauración probada antes de salir.
- **NF-02** HTTPS obligatorio y expiración de sesión configurable.
- **NF-03** Pruebas automáticas de las reglas de dinero (ventas, anulaciones, cierres, pagos de pedidos, deudas) antes de cada despliegue.
- **NF-04** El POS registra una venta en menos de 2 segundos con los ~1.200 productos.
- **NF-05** Servidor de producción en un VPS en la nube (P-22), con HTTPS y respaldo diario guardado fuera del VPS.

## 5. Preguntas

No quedan preguntas que bloqueen la V1. La unidad de precio por peso quedó decidida el 25-10-2026: precio por kilo (CAT-08). Antes de migrar falta marcar en la planilla qué productos se venden por peso y llevar su precio a Bs por kilo. Las preguntas pendientes son para la V2 y la V3. Cada respuesta se convierte en requerimientos de la sección 3.

### 5.1 Pendientes

| ID | Pregunta | Supuesto mientras tanto | Se necesita antes de |
| --- | --- | --- | --- |
| P-06 | ¿Se permite vender sin stock? | — | V2 |
| P-07 | ¿Costo por último precio de compra o costo promedio? | Último precio de compra. | V2 |
| P-09 | ¿Devoluciones de clientes después de cerrar la caja? | No. | V2 |
| P-13 | ¿Otros métodos de pago (tarjeta, transferencia)? | Solo efectivo y QR. | V2 |
| P-33 | ¿Qué variantes hay (sabor, tamaño) y comparten costo y precio? | Hasta la V2, cada variante sigue siendo un producto aparte. | V2 |
| P-01 | ¿Habrá más tiendas, depósitos o cajas? | Una tienda, una caja. | V3 |
| P-10 | ¿Hay que emitir factura (SIN) o solo nota de venta? | Solo nota de venta. | V3 |
| P-12 | ¿Se vende a crédito a clientes registrados (además de la tienda amiga)? | No. | V3 |

### 5.2 Respondidas

| ID | Pregunta | Respuesta |
| --- | --- | --- |
| P-02 | ¿Cuántos productos hay? | ~1.200 en la planilla Productos. |
| P-03 | ¿Se vende por peso? | Sí (100 g, 1/2 libra, etc.): pasa a la V1 (CAT-08, POS-10). |
| P-04 | ¿Hay variantes? | Sí; se agrupan en la V2 (CAT-09). |
| P-05 | ¿Se controla lote o vencimiento? | No. |
| P-11 | ¿Lector de código de barras o impresora de tickets? | No en la V1; sí en la V2 (CAT-10). |
| P-14 | ¿El POS debe funcionar sin internet? | No. |
| P-15 | ¿Qué roles hay? | Operador y Administrador (tabla 2.2). |
| P-16 | ¿Se migran datos? | Sí: productos, saldos de cuentas, pedidos por pagar, y ventas y movimientos desde julio. Sin deudas iniciales. |
| P-17 | ¿Se reescribe o se trabaja sobre el proyecto actual? | Se trabaja sobre el proyecto actual, con el mismo stack (Spring Boot + Angular + PostgreSQL), previa revisión del código (T-00). |
| P-18 | ¿La tienda amiga paga en el momento? | A veces queda debiendo: hacen falta cuentas por cobrar en la V1 (DEU-05 a DEU-07). |
| P-19 | ¿El operador registra consumos, ventas al costo y otros gastos de caja? | Sí, los tres. |
| P-20 | ¿A quién se le debe hoy? | A nadie. La planilla puede ajustarse antes de migrar. |
| P-21 | ¿Cómo se paga la consignación? | Queda por pagar hasta que se registre el pago. |
| P-22 | ¿Dónde se aloja el sistema? | En la nube, en un VPS. |
| P-23 | ¿Se migran ventas históricas? | Sí, desde julio, para tener los reportes (FIN-11). |
| P-24 | ¿Cómo se trata el envío de un pedido? | Gasto de Transporte aparte, siempre vinculado a su pedido para los reportes. |
| P-25 | ¿El operador puede crear productos? | Sí. |
| P-26 | Cuando una persona paga un pedido por el negocio, ¿qué es? | Una deuda del negocio con esa persona. |
| P-27 | ¿Cómo se valoran los consumos? | Al costo. Las salidas a la tienda amiga también van al costo, como venta separada. |
| P-28 | ¿Las notas sueltas de la planilla (Tía Neida, "Mami me debe") entran en la V1? | No; se ignoran al migrar. |
| P-29 | Si cambia el costo en una compra, ¿quién aprueba el nuevo precio de venta sugerido? | Quien registra la compra, incluido el operador. |
| P-30 | ¿El operador puede cambiar el precio de venta desde el catálogo? | Sí. |
| P-31 | ¿Qué productos se venden por peso? | Frutos secos y productos del proveedor Granel. Precio por kilo (decidido el 25-10-2026, CAT-08); antes de migrar se marcan en la planilla y se lleva su precio a Bs por kilo. |
| P-32 | ¿Cómo se importan las ventas históricas? | Una venta histórica por día con sus líneas por producto, costo y utilidad, sin caja ni pagos. |
| P-33 | ¿Los permisos son por acción o por pantalla? | Por pantalla: quien entra a una pantalla puede hacer todo en ella, incluidas las anulaciones (25-10-2026). El operador anula ventas y gastos de caja en el POS, y pedidos y pagos en Compras. |

## 6. Hoja de ruta

Las planillas se dejan al terminar la fase 2, después de dos semanas en paralelo; el inventario (V2) y las extensiones (V3) llegan después sin frenar la operación. Ninguna fase avanza sin pasar su compuerta, probada con datos reales de la tienda.

1. **Fase 0 · Decisiones y base técnica** — Revisión del código (T-00). Flyway, secretos por entorno, VPS con HTTPS y respaldo diario. Roles Operador y Administrador con permisos por pantalla (USR-05; T-01 a T-19 marcados V1).
   - Compuerta: revisión del código hecha y servidor de producción listo.
2. **Fase 1 · V1: lo que falta para reemplazar las planillas** — Compras, deudas y cuentas por cobrar, consumos y ventas al costo, venta por peso, ajustes de caja, costo por línea, historial de ventas, reportes e importación (COM, DEU, CON, CAT-08, CAJ-08 a 10, FIN-10 y 11).
   - Compuerta: productos (con unidad de venta y precio por kilo revisados), saldos, pedidos e históricos desde julio migrados.
3. **Fase 2 · Dos semanas en paralelo con las planillas — salida a producción** — Se registra todo en ambos lados; cada cierre diario y cada reporte deben dar lo mismo que la planilla. Al pasar la compuerta se dejan las planillas.
   - Compuerta: 14 cierres seguidos iguales y reportes que coinciden con las tablas dinámicas.
4. **Fase 3 · V2: inventario** — Stock calculado desde compras, ventas y consumos; conteo físico, variantes, código de barras y tickets (INV-01 a INV-05, CAT-05, CAT-09, CAT-10, CON-04, T-04). Resolver P-06, P-07, P-09, P-13 y P-33.
   - Compuerta: un conteo físico completo cuadra con el sistema.
5. **Fase 4 · V3: extensiones** — Facturación (P-10), clientes y crédito (P-12), varias tiendas o cajas (P-01), reportes avanzados.

### Cómo se trabaja cada módulo

1. Revisar en este archivo los requerimientos y preguntas del módulo.
2. Escribir `specs/<modulo>.md` con los requerimientos del módulo como escenarios (cuando / entonces), usando los mismos IDs, y una lista de tareas al final.
3. Validar los escenarios con casos reales de la planilla (un pedido a cuenta, un pago de Mami, un cierre con sobrante).
4. Construir con pruebas: las reglas de dinero tienen pruebas automáticas antes de dar el módulo por terminado.
5. Probar con datos reales; lo que falle se agrega aquí como requerimiento nuevo.

### Próximos pasos

- [x] Revisar el código actual (T-00) y anotar las correcciones en la sección 4.
- [ ] Antes de migrar: marcar en la planilla los frutos secos y productos Granel como venta por peso y llevar su precio a Bs por kilo.
- [ ] Contratar el VPS y preparar HTTPS y el respaldo diario.
- [x] Spec de Compras (`specs/purchases.md`) y COM-01 a COM-06 construidos (25-10-2026).
- [x] Compras: entrega (COM-07), vista de pedidos por pagar (COM-08) y anulación de pedidos y pagos (COM-09) (25-10-2026).
- [ ] Escribir las specs de Deudas y cuentas por cobrar (incluye el pago de pedidos por una persona, DEU-02) y de Consumos y ventas al costo.
