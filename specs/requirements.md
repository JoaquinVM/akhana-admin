# Akhana — Requerimientos y hoja de ruta

Fuente de verdad del proyecto: **qué** debe hacer el sistema, en qué estado está y qué falta. El **cómo** (endpoints,
migraciones, clases, pruebas, validaciones con la planilla) vive en `specs/<modulo>.md`, con los mismos IDs, y la
historia de cada cambio en git.

Stack: Spring Boot 4.1 (Java 21), Angular 21, PostgreSQL 17.

## 1. Estado

Avance de la V1 al 29-10-2026. Al cerrar cada sesión se actualiza la fila del módulo (una línea) en el mismo commit.

| Módulo | IDs | Estado V1 | Falta para la V1 | Spec |
| --- | --- | --- | --- | --- |
| Base técnica | T-00 a T-19, RG-01 a RG-05 | ✅ Completo | — | §4 |
| Login y usuarios | USR-01 a USR-07 | ✅ Completo | — | `users.md` |
| Catálogo | CAT-01 a CAT-04, CAT-06 a CAT-08, CAT-11 | ✅ Completo | — | `product-import.md`, `product-presentations.md` |
| Proveedores y compras | COM-01 a COM-09 | ✅ Completo | — | `purchases.md` |
| Deudas y cuentas por cobrar | DEU-01 a DEU-07 | ✅ Completo | — | `debts.md` |
| Consumos y ventas al costo | CON-01 a CON-03 | ✅ Completo | — | `consumptions.md` |
| Caja | CAJ-01 a CAJ-10 | ✅ Completo | — | `cash.md` |
| POS y ventas | POS-01 a POS-13 | ✅ Completo | — | `sales.md` |
| Finanzas | FIN-01 a FIN-09 | ✅ Completo | — | `finance.md` |
| Reportes | FIN-10, CON-03 | ✅ Completo | — | `reports.md` |
| Dashboard | DSH-01 a DSH-04 | ✅ Completo | Revisar los números con datos reales en producción | `dashboard.md` |
| Migración | CAT-06, FIN-11 | ✅ Completo | Corrida en producción con corte 27-10 y 0 rechazadas; falta el pago de `PED-H00487` | `migration.md` |
| Producción | NF-01, NF-02, NF-05, NF-06 | ✅ Completo | `v1.0.1` en el VPS con HTTPS, respaldo a B2, restauración probada y despliegue continuo por tag (NF-06) funcionando; falta Tailscale en la PC de caja y el celular | `deploy.md` |
| Inventario | INV, CAT-05, CAT-09, CAT-10, CON-04, COM-10 | V2 | — | — |

### 1.1 Las planillas que se reemplazan

La operación vive hoy en dos Google Sheets: **Akhana Recaudaciones** y **Akhana Pedidos**. Ventas de unos
Bs 2.000–4.000 por día, 880 productos y ~420 pedidos desde abril. Ni las planillas ni la V1 llevan stock.

| Hoja | Qué registra | En el sistema |
| --- | --- | --- |
| Productos | Grupo, proveedor, etiqueta, código, costo, precio | Catálogo, con importación (CAT-06) |
| Ventas | Una fila por producto y día, con costo y utilidad | POS: cada venta es un ticket con el costo de cada línea (POS-05) |
| Cierre de Caja + Conteo | Caja inicial, ingresos, ventas, gastos, salida interna, caja final, sobrante; conteo por billetes | Caja: apertura propuesta, ingresos y retiros, cierre con cortes y ajuste (CAJ) |
| Transacc. + Resumen | Movimientos de Caja, Bisa y Efectivo por categoría | Finanzas (FIN) |
| Pedidos + Detalle Pedidos | Pedidos a proveedores con envío, pago, entrega y líneas | Compras (COM) y Deudas (DEU) |
| Consumos | Productos consumidos en la tienda | Consumos (CON-01) |
| Resumen Ventas / Diario / Transacc. | Tablas dinámicas por día, proveedor, mes y categoría | Reportes (FIN-10, CON-03) |
| HOJITA | Borrador diario | No se migra (P-41) |

## 2. Versión 1: reemplazo de planillas

La V1 sale a producción cuando reemplaza las dos planillas, sin inventario. El stock llega en la V2 y se calcula a partir de las compras, ventas y consumos que la V1 ya registra por producto, sin recapturar nada.

### 2.1 Alcance

Entra en la V1:

- Catálogo con los productos importados de la planilla, incluida la venta por peso con precio por kilo y presentaciones.
- POS y caja, con ingresos y retiros de caja, ajuste automático por sobrante o faltante y caja inicial propuesta desde el cierre anterior.
- Compras (pedidos) con líneas, envío, pagos parciales, estado de pago y de entrega, y pago desde la caja.
- Deudas con personas que pagan pedidos por el negocio (ej. "Mami pagó") y cuentas por cobrar de la tienda amiga.
- Consumos internos y ventas al costo, separados de las ventas normales.
- Ventas sin identificar y de productos de terceros.
- Finanzas con las cuentas Caja, Bisa y Efectivo.
- Historial de ventas y los reportes que hoy dan las tablas dinámicas.
- Dos roles: operador y administrador.
- Migración de productos, saldos y todos los datos de las dos planillas desde su primer registro.

No entra en la V1: stock y kardex, conteo físico, variantes de producto, lector de códigos e impresora de tickets, facturación y varias tiendas.

### 2.2 Roles

El administrador puede hacer todo; el operador, lo del día a día de la tienda. Los permisos son por pantalla: quien entra a una pantalla puede hacer todo en ella, incluidas sus anulaciones (P-33).

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
| Devoluciones a personas y cobros a la tienda amiga, y sus anulaciones (en Deudas) | Sí | Sí |
| Dashboard, finanzas, reportes, migración histórica, usuarios | No | Sí |

### 2.3 Criterio para dejar las planillas

- Datos migrados: productos, saldos de cuentas al día del corte y todo el historial de las dos planillas (FIN-11). La única deuda inicial con personas es lo vendido de Mami y todavía no pagado (POS-13, P-42).
- Dos semanas en paralelo con las planillas: cada cierre diario da la misma caja final en ambos.
- Los reportes de esas dos semanas coinciden con las tablas dinámicas (ventas por día y por proveedor, movimientos por categoría).
- Respaldo diario de la base funcionando y con una restauración probada.

## 3. Requerimientos por versión

Cada requerimiento indica su versión; los **V1** son los necesarios para dejar las planillas. Moneda: bolivianos (Bs), 2 decimales, redondeo HALF_UP; los montos se leen igual en la pantalla y en los mensajes del servidor, con formato boliviano (Bs 1.234,50), y las fechas son días de la tienda (America/La_Paz).

### 3.1 Principios transversales · V1

- **RG-01** Nada se borra físicamente: productos, ventas, pedidos, movimientos y usuarios usan baja lógica o anulación.
- **RG-02** Todo registro guarda auditoría: quién y cuándo creó, modificó, eliminó o anuló. La genera el servidor, nunca el cliente.
- **RG-03** Toda anulación exige motivo (sin largo mínimo) y revierte su efecto en caja y cuentas (y en stock desde la V2).
- **RG-04** Las validaciones de negocio (unicidad, saldos) viven en el backend; el frontend solo muestra el error. Todo error llega al usuario en español, sin detalles técnicos; dos guardados iguales a la vez responden un conflicto, no un error del servidor.
- **RG-05** Toda operación que mueve dinero es atómica: todo o nada, en una sola transacción de base de datos.

### 3.2 Catálogo

- **CAT-01** · V1 — Producto con código y nombre únicos (sin distinguir mayúsculas, excluyendo eliminados), categoría, proveedor, descripción, etiquetas, costo y precio de venta. El costo puede ser 0, con el aviso «Costo 0: toda la venta cuenta como utilidad.»; el precio de venta, redondeado a 2 decimales, debe ser mayor a 0.
- **CAT-02** · V1 — Utilidad fija y porcentual calculadas, de solo lectura; se permite venta a pérdida con advertencia.
- **CAT-03** · V1 — Estados: Activo, Inactivo, Eliminado. Solo los activos se venden.
- **CAT-04** · V1 — Categorías y etiquetas con color, nombre único y baja lógica.
- **CAT-06** · V1 — Importación de productos desde el `.xlsx` de la planilla (Grupo → categoría, Proveedor, Etiqueta, Código, Costo, Precio, «Se vende por»), con pasos Revisar e Importar, listado descargable de filas rechazadas y por qué, y sin duplicar al repetirla. Solo crea productos; las libras de frutos secos entran como presentaciones (CAT-11). Detalle en `product-import.md`.
- **CAT-07** · V1 — El operador puede crear productos y cambiar su costo y su precio de venta, desde el catálogo o al aceptar el precio sugerido en una compra (COM-03). Todo cambio de costo o precio queda registrado con quién y cuándo. Falta una pantalla que muestre ese historial.
- **CAT-08** · V1 — Productos vendidos por peso (frutos secos y Granel, P-31): cada producto indica si se vende por unidad o por kilo; por kilo, el precio es Bs por kilo y la cantidad son kilos con hasta 3 decimales en ventas, compras y consumos. Un producto por unidad solo acepta cantidades enteras.
- **CAT-11** · V1 — Presentaciones de productos por peso: hasta 6 por producto, con nombre, peso fijo y precio fijo opcional (sin él, peso × precio por kilo). El costo siempre es peso × costo por kilo. El POS y Consumos ofrecen las presentaciones y «Otro peso»; los reportes suman kilos. Detalle en `product-presentations.md`.
- **CAT-05** · V2 — Cada producto indica si controla stock (sí/no), para admitir servicios o ítems sin inventario.
- **CAT-09** · V2 — Productos con variantes (ej. sabor, tamaño) agrupados bajo un producto base; cada variante con su código, costo y precio (P-33).
- **CAT-10** · V2 — Código de barras por producto y lectura con lector en el POS; impresión de ticket (P-11).

### 3.3 Compras (pedidos) · V1

Detalle en `purchases.md`.

- **COM-01** Proveedores con código y nombre únicos, teléfono, descripción y baja lógica.
- **COM-02** Pedido con número correlativo, proveedor, fecha, líneas (producto, cantidad, costo unitario), costo de envío, total y comentario. El envío se paga como gasto de Transporte vinculado al pedido (P-24).
- **COM-03** Desde una línea del pedido se puede crear un producto nuevo. Si el costo de la línea difiere del registrado, se muestra una alerta; al confirmar, el costo del producto se actualiza y se propone un precio de venta que mantiene la utilidad porcentual, redondeado hacia arriba al boliviano. Quien registra la compra acepta, edita o mantiene el precio (P-29). Las ventas anteriores no cambian (POS-05).
- **COM-04** Estado de pago calculado desde los pagos: Por pagar, A cuenta o Pagado (el envío no cuenta). Consignación queda por pagar hasta que se registre el pago (P-21).
- **COM-05** Un pedido admite varios pagos, cada uno con fecha, monto (no mayor al saldo) y origen: Caja, otra cuenta o una persona que pagó por el negocio (DEU-02).
- **COM-06** Un pago desde la caja genera un gasto de Caja con categoría Pedido vinculado al pedido y baja el efectivo esperado. Los movimientos de un pedido solo se gestionan desde Compras.
- **COM-07** Entrega con tipo (en puerta, terminal, en tienda), estado (pendiente, entregado) y fecha.
- **COM-08** Vista de pedidos por pagar con saldo por pedido, por proveedor y total.
- **COM-09** Un pedido o un pago solo se corrige anulándolo con motivo, lo que revierte el dinero; no se anula si tiene movimientos de una caja cerrada.
- **COM-10** · V2 — Cajas vinculadas a su unidad (Caja x12 = 12 × Barra Chia) con «Abrir caja». En la V1 la caja es un producto aparte.

### 3.4 Deudas y cuentas por cobrar · V1

Detalle en `debts.md`. Los saldos no se guardan: se calculan desde los registros activos y nunca quedan negativos.

- **DEU-01** Personas a las que el negocio puede deber dinero (ej. Mami), con nombre único, teléfono, descripción y baja lógica; no se dan de baja con deuda.
- **DEU-02** Un pago de pedido hecho por una persona marca el pedido como pagado (o a cuenta) y aumenta la deuda con ella, sin movimiento de dinero.
- **DEU-03** La devolución a la persona se registra desde Caja o una cuenta y baja la deuda sin superarla. Es un gasto con categoría Pedido (P-34), o Pago a tercero si se le paga lo de sus ventas (POS-13), sin superar lo pendiente de ellas; solo se anula desde Deudas.
- **DEU-04** Saldo por persona e historial de pagos y devoluciones con el saldo después de cada registro. Al devolver se propone el concepto según lo pendiente (P-48).
- **DEU-05** Una venta al costo puede quedar total o parcialmente a crédito de un cliente registrado; solo la venta al costo admite crédito (P-12).
- **DEU-06** Los cobros se registran en Caja o en la cuenta QR, bajan el saldo sin superarlo y son un ingreso con categoría Venta (P-34).
- **DEU-07** Saldos por cobrar por cliente, con historial de ventas al costo y cobros; las pagadas en el momento se ven «pagadas» sin cambiar el saldo (P-49).

### 3.5 Consumos y ventas al costo · V1

Detalle en `consumptions.md`.

- **CON-01** Consumo interno: productos y cantidades valorados al costo del momento, con «Para quién» y comentario. No mueve dinero ni pasa por Caja (P-35, P-36).
- **CON-02** Venta al costo desde el POS: precio igual al costo, utilidad cero, sin descuentos ni productos con costo 0, con comentario opcional. Es para cualquiera (P-39), se paga en el momento o queda a crédito (DEU-05) y propone «Sin cliente» (P-49). Regalos y roturas son ventas al costo.
- **CON-03** Los reportes muestran por separado ventas normales, ventas al costo y consumos.
- **CON-04** · V2 — Consumos y ventas al costo generan salidas de stock.

### 3.6 Caja · V1

Detalle en `cash.md`.

- **CAJ-01** No se puede vender sin una caja abierta.
- **CAJ-02** Apertura con monto inicial ≥ 0 y comentario opcional.
- **CAJ-03** Efectivo esperado = monto inicial + ventas en efectivo + ingresos de caja − gastos y pagos de pedidos desde caja − retiros. Los ajustes de apertura y cierre no cuentan. Una salida de Caja que supera el esperado se avisa sin bloquear.
- **CAJ-04** Cierre con monto contado obligatorio; arqueo opcional por cortes (Bs 200, 100, 50, 20, 10, 5, 2, 1, 0,50, 0,20, 0,10) en "caja" y "reserva", que si se carga debe sumar el monto contado.
- **CAJ-05** Se muestra la diferencia (contado − esperado) antes de confirmar; se permite cerrar con diferencia.
- **CAJ-06** Una caja cerrada es inmutable: no se reabre ni recibe movimientos.
- **CAJ-07** Historial de cajas con detalle de apertura, ventas por método, gastos, pagos de pedidos, retiros, cierre y cortes.
- **CAJ-08** Ingreso y retiro de caja ("salida interna") desde el POS como transferencia entre Caja y otra cuenta, con comentario obligatorio; se anula solo mientras su caja esté abierta.
- **CAJ-09** Al cerrar, la diferencia se registra automáticamente como ajuste (sobrante o faltante) en la cuenta Caja, para que su saldo coincida con lo contado.
- **CAJ-10** La apertura propone como monto inicial el cierre anterior (en la primera caja, el saldo de Caja); si se cambia, el comentario es obligatorio y la diferencia con el saldo de Caja se registra como ajuste, que no se anula.

### 3.7 Punto de venta · V1

Detalle en `sales.md`.

- **POS-01** Buscador de productos activos; seleccionar uno repetido suma cantidad en lugar de duplicar la línea. Enter agrega el producto del código escrito. Todos los buscadores de productos buscan por código, nombre, proveedor, categoría y etiqueta, sin tildes, en cualquier orden y con errores de tipeo, y muestran todos los resultados por relevancia, con el proveedor de cada uno.
- **POS-02** Productos rápidos en grupos configurables con orden por arrastrar y soltar.
- **POS-03** Descuento por unidad (0 ≤ descuento ≤ precio) y descuento general (≤ subtotal).
- **POS-04** Pago en efectivo (con cambio y monto recibido ≥ total), QR (a la cuenta QR, hoy Bisa) o mixto, con efectivo y QR mayores a 0.
- **POS-05** La venta guarda una copia del nombre, código, precio **y costo** de cada producto al vender, para que la utilidad histórica no cambie.
- **POS-06** Numeración de venta única y correlativa. En desarrollo los números saltan porque las pruebas consumen las secuencias; en producción no.
- **POS-07** Anulación con motivo mientras la caja de la venta esté abierta (operador y administrador, P-33).
- **POS-08** Detalle de venta de solo lectura (ítems, descuentos, pagos, auditoría de anulación), reutilizable en POS e historial.
- **POS-09** Historial de ventas paginado con filtros por fecha, estado, tipo de venta, método de pago y usuario.
- **POS-10** Venta por peso: cantidades decimales en kilos y presentaciones del producto (CAT-11).
- **POS-11** Línea libre: productos especiales del catálogo, sin inventario, que piden nombre y precio al vender. Sin descuento por unidad, productos rápidos, presentaciones ni venta al costo; el descuento general se reparte solo entre las líneas normales (P-44). Llevan categoría y proveedor reales (P-50).
- **POS-12** Venta sin identificar: la línea con «Producto sin identificar» queda «por asignar»; después, desde el historial, se le asigna una sola vez el producto real y su costo actual, mostrando antes la utilidad que tendrá la línea, con auditoría y sin cambiar el monto ni la caja (única excepción a T-07). La que nunca se identifique se cierra como «sin identificar definitivo»: suma al total en una columna aparte «Sin identificar» y no cuenta en la utilidad.
- **POS-13** Venta de productos de terceros (hoy, de Mami): producto especial con su dueña (DEU-01); el costo de la línea es igual al precio y cada venta suma a la deuda con ella, que se le paga con «Pago a tercero» (DEU-03). Los reportes la muestran aparte, fuera de las ventas y la utilidad de la tienda.

### 3.8 Dinero (finanzas) · V1

Detalle en `finance.md`, `reports.md` y `migration.md`.

- **FIN-01** Cuentas Caja, Bisa y Efectivo, cada una con color y saldo inicial registrado como movimiento. El saldo al corte se carga una sola vez desde Finanzas → Cuentas, mientras la cuenta no tenga otros movimientos.
- **FIN-02** La cuenta Caja es única, no se elimina y solo recibe ingresos por ventas, ajustes de cierre, transferencias o cobros a la tienda amiga (DEU-06).
- **FIN-03** Exactamente una cuenta marcada para cobros QR en todo momento.
- **FIN-04** Una cuenta solo se elimina con saldo 0 y si no es Caja ni la cuenta QR.
- **FIN-05** Categorías de ingreso y gasto, iniciando con las de la planilla: Venta, Otro ingreso, Pedido, Consumo, Comida, Transporte, Otro gasto, Retiro caja, Depósito, Utilidad (retiro del dueño), Ajuste y Pago a tercero (POS-13).
- **FIN-06** Ingresos y gastos manuales, transferencias con validación de saldo suficiente (también al anular una transferencia o un ingreso), anulación auditada. Un movimiento manual no usa las categorías que registra el sistema (Pedido, Pago a tercero, Ajuste, Venta); Transporte sí.
- **FIN-07** Cada venta genera automáticamente sus ingresos (efectivo a Caja, QR a la cuenta QR); esos movimientos no se editan desde Finanzas.
- **FIN-08** El saldo de una cuenta es la suma de sus movimientos activos (ver T-04).
- **FIN-09** Resumen con saldo por cuenta y saldo consolidado.
- **FIN-10** Reportes (solo Administrador): ventas por día; ventas, cantidad y utilidad por proveedor y mes; movimientos por categoría y mes; totales de ventas y utilidad por mes; consumos al costo. Cada uno exportable a Excel. Detalle en `reports.md`.
- **FIN-11** Migración histórica, una sola vez, de todos los datos de las dos planillas (ventas, movimientos, cierres de caja, pedidos con líneas, pagos y entrega, y consumos) desde su primer registro. Entran marcados «Histórico», de solo lectura, alimentan los reportes y no alteran saldos ni cajas; los saldos de hoy entran como saldo al corte (FIN-01). Cada día de Ventas es una venta histórica (P-32). Detalle en `migration.md`.

### 3.9 Usuarios y permisos · V1

Detalle en `users.md`.

- **USR-01** Usuario con nombre completo y nombre de usuario inmutable y único sin distinguir mayúsculas; contraseña de 8 a 100 caracteres, distinta del nombre de usuario, con hash BCrypt.
- **USR-02** Permisos controlados en el frontend (menú, rutas) y en el backend (cada endpoint).
- **USR-03** Un usuario dado de baja pierde el acceso de inmediato, aunque tenga una sesión abierta.
- **USR-04** Nadie puede darse de baja a sí mismo ni eliminar al último administrador, tampoco con dos bajas a la vez.
- **USR-05** Dos roles, Operador y Administrador, con permisos por pantalla según la tabla 2.2 (P-33).
- **USR-06** El primer administrador se crea desde variables de entorno solo si no hay usuarios y debe cambiar la contraseña en su primer ingreso. Cualquier usuario puede cambiar su contraseña indicando la actual.
- **USR-07** El login se bloquea tras varios intentos fallidos por usuario o por IP, con un mensaje que no revela si el usuario existe (T-19).

### 3.10 Inventario · V2

- **INV-01** Existencia actual por producto con control de stock, calculada desde un libro de movimientos (kardex), nunca editada a mano.
- **INV-02** Movimientos: carga inicial, entrada por compra, salida por venta, venta al costo y consumo, reingreso por anulación, ajustes con motivo (merma, rotura, vencimiento).
- **INV-03** Kardex por producto y rango de fechas, con saldo después de cada movimiento.
- **INV-04** Stock mínimo por producto y listado de productos bajo mínimo.
- **INV-05** Conteo físico: se registra lo contado, se muestra la diferencia y, al confirmar, se generan los ajustes.

### 3.11 Dashboard · V1

Detalle en `dashboard.md`. Solo Administrador (permiso propio, P-33) y su pantalla de inicio; el operador sigue entrando al POS.

- **DSH-01** Indicadores del período (hoy, semana, mes o rango): ventas, utilidad, margen, tickets y ticket promedio de las ventas normales, con el cambio contra el período anterior; al costo y consumos aparte.
- **DSH-02** Ventas y utilidad por día del período junto al período anterior, y cómo se cobró (efectivo, QR, crédito).
- **DSH-03** Top 10 de productos y de proveedores por venta o por utilidad, y gastos por categoría sin transferencias.
- **DSH-04** Ahora mismo: estado de la caja abierta, saldo por cuenta y total, por pagar a proveedores, por devolver a personas y por cobrar.

## 4. Requerimientos técnicos y de seguridad

Salieron de la revisión del código (T-00, 25-10-2026). Los V1 están hechos; el detalle de cada uno está en la spec indicada.

| ID | Requerimiento | Versión | Detalle |
| --- | --- | --- | --- |
| T-00 | Revisión del código existente (servicios, esquema, pruebas). | V1 | — |
| T-01 | Flyway como única forma de cambiar el esquema; `ddl-auto=validate`. Nunca se edita una migración aplicada. | V1 | `CLAUDE.md` |
| T-02 | El esquema sale solo de las migraciones; no hay `schema.sql` a mano. | V1 | — |
| T-03 | Secretos solo por variables de entorno; sin ellas el backend no arranca. Sin usuarios sembrados salvo el primer admin (USR-06). | V1 | `users.md` |
| T-05 | Movimientos de dinero inmutables: solo se editan descripción, notas y categoría; lo demás se corrige con anulación y nuevo registro. | V1 | `finance.md` |
| T-06 | Número de venta por secuencia con índice único; una sola caja abierta garantizada por la base. | V1 | `sales.md` |
| T-07 | Costo de cada línea de venta guardado al vender y no modificable (POS-05). | V1 | `sales.md` |
| T-08 | Un solo modelo de permisos: roles que agrupan permisos por pantalla. | V1 | `users.md` |
| T-10 | Las cuentas Bisa y Efectivo no se siembran con saldo en el esquema; el saldo al corte se carga desde la app (FIN-01). | V1 | `finance.md` |
| T-11 | Cantidades decimales (3 decimales) en ventas, compras y consumos. | V1 | `sales.md` |
| T-13 | Cada endpoint exige el permiso de su pantalla, con prueba que recorre todos los endpoints. | V1 | `users.md` |
| T-14 | Toda operación de dinero bloquea la caja y las cuentas que toca, en orden fijo (movimiento o documento → caja → persona o cliente → cuentas por id), con prueba concurrente. | V1 | `cash.md` |
| T-15 | Un único cálculo de efectivo esperado (CAJ-03) usado por todos los servicios. | V1 | `cash.md` |
| T-16 | Los movimientos de la cuenta Caja exigen caja abierta y se vinculan a ella; los de una caja cerrada no se editan ni anulan. | V1 | `cash.md` |
| T-17 | La venta falla si falta su cuenta o su categoría (RG-05). | V1 | `sales.md` |
| T-18 | Montos con escala 2 en el servidor, motivo obligatorio en toda anulación, saldo inicial no anulable y fecha puesta por el servidor (salvo FIN-11). | V1 | `finance.md` |
| T-19 | Límite de intentos de login por usuario e IP y token de 12 h que deja de valer al cambiar la contraseña (USR-07). | V1 | `users.md` |
| T-04 | Saldos derivados del libro de movimientos, o caché con reporte de conciliación. | V2 | — |
| T-09 | Auditoría con referencia al id del usuario, no solo su nombre. | V2 | — |
| T-12 | Caja por punto de venta si hay más de uno (P-01). | V3 | — |

Requerimientos no funcionales, todos para la V1:

- **NF-01** Respaldo diario automático de la base, con una restauración probada antes de salir.
- **NF-02** HTTPS obligatorio y expiración de sesión configurable.
- **NF-03** Pruebas automáticas de las reglas de dinero (ventas, anulaciones, cierres, pagos de pedidos, deudas) antes de cada despliegue.
- **NF-04** El POS registra una venta en menos de 2 segundos con todo el catálogo. Medido en local: la venta responde en 20–50 ms y el catálogo activo (590 KB, 60 KB comprimido desde la cuarta revisión) en 35–70 ms.
- **NF-05** Servidor de producción en un VPS en la nube (P-22), con HTTPS y respaldo diario guardado fuera del VPS.
- **NF-06** Despliegue continuo: un tag de versión que pasa las pruebas automáticas se despliega solo en el VPS, sin abrirle puertos, y si falla vuelve solo a la versión anterior.

## 5. Preguntas

Cada respuesta se convierte en requerimientos de la sección 3; el detalle de cómo se aplicó está en la spec del módulo.

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
| P-02 | ¿Cuántos productos hay? | 880 filas en la hoja Productos (se estimaban ~1.200). |
| P-03 | ¿Se vende por peso? | Sí: V1 (CAT-08, POS-10). |
| P-04 | ¿Hay variantes? | Sí; se agrupan en la V2 (CAT-09). |
| P-05 | ¿Se controla lote o vencimiento? | No. |
| P-11 | ¿Lector de código de barras o impresora de tickets? | No en la V1; sí en la V2 (CAT-10). |
| P-14 | ¿El POS debe funcionar sin internet? | No. |
| P-15 | ¿Qué roles hay? | Operador y Administrador (tabla 2.2). |
| P-16 | ¿Se migran datos? | Sí: productos, saldos y todos los datos de las dos planillas desde su primer registro (FIN-11). |
| P-17 | ¿Se reescribe o se trabaja sobre el proyecto actual? | Sobre el actual, con el mismo stack, previa revisión del código (T-00). |
| P-18 | ¿La tienda amiga paga en el momento? | A veces queda debiendo: cuentas por cobrar en la V1 (DEU-05 a DEU-07). |
| P-19 | ¿El operador registra consumos, ventas al costo y otros gastos de caja? | Sí, los tres. |
| P-20 | ¿A quién se le debe hoy? | A nadie, salvo lo vendido de Mami y no pagado (POS-13). |
| P-21 | ¿Cómo se paga la consignación? | Queda por pagar hasta que se registre el pago. |
| P-22 | ¿Dónde se aloja el sistema? | En un VPS Contabo (US East) con Ubuntu, accesible solo por Tailscale (HTTPS `.ts.net`), con respaldo diario cifrado en Backblaze B2 (`deploy.md`). |
| P-23 | ¿Se migran ventas históricas? | Sí, todas (FIN-11). |
| P-24 | ¿Cómo se trata el envío de un pedido? | Gasto de Transporte aparte, vinculado a su pedido. |
| P-25 | ¿El operador puede crear productos? | Sí. |
| P-26 | Cuando una persona paga un pedido por el negocio, ¿qué es? | Una deuda del negocio con esa persona. |
| P-27 | ¿Cómo se valoran los consumos? | Al costo. Las salidas a la tienda amiga también, como venta separada. |
| P-28 | ¿Las notas sueltas de la planilla entran en la V1? | No; se ignoran al migrar. |
| P-29 | Si cambia el costo en una compra, ¿quién aprueba el precio sugerido? | Quien registra la compra, incluido el operador. |
| P-30 | ¿El operador puede cambiar el precio de venta desde el catálogo? | Sí. |
| P-31 | ¿Qué productos se venden por peso? | Frutos secos y Granel, con precio por kilo; se marcan en la columna «Se vende por» de la planilla. |
| P-32 | ¿Cómo se importan las ventas históricas? | Una venta histórica por día con sus líneas, costo y utilidad, sin caja ni pagos. |
| P-33 | ¿Los permisos son por acción o por pantalla? | Por pantalla, incluidas las anulaciones. |
| P-34 | ¿Con qué categoría se reportan la devolución a una persona y el cobro a la tienda amiga? | Devolución como Pedido y cobro como Venta. |
| P-35 | ¿Los consumos se valoran al costo aunque la planilla use el precio de venta? | Sí; en la fase 2 se comparan por cantidad, no por monto. |
| P-36 | ¿Se guarda quién consumió? | Sí, como texto libre opcional «Para quién». |
| P-37 | ¿El almuerzo es un consumo? | No: es un gasto de caja Comida. |
| P-38 | ¿Los encargos de la tienda amiga (Tía Neida) son pedido + venta al costo a crédito + cobro? | Sí, los tres pasos. |
| P-39 | ¿La venta al costo es para cualquiera y regalos y roturas son consumos? | Venta al costo para cualquiera, con comentario; crédito solo a un cliente registrado. Regalos y roturas son ventas al costo, no consumos. |
| P-40 | ¿Cómo se migra la hoja Consumos, que se superpone con los gastos Consumo de Transacc.? | Se corrigió la planilla para que cada consumo quede solo en Consumos, con código y costo (`migration.md`). |
| P-41 | ¿Se migra HOJITA? | No; lo pendiente se pasó a las otras hojas. |
| P-42 | ¿Qué pasa con ventas antiguas de productos que no están en Productos? | Son filas de Ventas sin código: se identificaron en la planilla; las de Mami son de tercero y el resto, sin identificar definitivo (`migration.md`). |
| P-43 | ¿Cómo se migran los pagos de pedidos sin contarlos dos veces con Transacc.? | Transacc. es la única fuente de dinero; los pagos históricos entran sin movimiento, solo para el estado de pago. Se compararon por mes antes de importar. |
| P-44 | ¿Qué descuentos y asignaciones admiten las líneas libres? | Sin descuento por unidad; descuento general repartido solo entre las líneas normales; asignación solo a productos por unidad; tipo y dueña fijos. |
| P-45 | ¿Cómo se migran los pedidos 1 a 341, sin líneas? | Solo con la cabecera. |
| P-46 | ¿De dónde sale el proveedor de los pedidos? | De una columna nueva Proveedor en la planilla, confirmada por el dueño. |
| P-47 | ¿Cómo se migran los pedidos «A Cuenta» sin monto pagado? | Con la columna nueva Monto pagado; el resto queda por pagar. |
| P-48 | ¿Qué concepto se propone al devolverle a Mami? | Primero «Pago a tercero» por lo pendiente de sus ventas; si no queda, «Pedido». |
| P-49 | ¿Qué cliente propone el cobro de la venta al costo? | «Sin cliente»; al dar crédito se propone el único cliente activo. |
| P-50 | ¿Los productos especiales llevan categoría y proveedor? | Sí, reales (los de Mami: Inciensos y Sagrada Madre). |

## 6. Hoja de ruta

Las planillas se dejan al terminar la fase 2, después de dos semanas en paralelo; el inventario (V2) y las extensiones (V3) llegan después sin frenar la operación. Ninguna fase avanza sin pasar su compuerta, probada con datos reales de la tienda.

1. ✅ **Fase 0 · Decisiones y base técnica** — Revisión del código, Flyway, secretos, roles (T-00 a T-19, USR-05).
   - Compuerta pendiente: servidor de producción listo (NF-01, NF-02, NF-05).
2. 🟡 **Fase 1 · V1** — Compras, deudas, consumos, venta por peso, caja, historial, reportes, importación y migración. Construida.
   - Compuerta: productos, saldos y todo el historial de las dos planillas migrados en producción.
3. **Fase 2 · Dos semanas en paralelo con las planillas** — Se registra todo en ambos lados.
   - Compuerta: 14 cierres seguidos iguales y reportes que coinciden con las tablas dinámicas.
4. **Fase 3 · V2: inventario** — INV-01 a INV-05, CAT-05, CAT-09, CAT-10, CON-04, COM-10, T-04. Resolver P-06, P-07, P-09, P-13 y P-33.
   - Compuerta: un conteo físico completo cuadra con el sistema.
5. **Fase 4 · V3: extensiones** — Facturación (P-10), clientes y crédito (P-12), varias tiendas o cajas (P-01), reportes avanzados.

### Próximos pasos

Solo los pasos abiertos; al terminar uno se borra de aquí (queda en git).

- [ ] Instalar Tailscale en la PC de caja y el celular de la tienda (`deploy.md`).
- [ ] Después de migrar: registrar el pago de `PED-H00487` (Savia Herbolarita, Bs 122) hecho por Mami (DEU-02).
- [ ] Fase 2: comparar cada cierre y los reportes con las planillas durante dos semanas.
- [ ] Reportes, fase 2: comparar dos semanas con las tablas dinámicas (`reports.md`).

### Cómo se trabaja cada módulo

1. Revisar en este archivo los requerimientos y preguntas del módulo.
2. Escribir `specs/<modulo>.md` con los requerimientos del módulo como escenarios (cuando / entonces), usando los mismos IDs, y una lista de tareas al final.
3. Validar los escenarios con casos reales de la planilla.
4. Construir con pruebas: las reglas de dinero tienen pruebas automáticas antes de dar el módulo por terminado.
5. Probar con datos reales; lo que falle se agrega aquí como requerimiento nuevo.
6. Al cerrar: actualizar la fila del tablero y borrar el paso hecho de «Próximos pasos». El detalle va a la spec del módulo, no aquí.
