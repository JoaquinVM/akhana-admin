# Akhana — Requerimientos y hoja de ruta

Fuente de verdad del proyecto. Se mantiene en este archivo; las specs por módulo (`specs/<modulo>.md`) usan los mismos IDs.
Origen: documento de claude.ai del 24–25 de octubre de 2026, exportado aquí al pasar la orquestación a Claude Code.

## 1. Lo que existe hoy

Akhana Admin ya resuelve bien la venta en POS, la caja y el dinero. **Ni el sistema ni las planillas actuales llevan stock**, así que la primera versión puede reemplazar las planillas sin inventario: lo que falta son compras (pedidos) con sus pagos, algunos movimientos de caja, consumos y reportes.

Stack: Spring Boot 4.1 (Java 21), Angular 21, PostgreSQL 17.

| Módulo | Qué hace hoy | Estado | Qué falta |
| --- | --- | --- | --- |
| Login y usuarios | JWT, BCrypt, baja lógica, 12 permisos por submenú, protección del último admin | Completo | Roles Operador/Administrador y permisos por acción |
| Catálogo | Productos (código, nombre, categoría, proveedor, etiquetas, precio compra/venta, utilidad), categorías, etiquetas | Completo | Venta por peso; importación |
| Proveedores | ABM con baja lógica | Completo | Base para compras |
| Caja | Una sola caja abierta global, apertura, cierre con arqueo por cortes en Bs (caja + reserva), diferencia | Completo | Ingresos/retiros de caja, ajuste por sobrante |
| POS / ventas | Carrito, productos rápidos por grupos, descuento por ítem y general, pago efectivo/QR/mixto, cambio, anulación solo con caja abierta | Completo | Costo por línea, venta al costo, venta por peso |
| Historial de ventas | Ruta `/sales` existe pero es una página vacía | Pendiente | Construir |
| Finanzas | Cuentas, cuenta QR única, categorías ingreso/gasto, ingresos/gastos manuales, transferencias, anulación, resumen | Completo | Cuentas Caja/Bisa/Efectivo, reportes |
| Compras | Menú solo con Proveedores; los "gastos de caja" del POS hacen de compras | No existe | Construir |
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

Dos usuarios iniciales. El administrador puede hacer todo; el operador, lo del día a día de la tienda.

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
| Anular ventas, pedidos o pagos | No | Sí |
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
- **CAT-07** · V1 — El operador puede crear productos y cambiar el costo (precio de compra) de un producto existente. También puede cambiar el precio de venta, desde el catálogo o al aceptar el precio sugerido en una compra (COM-03). Todo cambio de costo o precio queda auditado.
- **CAT-08** · V1 — Productos vendidos por peso: precio por unidad de peso, para frutos secos y productos del proveedor Granel (P-31), y presentaciones rápidas (100 g, 1/2 libra); cantidades con hasta 3 decimales en ventas, compras y consumos.
- **CAT-09** · V2 — Productos con variantes (ej. sabor, tamaño) agrupados bajo un producto base; cada variante con su código, costo y precio (P-33).
- **CAT-10** · V2 — Código de barras por producto y lectura con lector en el POS; impresión de ticket (P-11).
- **CAT-05** · V2 — Cada producto indica si controla stock (sí/no), para admitir servicios o ítems sin inventario.

### 3.3 Compras (pedidos) · V1

- **COM-01** Proveedores con código y nombre únicos, teléfono, descripción y baja lógica (como hoy).
- **COM-02** Pedido con número correlativo, proveedor, fecha, líneas (producto, cantidad, costo unitario), costo de envío, total y comentario. El envío se paga como gasto de Transporte vinculado al pedido, para reportar el costo de cada pedido con y sin envío (P-24).
- **COM-03** Desde una línea del pedido se puede crear un producto nuevo. Si el costo de la línea difiere del costo registrado del producto, el sistema muestra una alerta con ambos valores. Si el usuario confirma, el costo del producto se actualiza al de la línea, con auditoría. El cambio no altera la utilidad de las ventas anteriores, que usan el costo guardado en cada línea de venta (POS-05). Al cambiar el costo, el sistema propone un nuevo precio de venta que mantiene la utilidad porcentual actual: precio sugerido = nuevo costo × (1 + utilidad % actual), redondeado hacia arriba al boliviano entero. Quien registra la compra (operador o administrador) puede aceptar el precio sugerido, editarlo o mantener el precio actual, sin aprobación adicional; el cambio queda auditado.
- **COM-04** Estado de pago calculado a partir de los pagos: Por pagar, A cuenta (pago parcial) o Pagado. Consignación es un estado aparte: queda por pagar hasta que se registre el pago (P-21).
- **COM-05** Un pedido admite varios pagos. Cada pago tiene fecha, monto y origen: Caja (sesión abierta), Bisa u otra cuenta, o una persona que pagó por el negocio.
- **COM-06** Un pago desde la caja genera un gasto en Caja con categoría Pedido, vinculado al pedido, y descuenta del efectivo esperado de la sesión.
- **COM-07** Entrega con tipo (en puerta, terminal, en tienda), estado (pendiente, entregado) y fecha.
- **COM-08** Vista de pedidos por pagar, con saldo pendiente por pedido y por proveedor.
- **COM-09** Un pedido o un pago confirmado solo se corrige anulándolo con motivo (administrador), lo que revierte el dinero.

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
- **POS-07** Anulación con motivo mientras la caja de la venta esté abierta (administrador).
- **POS-08** Detalle de venta de solo lectura (ítems, descuentos, pagos, auditoría de anulación), reutilizable en POS e historial.
- **POS-09** Historial de ventas con filtros por fecha, estado, tipo de venta, método de pago y usuario.
- **POS-10** Venta por peso: el POS acepta cantidades decimales y ofrece las presentaciones rápidas del producto (100 g, 1/2 libra).

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
- **USR-05** Dos roles iniciales, Operador y Administrador, con permisos por acción según la tabla 2.2.

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
| T-00 | La revisión inicial cubrió documentación, esquema, specs y estructura, pero no la lógica de los servicios Java ni la ejecución de las pruebas. | Revisión del código antes de construir: correr las pruebas de backend y frontend, revisar cómo los servicios calculan los saldos de caja y cuentas, y listar las correcciones en esta tabla. | V1 |
| T-01 | No hay Flyway en `build.gradle` y `ddl-auto=update`: Hibernate altera la base solo y los scripts V1–V9 no son la fuente real. | Migraciones versionadas (Flyway) como única forma de cambiar el esquema; `ddl-auto=validate`. Como no hay datos de producción, las migraciones actuales pueden consolidarse en una migración inicial limpia. | V1 |
| T-02 | `schema.sql` dice "actualizado tras V1" y le faltan finanzas y permisos. | El esquema se genera desde las migraciones; no se mantiene a mano. | V1 |
| T-03 | Secreto JWT por defecto y contraseña de base escritos en `application.properties`; usuarios semilla `admin`/`12345admin` documentados en el README. | Secretos solo por variables de entorno; el primer admin se crea con contraseña obligatoria de cambiar. | V1 |
| T-05 | Los ingresos manuales se editan cambiando el monto. | Movimientos de dinero inmutables: se corrigen con anulación + nuevo registro. | V1 |
| T-06 | `sale_number` sin restricción de unicidad; estados como texto libre sin `CHECK`. | Unicidad y dominios de estado garantizados en la base. | V1 |
| T-07 | `sale_items` no guarda el costo del producto al vender. | Snapshot de costo por línea (POS-05). | V1 |
| T-08 | Rol `ADMIN/SELLER` convive con la matriz de permisos. | Un solo modelo: roles Operador y Administrador que agrupan permisos por acción. | V1 |
| T-10 | Cuentas "BISA" y "Cuenta Joaco" sembradas dentro de una migración. | Las cuentas Caja, Bisa y Efectivo se cargan en la migración de datos, no en el esquema. | V1 |
| T-11 | Cantidades enteras (`quantity INT`). | Cantidades decimales para venta por peso (P-03, CAT-08). | V1 |
| T-04 | `current_balance` y totales de caja se guardan aparte de los movimientos y pueden desalinearse. | Saldos derivados del libro de movimientos, o caché con reporte de conciliación. | V2 |
| T-09 | Auditoría guarda el nombre de usuario como texto. | Auditoría con referencia al id del usuario. | V2 |
| T-12 | Una sola caja abierta en todo el sistema. | Caja por punto de venta si hay más de uno (P-01). | V3 |

Requerimientos no funcionales, todos para la V1:

- **NF-01** Respaldo diario automático de la base, con una restauración probada antes de salir.
- **NF-02** HTTPS obligatorio y expiración de sesión configurable.
- **NF-03** Pruebas automáticas de las reglas de dinero (ventas, anulaciones, cierres, pagos de pedidos, deudas) antes de cada despliegue.
- **NF-04** El POS registra una venta en menos de 2 segundos con los ~1.200 productos.
- **NF-05** Servidor de producción en un VPS en la nube (P-22), con HTTPS y respaldo diario guardado fuera del VPS.

## 5. Preguntas

No quedan preguntas que bloqueen la V1. Antes de migrar falta revisar la unidad de precio de los productos por peso (frutos secos y proveedor Granel). Las preguntas pendientes son para la V2 y la V3. Cada respuesta se convierte en requerimientos de la sección 3.

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
| P-31 | ¿Qué productos se venden por peso? | Frutos secos y productos del proveedor Granel. La unidad de precio de cada uno se revisa antes de migrar. |
| P-32 | ¿Cómo se importan las ventas históricas? | Una venta histórica por día con sus líneas por producto, costo y utilidad, sin caja ni pagos. |

## 6. Hoja de ruta

Las planillas se dejan al terminar la fase 2, después de dos semanas en paralelo; el inventario (V2) y las extensiones (V3) llegan después sin frenar la operación. Ninguna fase avanza sin pasar su compuerta, probada con datos reales de la tienda.

1. **Fase 0 · Decisiones y base técnica** — Revisión del código (T-00). Flyway, secretos por entorno, VPS con HTTPS y respaldo diario. Roles Operador y Administrador con permisos por acción (USR-05; T-01 a T-11).
   - Compuerta: revisión del código hecha y servidor de producción listo.
2. **Fase 1 · V1: lo que falta para reemplazar las planillas** — Compras, deudas y cuentas por cobrar, consumos y ventas al costo, venta por peso, ajustes de caja, costo por línea, historial de ventas, reportes e importación (COM, DEU, CON, CAT-08, CAJ-08 a 10, FIN-10 y 11).
   - Compuerta: productos (con unidad de peso revisada), saldos, pedidos e históricos desde julio migrados.
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

- [ ] Revisar el código actual (T-00) y anotar las correcciones en la sección 4.
- [ ] Antes de migrar: revisar la unidad de precio de frutos secos y productos Granel, y ajustar la planilla.
- [ ] Contratar el VPS y preparar HTTPS y el respaldo diario.
- [ ] Escribir las specs de Compras, Deudas y cuentas por cobrar, Consumos y venta por peso, los módulos nuevos de la V1.
