# Migración histórica

Detalle de FIN-11 (sección 3.8 de `specs/requirements.md`), con los mismos IDs. Carga todo el historial de las
dos planillas (*Akhana Recaudaciones* y *Akhana Pedidos*) desde su primer registro hasta el día del corte, para
que los reportes (FIN-10, CON-03) muestren la historia completa. Se corre una sola vez, el día de la salida,
después de importar los productos (CAT-06) y cargar el saldo al corte de las cuentas (FIN-01).

Decisiones del 27-10-2026 con el dueño (P-40 a P-42, P-45 a P-47): la planilla se corrige antes de migrar para que
la migración sea mecánica, y lo que no tiene producto entra con los productos especiales de POS-12 y POS-13. Moneda
Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales y las reglas de CAT-08 y CAT-11.

Construida el 27-10-2026 (rama `feat/migration`, migración `V18__historical_migration.sql`). Correcciones en la
planilla hechas el 27-10-2026 (HOJITA cerrada) y el 28-10-2026 (filas de Ventas sin código); falta correrla el día de la salida,
vaciando antes la última fila del Cierre (borrador).

## Qué se migra y qué no

| Hoja | Archivo | Se migra como | Notas |
| --- | --- | --- | --- |
| Ventas | Recaudaciones | Una venta histórica por día y tipo, con sus líneas (P-32) | Desde el 17-07. Sin caja ni pagos; el dinero está en Transacc. |
| Transacc. | Recaudaciones | Movimientos históricos de Caja, Bisa y Efectivo | Desde el 04-05. Única fuente de dinero histórico (P-43). |
| Cierre de Caja | Recaudaciones | Cajas históricas cerradas | Desde el 04-05. No generan ajustes (CAJ-09). |
| Conteo | Recaudaciones | No se migra | No tiene fechas: son los 3 bloques del conteo del día en curso, no un historial. |
| Pedidos | Pedidos | Pedidos con envío, estado de pago y entrega | 517 pedidos desde abril (al 27-10). Los que tienen saldo quedan por pagar (COM-08). |
| Detalle Pedidos | Recaudaciones | Líneas de los pedidos | Solo desde el pedido 342 (01-09); los anteriores entran sin líneas (P-45). |
| Consumos | Recaudaciones | Consumos al costo (CON-01) | Desde el 04-05, con código y costo unitario (P-40). |
| Productos | Recaudaciones | Ya importada con CAT-06 | La migración solo lee «Se vende por» (CAT-08). |
| HOJITA | Recaudaciones | No se migra | Lo pendiente se pasa antes a las otras hojas (P-41). |
| Resumen, Resumen Ventas, Diario, Resumen Transacc. | Recaudaciones | No se migran | Las calcula el sistema (FIN-10); sirven para validar. |

## Correcciones previas en la planilla

Antes de migrar se corrige la planilla, con ayuda de Claude (excepción a «la planilla no se modifica» de
CAT-06). Hay que hacer antes una copia de respaldo de las dos planillas.

Hechas el 27-10-2026 con el dueño, con copia de respaldo previa de las dos planillas: consumos (P-40), hoja
Consumos completa, Cierre igual a Ventas, columnas Proveedor y Monto pagado en Pedidos y los arreglos de la
validación. Las filas de Transacc. cambiaron de número (se quitaron 59 gastos Consumo y se agregó un Sobrante):
los números de fila de la validación del 26-10 ya no sirven. Las filas de Ventas sin código (P-42) se hicieron el
28-10-2026; el borrador del Cierre se vacía el día de la salida (abajo); HOJITA (P-41) quedó cerrada.

### Consumos anotados como venta (P-40)

Hasta julio de 2026 un consumo se anotaba como venta en efectivo y se sacaba el mismo monto de Caja como gasto
Consumo en Transacc., a precio de venta. Al corregir se vio que desde el 17-07 el consumo no está en la hoja
Ventas: solo inflaba el Total del Cierre y el «Ventas» Caja de Transacc. (antes del 17-07 no hay hoja Ventas).

- **Cuando** hay un gasto Consumo en Transacc. **entonces** se corrige en tres hojas: en Transacc. se quita el
  gasto y se baja el «Ventas» Caja del día por el mismo monto; en Cierre de Caja se bajan el Total y los Gastos
  por el mismo monto (Resultado, caja final y sobrante no cambian); en Consumos se agrega la fila con fecha,
  cantidad, descripción, código y costo unitario, si no estaba. La hoja Ventas no se toca. Ningún saldo cambia.
- **Cuando** el consumo ya está en la hoja Consumos (17 a 23-07) **entonces** no se agrega de nuevo.
- Hecho: 59 gastos (Bs 1.006): 7 desde el 17-07 que ya estaban en Consumos y 52 del 04-05 al 13-07 (Bs 907),
  pasados a Consumos con el código y el costo que confirmó el dueño (53 filas, el del 15-06 dividido en barras y
  Frombombones; el Acualite, producto que ya no existe, como `MAN-LAT`). Costo unitario: el del catálogo al
  27-10, salvo la Alulosa (77). Transacc. ya no tiene la categoría Consumo.
- El almuerzo no es consumo (P-37): los dos «Almuerzo» de Transacc. anotados como Consumo pasaron a Comida, y el
  almuerzo del 12-10 de la hoja Consumos se quitó.
- La hoja Consumos tiene 141 filas del 04-05 al 26-10, todas con **Codigo** y **Costo unitario**; la columna de
  «para quién» se titula **Para quien**. La cuarta columna (precio en los consumos de octubre) queda sin título.

### Cierre igual a la hoja Ventas

El Total del Cierre se obtenía sumando la hoja Ventas. Hecho: desde el 17-07 coinciden todos los días.

- **Cuando** el Total del Cierre no coincide con la suma de Ventas del día **entonces** el Total pasa a ser la
  suma de Ventas; en Transacc. el «Ventas» Caja se iguala al nuevo Efectivo y el «Sobrante» al nuevo sobrante
  (se agrega la fila si no había). La caja final contada no cambia: la diferencia queda en el sobrante.
- **Cuando** el día no se contó (caja final con la fórmula `=J`, igual al Resultado) **entonces** la caja final se
  mueve sola y la diferencia cae en el sobrante del siguiente día que sí se contó.
- Corregidos 8 días: 17-07, 20-07, 29-07, 03-08 (no contado; pasa al 04-08), 04-08, 11-08, 14-08 y 30-09.
- Hay días «No se cerró» con la caja final escrita a mano en vez de `=J` (25-05, 01-06, varios de junio):
  correctos hoy, pero un cambio de su Total no pasaría al día siguiente.

### Filas de Ventas sin código (P-42)

Tienen nombre y precio de venta, pero no costo: no se identificó el producto o no estaba en el catálogo. No se
borraron productos de la hoja Productos. Al 26-10 son 327 filas.

- **Cuando** se revisan **entonces** se agrupan por nombre y precio, y para cada grupo se propone el producto de
  la hoja Productos que coincida; el dueño confirma y se completa el código.
- **Cuando** el producto se identifica **entonces** el costo sale, en este orden, de la fila, del Excel de costos
  del sistema anterior o del costo del catálogo.
- **Cuando** la fila es una venta de Mami (últimas semanas antes del corte) **entonces** se marca con el código
  del producto especial de Mami (POS-13).
- **Cuando** no se puede identificar **entonces** queda sin código y entra como venta sin identificar definitivo
  (POS-12, decidido el 28-10-2026): ya se revisó una por una, así que no queda por asignar.
- El Excel de costos del sistema anterior es un archivo de trabajo: no va al repositorio.
- **Cuando** el producto se vendió pero ya no se compra ni está en Productos, y el Excel anterior tiene su costo
  **entonces** se agrega a la hoja Productos y, después de importarlo (CAT-06, que crea todo Activo), se da de baja
  (inactivo): la migración acepta productos inactivos (solo ignora los eliminados) y la venta entra con código y
  utilidad. Si el proveedor no se conoce, va con el proveedor «Sistema anterior».
- **Cuando** el producto se vende por gramo (granel) **entonces** la Cant de la fila pasa a gramos y el Precio
  unitario a precio por gramo, como las demás filas de frutos secos.
- Hecho el 28-10-2026 con el dueño (las 327 filas del 26-10). El Excel anterior (`query_11`) solo trae nombre, costo
  y precio de venta: su costo se usa cuando producto y precio coinciden; si no, el del catálogo. Las ventas del 24 al
  26-10 ya estaban codificadas en la planilla. Resultado:
  - 236 filas con código y costo unitario; 5 de granel pasadas a gramos (Arándano, Cayú).
  - 25 productos nuevos al final de Productos (filas 886 a 910: `AMJ-ACE-BAC-90U`, `AMJ-MAG-TOT-100U`,
    `AMJ-CUR-110U`, `VBE-DIM-MAG-60U`, `VBE-COL-HID-300GR`, `VBE-MEL-LEO-POL-90GR`, `VIB-COL-T2D-60U`,
    `ALF-OM3-KID-60U`, `SOL-GLI-VEG-40ML`, `SOL-CAR-ACT-30GR`, `ANV-HOJ-QUI-250GR`, `ITE-HID-100ML`,
    `ITE-ACE-COC-200GR`, `PAT-BOL-SUR-50G`, `ORI-CAF-THO-250GR`, `GRA-MOS`, `UNI-CRE-REG-50GR`, `UNI-TON-MAN-140ML`,
    `UNI-TON-PMI-140ML`, `UNI-REP-60ML`, `INN-STE-HOJ-100GR` y los de «Sistema anterior» `ANT-VIT-BIO-500ML`,
    `ANT-CAF-CHU-320GR`, `ANT-CAF-EXO-250GR`, `ANT-ACE-OLI-500ML`), que se dan de baja después de importarlos.
  - 8 filas de Sagrada Madre (20 al 23-10) son de Mami: código `TER-SAG-MAD`, costo igual al precio.
  - 91 filas quedan sin código (sin nombre, genéricas o no identificadas) y entran como venta sin identificar.
- También el 28-10-2026: las 63 ventas del 27-10 codificadas, la fila 6059 (Stevia) con su precio unitario, costo
  de las filas 3418 y 3741 que no lo tenían, y Kefir Frutado Naturale con costo 22.
- **Cuando** una fila tiene Cant × Precio unitario distinto del Total **entonces** la migración usa Total ÷ Cant
  como precio; se corrige la Cant cuando el Total es el cobrado (cuadra con el Cierre). Hecho el 28-10-2026 con el
  dueño en las 8 filas que había: 567 → 3, 1052, 1053, 1783, 1811 y 1994 → 2, 1492 → 4 y 5022 → 1. Las filas 1401 y 1402
  (11-08) tenían el total conjunto de Bs 160 copiado en las dos: se corrigió a 64 y 96 y, con la regla de «Cierre
  igual a Ventas», el Total del Cierre del 11-08 bajó a 3.296, el «Ventas» Caja de Transacc. a 2.479 y el Sobrante
  pasó de −55 a 105 (la caja final contada no cambia).

### Pedidos (P-45 a P-47)

- **Cuando** se prepara la hoja Pedidos **entonces** se agrega la columna **Proveedor** con el nombre del
  proveedor tal como está en el sistema: «Nombre» es texto libre («Sativa Abril», «Colmena 4 mayo»). Claude
  propone el proveedor de cada pedido por su nombre y el dueño confirma, como con las filas de Ventas sin código.
  En los pedidos con líneas puede quedar vacía: se usa el Provedor de Detalle Pedidos.
- **Cuando** un pedido está «A Cuenta» **entonces** el dueño anota en la columna nueva **Monto pagado** cuánto se
  pagó; el resto queda por pagar.
- Hecho: columnas **Proveedor**, **Monto pagado** y **Revisar proveedor** (notas de la propuesta) al final de la
  hoja; todos los pedidos tienen proveedor, confirmado por el dueño. «Belvida» es Belen; Mandala e Impostor se
  unieron en **Minoli** (también en Productos y Detalle Pedidos). Monto pagado de los «A Cuenta»: 118 → 1.000,
  349 → 542, 483 → 144.
- **Cuando** un pedido se pagó en partes con estados distintos (pagado y consignación) **entonces** cada parte
  lleva su número: la migración arma un pedido por COD. La parte en consignación del 91 es el 523.
- **Cuando** un pedido no tiene Monto o tiene Monto 0 **entonces** se quita (la migración exige Monto mayor a 0)
  y se anota en el pedido relacionado si hace falta. Quitados: 10, 11 y 12 (de antes de la planilla, los pagó
  Mami, sin monto ni movimiento), 105 (no se cobró; nota en el 216), 205 (su monto está en el 235; nota ahí) y
  340 (Nutrabol, nunca se pagará).

### Otros arreglos encontrados al validar

Hechos: los «Ventas QR» de octubre tienen el monto en Monto y no quedan filas sin fecha, monto o cuenta; en
Transacc. «Leches Impostor» (16-05) es Otro Ingreso, los traspasos de QR a Mami (27-06, 02-07) son Otro gasto,
las Tucumanas del 02-06 son −13 (con el Sobrante y los Gastos del Cierre de ese día ajustados) y se corrigió el
Tipo de dos filas; en Pedidos Boni tiene número, todos los pedidos están Entregados; los
códigos de Ventas y Detalle Pedidos existen en Productos (se agregó `TNA-MIE-ABE-350GR`).

- Cierre de Caja: la última fila es el borrador del día siguiente (sin fecha ni caja final) y la migración la
  rechaza («Falta la fecha»). Decidido el 28-10-2026 con el dueño: el día de la salida se vacía esa última fila
  antes de descargar el `.xlsx`; la migración no cambia.
- En Finanzas → Categorías deben existir las categorías de Transacc. (Otro gasto, Otro Ingreso, Utilidad,
  Envio, Devolucion…). «Venta» usa la categoría de ventas del sistema aunque se llame «Ventas».

### HOJITA (P-41)

- **Cuando** HOJITA tiene algo que no está en Consumos, Pedidos o Transacc. **entonces** se pasa a esas hojas
  antes de migrar.
- **Cuando** un pedido lo pagó una persona y todavía se le debe **entonces** queda «Por Pagar» en la hoja Pedidos
  (la migración solo importa pagos desde Caja o Bisa y no crea deudas con personas, P-16) y, después de migrar, se
  registra en el sistema el pago del pedido hecho por esa persona (DEU-02): sin movimiento de dinero, deja la deuda
  con ella, que se salda con una devolución (DEU-03).
- Cerrada el 27-10-2026 con el dueño (12-10 a 27-10): los consumos están en Consumos, los pedidos en Pedidos y los
  gastos y retiros en Transacc. (la devolución a Mami de 2.039,25 del 27-10 son los pedidos 517, 519 y 520). Se
  agregó el almuerzo del 12-10 (28, Comida) con el sobrante del día ajustado de −37 a −9 en Transacc. y en el Cierre;
  el taxi de Mami es Envio el 21-10 y el 27-10. Orégano 390 del 13-10 («Tía Neyda pagó por QR») se ignora: no fue
  de la tienda. El pedido 487 (Savia Herbolarita, 122) lo pagó Mami y se le debe: queda «Por Pagar» y después de
  migrar se registra como pagado por Mami (DEU-02).

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
  venta, historial de cajas, detalle de pedido y de consumo, Finanzas → Transacciones) y no se puede anular ni editar; el servidor lo
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
- **Cuando** un regalo o una rotura dice «precio costo» («Regalo Ali precio costo», «Mami rompio precio costo»)
  **entonces** la fila va a la venta `AL_COSTO` de ese día: quien regala o rompe pagó el costo (P-39, 28-10-2026).
  Las 4 filas de la planilla (25-08 fila 2247; 21-09 filas 3870, 3872 y 3873; Bs 126) se corrigieron así en la
  planilla el 28-10-2026; la hoja Consumos no tenía ninguna.
- **Cuando** el comentario empieza con «Regalo» o dice «rompió» sin «precio costo» **entonces** la fila entra como
  venta normal y Revisar muestra el aviso «<comentario> parece un regalo o una rotura: entra como venta normal. Si
  se pagó al costo, agregar «precio costo» al comentario (P-39).»
- **Cuando** la fila no tiene código **entonces** la línea va al «Producto sin identificar» con el nombre y el
  precio de la fila, costo 0 y estado `SIN_IDENTIFICAR` (sin identificar definitivo, POS-12, decidido el
  28-10-2026), con quien importa y la hora de la importación como quién y cuándo. No aparece en el filtro «Por
  asignar» ni se asigna después (la venta histórica es de solo lectura); en los reportes va en la columna «Sin
  identificar», fuera de la utilidad. Si falta crear ese producto: «Falta crear el «Producto sin identificar» en
  Productos (POS-12).»
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

Transacc. es la única fuente de movimientos de dinero históricos (P-43, confirmado el 28-10-2026). Los pagos de
los pedidos históricos no se vinculan con su gasto Pedido de Transacc.: el dueño prefirió no emparejarlos (la fecha
de pago no siempre coincide y un vínculo equivocado es peor que ninguno), pero pidió revisar antes de importar que
coincidan (tarea abajo). Los pagos de pedidos nuevos sí quedan vinculados a su movimiento (COM-05).

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
- **Cuando** el Estado Pago es Pagado **entonces** se importa un pago por el Monto más el Monto Envio con la Fecha pago, desde Caja o
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
  corte; la tabla de rechazadas con hoja, fila y motivo (descargable como `.xlsx`); los avisos; las líneas sin
  identificar con su total en Bs; la deuda inicial con cada dueña de productos de terceros; y los totales por mes
  (ventas, utilidad, sin identificar, al costo, de terceros, consumos) y los movimientos por categoría y mes para
  compararlos con las tablas dinámicas.
- **Cuando** hay alguna fila rechazada **entonces** no se puede importar: el historial entra completo o no entra.
  «Hay filas rechazadas: se corrigen en la planilla y se revisa de nuevo. No se importó nada.»
- **Cuando** se importa **entonces** todo se guarda en una sola transacción: si algo falla, no queda nada a
  medias.
- **Cuando** termina **entonces** los reportes de cualquier mes migrado coinciden con las tablas dinámicas,
  salvo las diferencias conocidas: consumos al costo y fuera de las ventas (P-35, P-40; regalos y roturas siguen
  en las ventas, como venta al costo, P-39), costos completados en
  las filas sin código (P-42), ventas de Mami fuera de las ventas de la tienda (POS-13) y las filas que quedaron
  sin código fuera de la utilidad (columna Sin identificar, POS-12).

### P-43 con la planilla del 29-10

Pagos de Pedidos (Pagado por Monto, A Cuenta por Monto pagado) contra gastos con categoría Pedido de Transacc.:
500 pagos por 274.362,58 y 506 gastos por 276.432,05 (diferencia 2.069,47). Por mes (gastos − pagos): mayo +6.767,
junio +1.223, julio −4.034, agosto −1.053,03, septiembre −356,50, octubre +209 y 686 de pagos sin fecha. Por monto,
cuenta y fecha cercana se emparejan 480; quedan 20 pagos (7.012,73) y 26 gastos (9.082,20) sin par. Explicados:

- Un gasto por dos pedidos: Colmena 1.407 del 23-06 = pedidos 80 (1.207) + 94 (200).
- Pago en dos gastos, uno con otra categoría: Fit Mit 257 (780) = 325 Pedido + 455 «Fit Mit Tia Neida» Otro gasto
  (05-08); Madre Tierra 437 (649) = 629 + 20 «Pago extra» Otro gasto (28-09).
- Gasto con categoría Otro gasto en vez de Pedido: Cuñapizzas 237 (315, fila 708), Suerox 334 (245, fila 999),
  Vitaly 341 (353, fila 1010).
- Centavos: Minoli 302 (164,43 / 164,40), Itenez 431 (334,05 / 334), Itenez 517 (454,75 / 454,80).
- Altramuz 161 + 195 (75 + 25 = 100) contra un gasto de 150 (fila 763, Bisa).
- A Cuenta sin Fecha pago ni Tipo Pago: 349 Glamazon (542 = «Adelanto Glamazon» del 02-09, Caja) y 483 Green Vibe
  (144 = «Pago parcial green vibe» del 23-10, Caja); su pago entraría sin fecha.

Sin explicación: pagos sin gasto (49 Misqui 238, 150 Solquifar 248, 343 Saltus 314, 345 Melifo 235,50, 406 Misqui
374) y gastos Pedido sin pedido (mayo: Pan 1.321, Kefir Belen 323, Belen 250, 517 y 285,50, Frombombones 276 y 253;
Aceite Borges 960 del 01-06, Vitabec 421 del 15-06, Apis 30, Sobres 45, Jorge Junio 100, Faltante sativa griegos
40,50; y 4 envíos con categoría Pedido: Brownies Vikingos 30, Alulosas 15, Activaloe 50, Pastel de frutas 15).

Revisado con el dueño el 29-10-2026. Corregido en la planilla, con un comentario en cada fila de Pedidos y Transacc.
que explica el cambio o la diferencia:

- Transacc. 708, 999 y 1010 (Cuñapizzas, Suerox, Vitaly) pasan a Pedido; Jorge Junio (531) a Otro gasto; los envíos
  1083, 1160, 1359 y 1412 a Envio.
- Pedido 161 Altramuz: Monto 100 (era 75); con el 195 suman 125 contra el gasto de 150: faltan 25, sin revisar.
- Pedido 257 Fit Mit: Monto 325, la parte de la tienda. Fue un pedido conjunto con Tía Neida: sus 455 se pagaron de
  Caja (Otro gasto, fila 767) y los devolvió el 11-08 (Otro Ingreso, fila 806).
- Pedido 437 Madre Tierra: Monto 629 (se cobró 20 de más y se devolvió).
- A Cuenta: 349 Glamazon con Fecha pago 02-09 y Caja (se deben 1.000); 483 Green Vibe con 23-10 y Caja (el 144 del
  01-10 es el pedido 439).
- Centavos (302, 431, 517): se dejan; comentario «Se pagó redondeado».
- Lo demás sin explicación se deja como está, con comentario «P-43, sin revisar»: no cambia saldos (se cargan al
  corte) ni pagos (los históricos no mueven dinero), solo el gasto Pedido de mayo y junio en los reportes.

### Importación en la base local del 29-10

Con los `.xlsx` del 29-10 y corte 27-10: Ventas 6.116, Transacc. 1.519, Cierre 145, Consumos 143, Pedidos 517 y
Detalle Pedidos 927, todas importadas y 0 rechazadas; 22 avisos, 91 líneas sin identificar (4.741), deuda inicial con
Mami 246. Saldos al corte cargados antes (Caja 3.392,90 del Cierre; Bisa 30.431,58 y Efectivo 16.332,04, sumas de
Transacc. solo para la prueba local) y sin cambio después de importar. Para llegar a 0 rechazadas:

- En Finanzas → Categorías se crearon Comida, Otro gasto, Envio, Utilidad y Devolucion (gasto) y Otro Ingreso
  (ingreso). En producción se crean igual antes de revisar.
- Detalle Pedidos 927 y 928 (Kefir Belen del 27-10) llevan el pedido 522; Ventas 4865 y 4866 quedan en 381 g y 263 g
  (los kilos van hasta el gramo; el total no cambia) y la 5802 (22-10, Bs 27) con «Sin descripción».
- Cierre de Caja: no había borrador; las filas sin fecha debajo del 27-10 solo tienen fórmulas en 0 y no se leen.

Hallazgo: un pedido Pagado con Monto Envio se importaba con un pago por el Monto, sin el envío, y quedaba en Por
pagar por el envío (36 pedidos, de 8 a 80, por ejemplo `PED-H00431`). Corregido el 29-10-2026: el pago es Monto +
envío. Ensayo repetido ese día en la base local recreada vacía (respaldo en `~/akhana-backups`, fuera del
repositorio): productos, Mami, `SIN-IDENT`, `TER-SAG-MAD`, 25 de P-42 inactivos, 6 categorías, saldos y el historial
con 0 rechazadas. Quedan 20 pedidos en Por pagar, todos A Cuenta, Por Pagar o en consignación en la planilla.

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

## Estado de la importación

- **Cuando** se entra a Migración y la importación ya se hizo **entonces** la pantalla lo dice antes de elegir
  archivos («La migración histórica ya se hizo el <fecha> por <usuario>, con fecha de corte <día>. Solo se hace una
  vez.») y deja los archivos, la fecha y los botones deshabilitados. `GET /api/migration/status` (permiso
  `MIGRATION`) devuelve `imported`, `cutoffDate`, `importedBy` e `importedAt`. Pruebas en
  `MigrationIntegrationTest`, `EndpointAuthorizationTest` y `migration.component.spec.ts` (tercera revisión,
  29-10-2026).

## Tareas

Antes de construir:

- [x] Construir POS-11 a POS-13 (spec en `specs/sales.md`): la migración usa sus productos especiales.
- [x] Validar con la planilla P-43: los montos coinciden en total; supuesto confirmado el 28-10-2026 (arriba).
- [x] Validar con la planilla los nombres de columnas de cada hoja, la cuenta de destino de una transferencia
  (columna Cuenta Destino) y cómo se reconoce un regalo o una rotura en Ventas (comentario).

Correcciones en la planilla (con el dueño, en otra sesión):

- [ ] Copia de respaldo de las dos planillas.
- [ ] Lista por día de consumos anotados como venta y corrección en Ventas, Transacc., Cierre de Caja y Consumos.
- [ ] Código y costo unitario en cada fila de la hoja Consumos (el dueño).
- [x] Revisión de las filas de Ventas sin código con el Excel de costos del sistema anterior; ventas de Mami
  marcadas (28-10-2026).
- [x] Día de la salida, antes de migrar: crear la persona Mami en Deudas y los productos especiales `SIN-IDENT`
  (Sin identificar) y `TER-SAG-MAD` (De tercero, dueña Mami); importar los productos y dar de baja los 25 nuevos
  de P-42. Ensayado el 29-10-2026 en la base local recreada vacía (respaldo de la anterior fuera del repositorio):
  `SIN-IDENT` «Producto sin identificar» (Nutricion, Sistema anterior) y `TER-SAG-MAD` «Sagrada Madre de Mami»
  (categoría Inciensos y proveedor Sagrada Madre creados para P-50); los 25 quedan Inactivo. En producción se
  repite igual.
- [x] Con el dueño: Cant corregida en las 8 filas de Ventas donde Cant × Precio no daba el Total (28-10-2026).
- [ ] Columna Proveedor en Pedidos (Claude propone, el dueño confirma) y Monto pagado en los pedidos A Cuenta.
- [ ] Arreglos de «Otros arreglos encontrados» y categorías faltantes en Finanzas.
- [x] HOJITA vaciada en las otras hojas (27-10-2026).
- [x] Antes de importar (P-43, 28-10-2026): comparar por mes los pagos de Pedidos (Fecha pago, Monto o Monto
  pagado, Tipo Pago) con los gastos Pedido de Transacc. (fecha, monto, cuenta) y listar los que no tienen par;
  revisar con el dueño las diferencias (total 3.755,42, mayo +6.767, julio −4.034 y los 5 montos de octubre) y
  corregir en la planilla lo que sea error. Sin cambio de código: los pagos históricos siguen sin vínculo.
  Comparación hecha el 29-10-2026 y revisada con el dueño ese día (arriba, «P-43 con la planilla del 29-10»).
- [x] Con el dueño, antes de migrar (hecho el 29-10-2026: precios con el último precio de venta, la gelatina a 17;
  4866 a `FSE-GRA-ESP` 263 g, 5759 a 100 g a 0,09; A1 ya decía «Grupo»; productos reimportados): precio de venta de los 16 productos rechazados por la importación que se usan en
  el historial (12 filas de Ventas y 12 de Detalle Pedidos: `GRA-GEL-SSA`, `BEN-ESP-ACL-100GR`, `APQ-FID-QUI-1KG`,
  `IVL-REN`, `DES-HAR-COC-500GR`, `ORI-SRO-DOY-800GR`, `ORI-SRO-GRA-800GR` y 9 de Brisol `BRI-*`); Ventas filas 4866
  (`FSE-GRA-ESP-1LI` con Cant 262,5: se leería como 262,5 libras) y 5759 (`FSE-GRA-ESP` con Cant 1 a Bs 9: 1 g);
  las diferencias de P-43; «Grupo» en A1 de Productos. Después, importar de nuevo los productos (sale sin duplicar).
- [ ] Después de migrar: registrar el pago de `PED-H00487` hecho por Mami (DEU-02).

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
  saldo y apertura sin históricos). Desde la segunda revisión (29-10-2026) la prueba se omite, en vez de fallar,
  cuando la base ya tiene una importación (la local tras el ensayo): la importación es una sola vez. Dos importaciones
  a la vez ya no pasan ambas la verificación: `importHistory` toma un lock de PostgreSQL de la transacción
  (`pg_advisory_xact_lock`) antes de comprobar que no hay otra, así la segunda espera a la primera y la rechaza
  («ya se importó»). Prueba concurrente en `SerializationLockIntegrationTest`.
- [x] Frontend: pantalla Migración con Revisar / Importar, resultado y descarga de rechazadas; marca «Histórico»
  en ventas, cajas, pedidos y consumos.
- [x] Validar con los `.xlsx` reales (arriba).
- [ ] Marca «Histórico» en Finanzas → Transacciones: `transactions.component.html` es muy grande; se divide antes
  de tocarlo. Mientras tanto el servidor rechaza editar o anular un movimiento histórico con su mensaje.
- [x] Regalos y roturas a la venta al costo (P-39, 28-10-2026, rama `feat/migration-gifts`): las 4 filas de la
  planilla llevan «precio costo» en el comentario; en `MigrationServiceImpl` la regla «Regalo…»/«…rompió» →
  Consumos se cambió por un aviso en Revisar. `MigrationIntegrationTest` cubre el regalo sin «precio costo» (aviso
  y venta normal) y la rotura con «precio costo» (venta al costo). La pantalla no cambia: los avisos ya se muestran.
- [x] Pedido Pagado con Monto Envio: el pago histórico cubre Monto + envío, para que no quede en Por pagar
  (hallazgo del 29-10-2026), con su caso en `MigrationIntegrationTest` (29-10-2026).
- [ ] El día de la salida: vaciar la última fila del Cierre (borrador) si la hay antes de descargar el `.xlsx`,
  importar productos (CAT-06), crear las categorías de Transacc. en Finanzas, cargar saldos al corte (FIN-01),
  revisar e importar. Ensayado en la base local el 29-10-2026 (arriba).

## Notas

- Saldos de referencia antes del corte (de `requirements.md` §2.3): 23 de octubre: Caja Bs 7.637,90; Bisa Bs 31.562,58; Efectivo Bs 11.352,04.
