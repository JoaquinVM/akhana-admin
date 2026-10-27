# Migración histórica

Detalle de FIN-11 (sección 3.8 de `specs/requirements.md`), con los mismos IDs. Carga todo el historial de las
dos planillas (*Akhana Recaudaciones* y *Akhana Pedidos*) desde su primer registro hasta el día del corte, para
que los reportes (FIN-10, CON-03) muestren la historia completa. Se corre una sola vez, el día de la salida,
después de importar los productos (CAT-06) y cargar el saldo al corte de las cuentas (FIN-01).

Decisiones del 27-10-2026 con el dueño (P-40 a P-42, P-45 a P-47): la planilla se corrige antes de migrar para que
la migración sea mecánica, y lo que no tiene producto entra con los productos especiales de POS-12 y POS-13. Moneda
Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales y las reglas de CAT-08 y CAT-11.

Construida el 27-10-2026 (rama `feat/migration`, migración `V18__historical_migration.sql`). Falta hacer las
correcciones en la planilla y correrla el día de la salida.

## Qué se migra y qué no

| Hoja | Archivo | Se migra como | Notas |
| --- | --- | --- | --- |
| Ventas | Recaudaciones | Una venta histórica por día y tipo, con sus líneas (P-32) | Desde el 17-07. Sin caja ni pagos; el dinero está en Transacc. |
| Transacc. | Recaudaciones | Movimientos históricos de Caja, Bisa y Efectivo | Desde el 04-05. Única fuente de dinero histórico (P-43). |
| Cierre de Caja | Recaudaciones | Cajas históricas cerradas | Desde el 04-05. No generan ajustes (CAJ-09). |
| Conteo | Recaudaciones | No se migra | No tiene fechas: son los 3 bloques del conteo del día en curso, no un historial. |
| Pedidos | Pedidos | Pedidos con envío, estado de pago y entrega | 515 pedidos desde abril. Los que tienen saldo quedan por pagar (COM-08). |
| Detalle Pedidos | Recaudaciones | Líneas de los pedidos | Solo desde el pedido 342 (01-09); los anteriores entran sin líneas (P-45). |
| Consumos | Recaudaciones | Consumos al costo (CON-01) | Con código y costo unitario agregados por el dueño (P-40). |
| Productos | Recaudaciones | Ya importada con CAT-06 | La migración solo lee «Se vende por» (CAT-08). |
| HOJITA | Recaudaciones | No se migra | Lo pendiente se pasa antes a las otras hojas (P-41). |
| Resumen, Resumen Ventas, Diario, Resumen Transacc. | Recaudaciones | No se migran | Las calcula el sistema (FIN-10); sirven para validar. |

## Correcciones previas en la planilla

Antes de migrar se corrige la planilla, con ayuda de Claude (excepción a «la planilla no se modifica» de
CAT-06). Hay que hacer antes una copia de respaldo de las dos planillas.

### Consumos anotados como venta (P-40)

Hasta julio de 2026 un consumo se anotaba como venta (el producto sumaba al total de ventas del día) y se sacaba
el mismo monto de Caja como gasto Consumo en Transacc., a precio de venta. Del 17 al 28-07 el mismo consumo
también está en la hoja Consumos.

- **Cuando** un gasto Consumo de Transacc. corresponde a un producto anotado en Ventas el mismo día **entonces**
  se corrige en las cuatro hojas a la vez: en Ventas se baja la cantidad (o se quita la fila); en Transacc. se
  quita el gasto; en Cierre de Caja se bajan las ventas y los gastos del día por el mismo monto (la caja final y
  el conteo no cambian); en Consumos se agrega la fila con fecha, código, cantidad y costo unitario, si no estaba.
- **Cuando** el consumo ya está en la hoja Consumos (17 a 28-07) **entonces** no se agrega de nuevo: solo se
  quitan la venta y el gasto.
- **Cuando** no se encuentra la venta que le corresponde a un gasto Consumo **entonces** queda en una lista
  aparte para que el dueño decida.
- **Cuando** termina la corrección **entonces** cada consumo está solo en la hoja Consumos y cada fila de esa hoja
  tiene código y costo unitario (columnas nuevas **Codigo** y **Costo unitario**; la de «para quién» se titula
  **Para quien**).
- El almuerzo no es consumo: sigue como gasto Comida (P-37).

### Filas de Ventas sin código (P-42)

Tienen nombre y precio de venta, pero no costo: no se identificó el producto o no estaba en el catálogo. No se
borraron productos de la hoja Productos. Al 26-10 son 327 filas.

- **Cuando** se revisan **entonces** se agrupan por nombre y precio, y para cada grupo se propone el producto de
  la hoja Productos que coincida; el dueño confirma y se completa el código.
- **Cuando** el producto se identifica **entonces** el costo sale, en este orden, de la fila, del Excel de costos
  del sistema anterior o del costo del catálogo.
- **Cuando** la fila es una venta de Mami (últimas semanas antes del corte) **entonces** se marca con el código
  del producto especial de Mami (POS-13).
- **Cuando** no se puede identificar **entonces** queda sin código y entra como venta sin identificar (POS-12).
- El Excel de costos del sistema anterior es un archivo de trabajo: no va al repositorio.

### Pedidos (P-45 a P-47)

- **Cuando** se prepara la hoja Pedidos **entonces** se agrega la columna **Proveedor** con el nombre del
  proveedor tal como está en el sistema: «Nombre» es texto libre («Sativa Abril», «Colmena 4 mayo»). Claude
  propone el proveedor de cada pedido por su nombre y el dueño confirma, como con las filas de Ventas sin código.
  En los pedidos con líneas puede quedar vacía: se usa el Provedor de Detalle Pedidos.
- **Cuando** un pedido está «A Cuenta» **entonces** el dueño anota en la columna nueva **Monto pagado** cuánto se
  pagó; el resto queda por pagar.

### Otros arreglos encontrados al validar con la planilla del 26-10

- Transacc.: los «Ventas QR» del 21, 22, 23 y 26-10 (filas 1527, 1536, 1551, 1564) tienen el monto en
  Comentarios y no en Monto, y la fila 1554 tampoco tiene monto; la fila 122 usa la categoría «Otro» en un
  ingreso; las filas 1567, 1568 y 1572 son notas sin fecha, tipo ni cuenta.
- Cierre de Caja: la fila 146 es el borrador del día siguiente (sin fecha ni caja final).
- Pedidos: el pedido 91 está dos veces; la fila 520 (Boni Pastas dentales) no tiene número; 3 pedidos sin Monto y
  2 con Monto 0; 3 dicen «Pagado» en Estado de Entrega.
- Códigos que no están en la hoja Productos: 9 en Ventas (`COL=PAN-SG`, `Crema Dental Surtido`…) y 14 líneas de
  Detalle Pedidos (`BRI-*`, `ORI-SRO-*`, `TNA-MIE-ABE-350GR`, `DES-HAR-COC-500GR`): se corrige el código o se
  importa el producto con CAT-06.
- En Finanzas → Categorías deben existir las categorías de Transacc. (Otro gasto, Consumo, Otro Ingreso,
  Utilidad, Envio, Devolucion…). «Venta» usa la categoría de ventas del sistema aunque se llame «Ventas».

### HOJITA (P-41)

- **Cuando** HOJITA tiene algo que no está en Consumos, Pedidos o Transacc. **entonces** se pasa a esas hojas
  antes de migrar.

## Permiso y pantalla

- Pantalla **Migración** (`/migration`, carga diferida, menú Finanzas → Migración), permiso nuevo `MIGRATION`
  solo para Administrador. Se usa una vez; después del corte puede quedar sin uso.
- Dos pasos, como CAT-06: **Revisar** (sube los dos `.xlsx` y la fecha de corte, y muestra el resultado sin
  guardar) e **Importar** (pide confirmación, vuelve a validar y guarda).
- **Cuando** un usuario sin `MIGRATION` llama a `/api/migration/**` **entonces** recibe 403.

## FIN-11 · Archivos y fecha de corte

- **Cuando** se revisa **entonces** se suben los dos `.xlsx` (Archivo → Descargar → Microsoft Excel), de hasta
  10 MB y 20.000 filas por hoja, y se indica la fecha de corte: el último día que se migra (no puede ser
  posterior a hoy). El sistema empieza a registrar el día siguiente.
- **Cuando** falta una hoja (Ventas, Transacc., Cierre de Caja, Consumos, Pedidos, Detalle Pedidos) o una de sus
  columnas obligatorias **entonces** «Faltan las hojas o columnas: <hoja>, <hoja> › <columna>.» y no se revisa
  nada. Hojas y columnas se buscan por nombre en cualquiera de los dos archivos, sin distinguir mayúsculas ni
  tildes, como en CAT-06:

  | Hoja | Columnas obligatorias | Opcionales |
  | --- | --- | --- |
  | Ventas | Fecha, Codigo, Cant, Descripción, Total, Costo unitario | Comentarios |
  | Transacc. | Tipo, Fecha, Descripcion, Monto, Categoria, Cuenta, Cuenta Destino | Comentarios |
  | Cierre de Caja | Caja Inicial, Otro Ingreso, Total, QR, Efectivo, Gastos Caja, Otra salida, Salida Interna, Resultado, Caja final, Sobrante | Fecha (si no hay, la primera columna, que no tiene título), Comentarios |
  | Consumos | Fecha, Codigo, Cantidad, Costo unitario | Descripción, Para quien, Comentarios |
  | Pedidos | COD, Proveedor, Monto, Estado Pago | Nombre, Monto Envio, Tipo Pago, Fecha pago, Monto pagado, Tipo Entrega, Estado de Entrega, Fecha Entrega, Comentarios |
  | Detalle Pedidos | Codigo (el primero, o COD: el pedido), Fecha, Codigo (el segundo: el producto), Cant, Precio unitario, Total | Provedor |
  | Productos (opcional) | Codigo | Se vende por |

- **Cuando** una fila tiene fecha posterior a la fecha de corte **entonces** no se migra y se cuenta aparte
  («posteriores al corte»).
- **Cuando** una fila no tiene fecha **entonces** se rechaza: «Falta la fecha.»
- **Cuando** ya hay una migración guardada **entonces** no se puede revisar ni importar de nuevo: «La migración
  histórica ya se hizo.»

## FIN-11 · Marca de histórico

Cada migración guardada queda en `historical_imports` (fecha de corte, quién y cuándo). Toda venta, movimiento,
caja, pedido, pago de pedido y consumo importado apunta a ella (`historical_import_id`) y la respuesta de cada uno
trae `historical`.

- **Cuando** se calcula el saldo de una cuenta (FIN-08) **entonces** los movimientos históricos no cuentan: el
  saldo de hoy es el saldo al corte (FIN-01) más los movimientos registrados en el sistema. (Los saldos se
  guardan al registrar cada movimiento y la migración no los toca.)
- **Cuando** se carga el saldo al corte (FIN-01) **entonces** los movimientos históricos no lo bloquean: sigue
  editable mientras la cuenta no tenga movimientos propios del sistema.
- **Cuando** se abre la primera caja **entonces** la propuesta de monto inicial (CAJ-10) ignora las cajas
  históricas y propone el saldo de Caja.
- **Cuando** se pide un reporte (FIN-10, CON-03) **entonces** los registros históricos cuentan igual que los del
  sistema.
- **Cuando** se mira un registro histórico **entonces** se ve marcado «Histórico» (historial de ventas, detalle de
  venta, historial de cajas, detalle de pedido y de consumo) y no se puede anular ni editar; el servidor lo
  rechaza con «La venta es histórica (migrada de la planilla): no se puede anular.» o el mensaje equivalente
  del consumo, el movimiento, el pedido o el pago. Un pedido histórico con saldo sí admite pagos nuevos.
- **Cuando** se numera una venta histórica **entonces** usa su propia serie (`HIS-000001`) y no consume la
  correlativa del POS (POS-06). Su forma de pago es «Histórico» (sin pagos) y no tiene caja.

## FIN-11 · Ventas

Una venta histórica por día y tipo, sin caja, sin pagos y sin movimientos (P-32). El dinero de esas ventas ya
está en Transacc. como ingresos Venta.

- **Cuando** un día tiene filas en Ventas **entonces** se crea una venta `NORMAL` con una línea por fila:
  producto por código, cantidad, precio unitario = total de la fila ÷ cantidad (redondeado; el subtotal es el
  total de la fila) y costo unitario de la fila (`sale_items.unit_cost`, T-07). El total de la venta es la suma
  de las filas.
- **Cuando** el producto se vende por kilo **entonces** la cantidad de la planilla está en la unidad de «Se vende
  por» de la hoja Productos (Gramo, 100 g o Kilo, CAT-08) y se pasa a kilos; precio y costo quedan por kilo:
  250 de Nuez en Gramo con costo 0,13 entran como 0,250 kg a costo 130,00. **Cuando** «Se vende por» no lo marca
  **entonces** «El producto <código> se vende por kilo: marcar «Se vende por» en Productos.»
- **Cuando** el código es de una libra (`-CLI`, `-MLI`, `-1LI`) **entonces** la línea se carga en el producto base
  con su presentación (1/4, 1/2 o 1 libra) y los kilos; el costo de la fila es por paquete y se guarda por kilo
  (CAT-11). Al 26-10 hay 68 filas así.
- **Cuando** un producto por unidad tiene cantidad con decimales (0,5 de Tostada) **entonces** se acepta hasta 3
  decimales: es historia.
- **Cuando** el comentario dice «precio costo» **entonces** la fila va a la venta `AL_COSTO` de ese día (CON-02,
  P-39).
- **Cuando** el comentario empieza con «Regalo» o dice «rompió» («Regalo Ali», «Mami rompió») **entonces** se migra
  como consumo con ese comentario y el costo de la fila, no como venta (P-39).
- **Cuando** la fila no tiene código **entonces** la línea va al «Producto sin identificar» con el nombre y el
  precio de la fila, costo 0 pendiente y estado por asignar (POS-12); se asigna después desde el historial de
  ventas. Si falta crear ese producto: «Falta crear el «Producto sin identificar» en Productos (POS-12).»
- **Cuando** la fila tiene el código de un producto de tercero (Mami) **entonces** la línea va a ese producto con
  el nombre de la fila y costo igual al precio (POS-13).
- **Cuando** el código no existe en el catálogo **entonces** la fila se rechaza: «El código <código> no existe en
  el catálogo.» (se corrige en la planilla o se importa el producto con CAT-06).
- **Cuando** una fila con código no tiene costo unitario **entonces** «Falta el costo unitario.»

### Deuda inicial con Mami (P-42)

- **Cuando** se importan las ventas de Mami **entonces** su suma es la deuda inicial con Mami (DEU-04), sin
  movimiento de dinero: la deuda se calcula de las líneas de tercero, así que no hay que cargarla aparte. Es la
  única deuda inicial con personas (P-16). La revisión la muestra por dueña.
- **Cuando** después se le paga **entonces** se registra en el sistema como devolución (DEU-03) con la categoría
  «Pago a tercero».

## FIN-11 · Movimientos (Transacc.)

Transacc. es la única fuente de movimientos de dinero históricos (P-43).

- **Cuando** una fila es Ingreso, Gasto, Retiro o Ajuste **entonces** se crea un movimiento histórico en su
  cuenta (Caja, Bisa o Efectivo, por nombre) con su fecha, descripción, monto y categoría (FIN-05). El signo del
  Monto dice si es ingreso o gasto, como lo suman las columnas de cuentas de la planilla: un «Gasto» positivo
  (Devolución Hongos tati) es un ingreso y un «Ingreso» negativo es un gasto.
- **Cuando** la categoría existe como ingreso y como gasto (Ajuste) **entonces** se usa la del sentido del
  movimiento. **Cuando** un Ajuste no tiene categoría **entonces** va a Ajuste. **Cuando** la categoría no existe
  en el sistema **entonces** «La categoría <nombre> no existe en Finanzas.»
- **Cuando** la fila es una Transferencia **entonces** se crea una transferencia histórica de Cuenta a Cuenta
  Destino (hallazgo: la planilla trae la cuenta de destino en su columna); si el monto es negativo va al revés.
- **Cuando** falta el tipo, el monto, la cuenta o la categoría **entonces** la fila se rechaza con el motivo.

## FIN-11 · Cierres de caja

- **Cuando** un día tiene fila en Cierre de Caja **entonces** se crea una caja histórica cerrada con monto inicial,
  ventas totales, en efectivo y QR, resultado (efectivo esperado), caja final y sobrante o faltante; otros
  ingresos, gastos, otra salida y salida interna se guardan en columnas propias de la caja histórica.
- **Cuando** la caja histórica tiene sobrante o faltante **entonces** no se crea el ajuste de CAJ-09: ya está en
  Transacc. si la planilla lo registró.
- **Cuando** el día ya está en otra fila **entonces** «El día ya está en la fila <n>.»
- La hoja Conteo no tiene fechas: las cajas históricas no tienen cortes.
- Las ventas y movimientos históricos no se vinculan a la caja histórica: la caja se guarda con los montos de la
  planilla.

## FIN-11 · Pedidos

- **Cuando** se migra un pedido de la hoja Pedidos **entonces** se crea con el número `PED-H<COD>` (por ejemplo
  `PED-H00342`), su proveedor (columna Proveedor, P-46), Monto como total de líneas, Monto Envio, comentario
  (Nombre · Comentarios) y entrega, sin cambiar el costo ni el precio de los productos (COM-03 no aplica).
- **Cuando** el pedido tiene líneas en Detalle Pedidos (desde el 342) **entonces** se cargan con producto por
  código, cantidad y costo unitario (por kilo si el producto se vende por kilo); su fecha es la de las líneas.
  **Cuando** la suma de las líneas no coincide con el Monto **entonces** queda un aviso y el pedido usa el Monto
  (23 pedidos al 26-10, la mayoría por centavos).
- **Cuando** el pedido no tiene líneas (1 a 341) **entonces** entra solo con la cabecera (P-45) y su fecha es la
  Fecha Entrega o, si no hay, la Fecha pago.
- **Cuando** el Estado Pago es Pagado **entonces** se importa un pago por el Monto con la Fecha pago, desde Caja o
  Bisa según Tipo Pago (Otro o vacío: sin cuenta), sin movimiento de dinero (los gastos Pedido y Transporte ya
  están en Transacc., P-43), solo para el estado de pago (COM-04). **Cuando** es A Cuenta **entonces** el pago es
  el Monto pagado (P-47). **Cuando** es Por Pagar **entonces** no tiene pagos. **Cuando** es Consignacion
  **entonces** queda en consignación.
- **Cuando** el pedido queda con saldo **entonces** aparece en Por pagar (COM-08) y se paga desde el sistema como
  cualquier pedido: ese pago sí genera movimiento.
- **Cuando** Tipo Entrega es En puerta, Terminal o En tienda (vacío: En tienda) y Estado de Entrega es Entregado o
  En camino (vacío: pendiente) **entonces** se cargan; otro valor rechaza el pedido.
- **Cuando** una línea no tiene código, el código no existe, es una libra o un producto especial, o el pedido no
  está en la hoja Pedidos **entonces** la fila de Detalle Pedidos se rechaza para corregirla en la planilla.
- **Cuando** falta el número, el número se repite, falta el proveedor o no existe, falta el Monto o la fecha
  **entonces** el pedido se rechaza.

## FIN-11 · Consumos

- **Cuando** una fila de la hoja Consumos tiene código y costo unitario **entonces** se crea un consumo histórico
  (número de la serie `CON-`) con esa fecha, producto, cantidad, costo de la fila, «Para quien» y comentario
  (CON-01, P-36). Los productos por kilo y las libras siguen las reglas de Ventas.
- **Cuando** falta el código o el costo **entonces** la fila se rechaza.

## FIN-11 · Resultado y validación

- **Cuando** se revisa **entonces** se muestra por hoja: filas leídas, a importar, rechazadas y posteriores al
  corte; la tabla de rechazadas con hoja, fila y motivo (descargable como `.xlsx`); los avisos; las líneas por
  asignar con su total en Bs; la deuda inicial con cada dueña de productos de terceros; y los totales por mes
  (ventas, utilidad, por asignar, al costo, de terceros, consumos) y los movimientos por categoría y mes para
  compararlos con las tablas dinámicas.
- **Cuando** hay alguna fila rechazada **entonces** no se puede importar: el historial entra completo o no entra.
  «Hay filas rechazadas: se corrigen en la planilla y se revisa de nuevo. No se importó nada.»
- **Cuando** se importa **entonces** todo se guarda en una sola transacción: si algo falla, no queda nada a
  medias.
- **Cuando** termina **entonces** los reportes de cualquier mes migrado coinciden con las tablas dinámicas,
  salvo las diferencias conocidas: consumos al costo y fuera de las ventas (P-35, P-40), costos completados en
  las filas sin código (P-42) y ventas de Mami fuera de las ventas de la tienda (POS-13).

### Validación con la planilla del 26-10 (sin corregir)

Con los productos de CAT-06 importados (sin «Se vende por») y las columnas nuevas vacías, la revisión tarda menos
de un segundo y da: Ventas 6.053 filas (5.905 a importar, 148 rechazadas: 143 por códigos de frutos secos y Granel
que faltan marcar en «Se vende por» y 9 códigos que no están en Productos), Transacc. 1.568 (9 rechazadas, arriba),
Cierre de Caja 145 (1), Consumos 89 (todas, sin código ni costo todavía), Pedidos 517 y Detalle Pedidos 902 (14).
Agosto da Venta 67.031,50, Pedido −67.097,56 y Comida −2.530, igual que la validación de FIN-10; la utilidad de
julio da 8.497,78 contra 8.505,78 de la tabla dinámica, por las filas rechazadas. P-43: los pagos de Pedidos
suman 269.878,33 y los gastos Pedido de Transacc. 273.633,75; por mes difieren por la fecha de pago (mayo +6.767,
julio −4.034), y en octubre los montos coinciden uno a uno salvo 5.

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/migration/preview` (multipart `recaudaciones`, `pedidos`, `cutoffDate`) | Valida y devuelve el resultado sin guardar. |
| `POST /api/migration` (mismos campos) | Valida de nuevo y guarda todo, solo sin rechazadas. |

## Tareas

Antes de construir:

- [x] Construir POS-11 a POS-13 (spec en `specs/sales.md`): la migración usa sus productos especiales.
- [x] Validar con la planilla P-43: los montos coinciden en total; queda el supuesto (arriba).
- [x] Validar con la planilla los nombres de columnas de cada hoja, la cuenta de destino de una transferencia
  (columna Cuenta Destino) y cómo se reconoce un regalo o una rotura en Ventas (comentario).

Correcciones en la planilla (con el dueño, en otra sesión):

- [ ] Copia de respaldo de las dos planillas.
- [ ] Lista por día de consumos anotados como venta y corrección en Ventas, Transacc., Cierre de Caja y Consumos.
- [ ] Código y costo unitario en cada fila de la hoja Consumos (el dueño).
- [ ] Revisión de las filas de Ventas sin código con el Excel de costos del sistema anterior; ventas de Mami
  marcadas.
- [ ] Columna Proveedor en Pedidos (Claude propone, el dueño confirma) y Monto pagado en los pedidos A Cuenta.
- [ ] Arreglos de «Otros arreglos encontrados» y categorías faltantes en Finanzas.
- [ ] HOJITA vaciada en las otras hojas.

Construcción (rama `feat/migration`, 27-10-2026):

- [x] Migración Flyway `V18`: `historical_imports` y `historical_import_id` en ventas, movimientos, cajas, pedidos,
  pagos de pedido y consumos; venta sin caja con forma de pago `HISTORICO`; serie `historical_sale_number_seq`;
  pagos históricos sin movimiento.
- [x] Saldo al corte (FIN-01) y propuesta de apertura (CAJ-10) sin los históricos; registros históricos de solo
  lectura en ventas, movimientos, pedidos, pagos y consumos.
- [x] `MigrationService` (`MigrationServiceImpl` + `MigrationWorkbook`): lectura de los dos `.xlsx` con Apache
  POI, validación por hoja, guardado en una transacción, una sola vez.
- [x] `MigrationController` con preview e import, `@PreAuthorize` de `MIGRATION`; prueba en
  `EndpointAuthorizationTest`.
- [x] Pruebas: `MigrationIntegrationTest` contra PostgreSQL con `.xlsx` armados en la prueba (fila sin código,
  venta de Mami, libra, producto en Gramo, «precio costo», regalo, transferencia, cierre con sobrante, pedidos
  pagado, a cuenta, por pagar con línea y en consignación, código inexistente, categoría inexistente, pedido sin
  proveedor, producto por kilo sin «Se vende por», fila posterior al corte, hoja faltante, segunda importación,
  saldo y apertura sin históricos).
- [x] Frontend: pantalla Migración con Revisar / Importar, resultado y descarga de rechazadas; marca «Histórico»
  en ventas, cajas, pedidos y consumos.
- [x] Validar con los `.xlsx` reales (arriba).
- [ ] Marca «Histórico» en Finanzas → Transacciones: `transactions.component.html` es muy grande; se divide antes
  de tocarlo. Mientras tanto el servidor rechaza editar o anular un movimiento histórico con su mensaje.
- [ ] El día de la salida: importar productos (CAT-06), cargar saldos al corte (FIN-01), revisar e importar.
