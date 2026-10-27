# Migración histórica

Detalle de FIN-11 (sección 3.8 de `specs/requirements.md`), con los mismos IDs. Carga todo el historial de las
dos planillas (*Akhana Recaudaciones* y *Akhana Pedidos*) desde su primer registro hasta el día del corte, para
que los reportes (FIN-10, CON-03) muestren la historia completa. Se corre una sola vez, el día de la salida,
después de importar los productos (CAT-06) y cargar el saldo al corte de las cuentas (FIN-01).

Decisiones del 27-10-2026 con el dueño (P-40 a P-42): la planilla se corrige antes de migrar para que la
migración sea mecánica, y lo que no tiene producto entra con los productos especiales de POS-12 y POS-13. Moneda
Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales y las reglas de CAT-08 y CAT-11.

## Qué se migra y qué no

| Hoja | Se migra como | Notas |
| --- | --- | --- |
| Ventas | Una venta histórica por día y tipo, con sus líneas (P-32) | Sin caja ni pagos; el dinero está en Transacc. |
| Transacc. | Movimientos históricos de Caja, Bisa y Efectivo | Única fuente de dinero histórico (P-43). |
| Cierre de Caja + Conteo | Cajas históricas cerradas, con su arqueo por cortes | No generan ajustes (CAJ-09). |
| Pedidos + Detalle Pedidos | Pedidos con líneas, envío, estado de pago y entrega | Los que tienen saldo quedan por pagar (COM-08). |
| Consumos | Consumos al costo (CON-01) | Con código y costo unitario agregados por el dueño (P-40). |
| HOJITA | No se migra | Lo pendiente se pasa antes a las otras hojas (P-41). |
| Resumen Ventas, Diario, Resumen Transacc. | No se migran | Las calcula el sistema (FIN-10); sirven para validar. |
| Productos | Ya importada con CAT-06 | `specs/product-import.md`. |

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
  tiene código y costo unitario.
- El almuerzo no es consumo: sigue como gasto Comida (P-37).

### Filas de Ventas sin código (P-42)

Tienen nombre y precio de venta, pero no costo: no se identificó el producto o no estaba en el catálogo. No se
borraron productos de la hoja Productos.

- **Cuando** se revisan **entonces** se agrupan por nombre y precio, y para cada grupo se propone el producto de
  la hoja Productos que coincida; el dueño confirma y se completa el código.
- **Cuando** el producto se identifica **entonces** el costo sale, en este orden, de la fila, del Excel de costos
  del sistema anterior o del costo del catálogo.
- **Cuando** la fila es una venta de Mami (últimas semanas antes del corte) **entonces** se marca con el código
  del producto especial de Mami (POS-13).
- **Cuando** no se puede identificar **entonces** queda sin código y entra como venta sin identificar (POS-12).
- El Excel de costos del sistema anterior es un archivo de trabajo: no va al repositorio.

### HOJITA (P-41)

- **Cuando** HOJITA tiene algo que no está en Consumos, Pedidos o Transacc. **entonces** se pasa a esas hojas
  antes de migrar.

## Permiso y pantalla

- Pantalla **Migración** (`/migration`, carga diferida), permiso nuevo `MIGRATION` solo para Administrador. Se
  usa una vez; después del corte puede quedar sin uso.
- Dos pasos, como CAT-06: **Revisar** (sube los dos `.xlsx` y muestra el resultado sin guardar) e **Importar**
  (vuelve a validar y guarda).
- **Cuando** un usuario sin `MIGRATION` llama a `/api/migration/**` **entonces** recibe 403.

## FIN-11 · Archivos y fecha de corte

- **Cuando** se revisa **entonces** se suben los dos `.xlsx` (Archivo → Descargar → Microsoft Excel) y se indica
  la fecha de corte: el último día que se migra. El sistema empieza a registrar el día siguiente.
- **Cuando** falta una hoja (Ventas, Transacc., Cierre de Caja, Conteo, Consumos, Pedidos, Detalle Pedidos) o una
  de sus columnas **entonces** «Faltan las hojas o columnas: <lista>.» y no se importa nada. Las columnas se buscan
  por nombre, como en CAT-06.
- **Cuando** una fila tiene fecha posterior a la fecha de corte **entonces** no se migra y se cuenta aparte
  («posteriores al corte»).
- **Cuando** ya hay datos históricos importados **entonces** no se puede importar de nuevo: «La migración
  histórica ya se hizo.»

## FIN-11 · Marca de histórico

Toda venta, movimiento, caja, pedido, pago de pedido y consumo importado queda marcado como histórico (origen
importación), con quién y cuándo lo importó.

- **Cuando** se calcula el saldo de una cuenta (FIN-08) **entonces** los movimientos históricos no cuentan: el
  saldo de hoy es el saldo al corte (FIN-01) más los movimientos registrados en el sistema.
- **Cuando** se carga el saldo al corte (FIN-01) **entonces** los movimientos históricos no lo bloquean: sigue
  editable mientras la cuenta no tenga movimientos propios del sistema.
- **Cuando** se abre la primera caja **entonces** la propuesta de monto inicial (CAJ-10) ignora las cajas
  históricas y propone el saldo de Caja.
- **Cuando** se pide un reporte (FIN-10, CON-03) **entonces** los registros históricos cuentan igual que los del
  sistema.
- **Cuando** se mira un registro histórico (venta, caja, pedido, consumo, movimiento) **entonces** se ve marcado
  «Histórico» y no se puede anular, editar ni pagar, salvo los pedidos por pagar (abajo).
- **Cuando** se numera una venta histórica **entonces** usa su propia serie y no consume la correlativa del POS
  (POS-06).

## FIN-11 · Ventas

Una venta histórica por día y tipo, sin caja, sin pagos y sin movimientos (P-32). El dinero de esas ventas ya
está en Transacc. como ingresos Venta.

- **Cuando** un día tiene filas en Ventas **entonces** se crea una venta `NORMAL` con una línea por fila:
  producto por código, cantidad, precio unitario = total de la fila ÷ cantidad y costo unitario de la fila
  (`sale_items.unit_cost`, T-07). El total de la venta es la suma de las filas.
- **Cuando** el código es de una libra de fruto seco (`FSE-NUE-MLI`) **entonces** la línea se carga en el producto
  base con su presentación y los kilos (CAT-11).
- **Cuando** la fila tiene el comentario «precio costo» **entonces** va a la venta `AL_COSTO` de ese día (CON-02,
  P-39).
- **Cuando** la fila es un regalo o una rotura («Regalo Ali», «Mami rompió») **entonces** se migra como consumo
  con ese comentario, no como venta (P-39).
- **Cuando** la fila no tiene código **entonces** la línea va al «Producto sin identificar» con el nombre y el
  precio de la fila, costo pendiente y estado por asignar (POS-12).
- **Cuando** la fila tiene el código del producto de Mami **entonces** la línea va a ese producto con costo igual
  al precio (POS-13).
- **Cuando** el código no existe en el catálogo **entonces** la fila se rechaza: «El código <código> no existe en
  el catálogo.» (se corrige en la planilla o se importa el producto con CAT-06).

### Deuda inicial con Mami (P-42)

- **Cuando** se importan las ventas de Mami **entonces** su suma es la deuda inicial con Mami (DEU-04), sin
  movimiento de dinero. Es la única deuda inicial con personas (P-16).
- **Cuando** después se le paga **entonces** se registra en el sistema como devolución (DEU-03) con la categoría
  «Pago a tercero».

## FIN-11 · Movimientos (Transacc.)

Transacc. es la única fuente de movimientos de dinero históricos (P-43).

- **Cuando** una fila es un ingreso o gasto **entonces** se crea un movimiento histórico en su cuenta (Caja, Bisa
  o Efectivo), con su fecha, descripción, monto y categoría (FIN-05).
- **Cuando** la categoría no existe en el sistema **entonces** la fila se rechaza con la categoría en el motivo.
- **Cuando** la fila es una transferencia (Retiro caja, Depósito, Otro entre cuentas) **entonces** se crea una
  transferencia histórica entre las dos cuentas. El hallazgo de FIN-10: la planilla la anota en una sola fila en
  positivo con la categoría; cómo se sabe la cuenta de destino se valida con la planilla.
- **Cuando** la fila es un Ajuste **entonces** queda como ingreso o gasto Ajuste según el signo.

## FIN-11 · Cierres de caja

- **Cuando** un día tiene fila en Cierre de Caja **entonces** se crea una caja histórica cerrada con monto
  inicial, ventas en efectivo y QR, otros ingresos, gastos, salida interna, caja final, sobrante o faltante, y los
  cortes de la hoja Conteo de ese día.
- **Cuando** la caja histórica tiene sobrante o faltante **entonces** no se crea el ajuste de CAJ-09: ya está en
  Transacc. si la planilla lo registró.
- Las ventas y movimientos históricos no se vinculan a la caja histórica: la caja se guarda con los montos de la
  planilla.

## FIN-11 · Pedidos

- **Cuando** se migra un pedido de *Akhana Pedidos* **entonces** se crea con su proveedor, fecha, líneas (producto
  por código, cantidad, costo unitario), envío, comentario y entrega (COM-02, COM-07), sin cambiar el costo ni el
  precio de los productos (COM-03 no aplica).
- **Cuando** el pedido tiene pagos **entonces** se importan sin movimiento de dinero (los gastos Pedido y
  Transporte ya están en Transacc., P-43), solo para el estado de pago (COM-04).
- **Cuando** el pedido queda con saldo **entonces** aparece en Por pagar (COM-08) y se paga desde el sistema como
  cualquier pedido: ese pago sí genera movimiento.
- **Cuando** una línea no tiene código o el código no existe **entonces** la fila se rechaza para corregirla en la
  planilla.

## FIN-11 · Consumos

- **Cuando** una fila de la hoja Consumos tiene código y costo unitario **entonces** se crea un consumo histórico
  con esa fecha, producto, cantidad, costo de la fila y comentario o «para quién» (CON-01, P-36).
- **Cuando** falta el código o el costo **entonces** la fila se rechaza.

## FIN-11 · Resultado y validación

- **Cuando** se revisa **entonces** se muestra por hoja: filas leídas, a importar, rechazadas y posteriores al
  corte; la tabla de rechazadas con hoja, fila y motivo (descargable como `.xlsx`); las líneas por asignar con su
  total en Bs; la deuda inicial con Mami; y los totales por mes de ventas, utilidad y movimientos por categoría
  para compararlos con las tablas dinámicas.
- **Cuando** hay alguna fila rechazada **entonces** no se puede importar: el historial entra completo o no entra.
- **Cuando** se importa **entonces** todo se guarda en una sola transacción: si algo falla, no queda nada a
  medias.
- **Cuando** termina **entonces** los reportes de cualquier mes migrado coinciden con las tablas dinámicas,
  salvo las diferencias conocidas: consumos al costo y fuera de las ventas (P-35, P-40), costos completados en
  las filas sin código (P-42) y ventas de Mami fuera de las ventas de la tienda (POS-13).

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/migration/preview` (multipart `recaudaciones`, `pedidos`, `cutoffDate`) | Valida y devuelve el resultado sin guardar. |
| `POST /api/migration` (mismos campos) | Valida de nuevo y guarda todo, solo sin rechazadas. |

## Tareas

Antes de construir:

- [ ] Construir POS-11 a POS-13 (spec en `specs/sales.md`): la migración usa sus productos especiales.
- [ ] Responder P-43 validando con la planilla que los pagos de Akhana Pedidos coinciden con los gastos Pedido y
  Transporte de Transacc.
- [ ] Validar con la planilla los nombres de columnas de cada hoja, cómo se identifica la cuenta de destino de
  una transferencia y cómo se reconoce un regalo o una rotura en Ventas.

Correcciones en la planilla (con el dueño, en otra sesión):

- [ ] Copia de respaldo de las dos planillas.
- [ ] Lista por día de consumos anotados como venta y corrección en Ventas, Transacc., Cierre de Caja y Consumos.
- [ ] Código y costo unitario en cada fila de la hoja Consumos (el dueño).
- [ ] Revisión de las filas de Ventas sin código con el Excel de costos del sistema anterior; ventas de Mami
  marcadas.
- [ ] HOJITA vaciada en las otras hojas.

Construcción (rama `feat/migration`):

- [ ] Migración Flyway: marca de histórico en ventas, movimientos, cajas, pedidos, pagos de pedido y consumos;
  serie propia de ventas históricas.
- [ ] Saldos (FIN-08), saldo al corte (FIN-01) y propuesta de apertura (CAJ-10) sin los históricos; registros
  históricos de solo lectura.
- [ ] `MigrationService`: lectura de los dos `.xlsx` con Apache POI, validación por hoja, guardado en una
  transacción, una sola vez.
- [ ] `MigrationController` con preview e import, `@PreAuthorize` de `MIGRATION`; prueba en
  `EndpointAuthorizationTest`.
- [ ] Pruebas: `MigrationServiceTest` con `.xlsx` armados en la prueba (consumo, fila sin código, venta de Mami,
  libra de fruto seco, «precio costo», transferencia, cierre con sobrante, pedido con saldo, código inexistente,
  fila posterior al corte, segunda importación) y una prueba contra PostgreSQL de saldos sin históricos.
- [ ] Frontend: pantalla Migración con Revisar / Importar, resultado y descarga de rechazadas.
- [ ] Validar con los `.xlsx` reales: totales por mes contra las tablas dinámicas.
- [ ] Actualizar `specs/requirements.md` (FIN-11, tablero).
