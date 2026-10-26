# Reportes

Detalle del módulo Reportes de la V1 (FIN-10 en la sección 3.8 y CON-03 en la 3.5 de `specs/requirements.md`),
con los mismos IDs. Reemplaza las tablas dinámicas de *Akhana Recaudaciones*; en la fase 2 cada reporte se compara
con su tabla durante dos semanas (sección 2.3).

Moneda Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales (kilos en los productos por peso,
CAT-08). Los días son días de la tienda (America/La_Paz), como en el historial de ventas (POS-09). Los reportes
solo leen: no crean ni cambian registros. Lo anulado (ventas, consumos, movimientos, cobros) nunca cuenta.

## Lo que muestra la planilla (validación del 26-10-2026)

Revisadas las tablas dinámicas de las hojas Resumen Ventas Diario, Resumen Ventas y Resumen Transacc., y sus
hojas de origen Ventas y Transacc.

| Tabla dinámica | Origen y agrupación | Valores | Ejemplo real |
| --- | --- | --- | --- |
| Resumen Ventas Diario | Ventas, por mes y día | Suma de Utilidad, Cant y Total, con subtotal por mes y suma total | 17-07: Total 2.745, Utilidad 640,84, Cant 74. Julio: Total 37.688, Utilidad 8.505,78. Total del 17-07 al 23-10: Bs 224.428,50 y utilidad Bs 51.493,34 |
| Resumen Ventas | Ventas, por proveedor y mes | Suma de Cant, Total y Utilidad, con total por proveedor | Bohemia: jul 2 / 160 / 16, ago 2 / 160 / 16, sep 3 / 230 / 28; total 7 / 550 / 60. Fila sin proveedor: 171 / 8.809,50 / 20 |
| Resumen Transacc. | Transacc., por mes y categoría | Suma de Monto (ingresos +, gastos −) | Agosto: Venta 67.031,50, Pedido −67.097,56, Comida −2.530, Retiro caja 19.500, Deposito 7.500, Otro 2.275,03 |

Hallazgos que cambian cómo se compara en la fase 2:

| Caso real | Qué hace hoy la planilla | Qué hará el sistema |
| --- | --- | --- |
| Días sin ventas (19-07, 26-07) | No aparecen en la tabla. | Tampoco aparecen: solo días con al menos una venta activa. |
| 31-08 Cant 437, 20-10 Cant 572 | Los productos por peso se anotan en gramos (o en unidades sueltas) y se suman con las unidades. | La cantidad de un producto por peso está en kilos (0,100 por 100 g). La columna Cantidad no coincide con la planilla esos días: se comparan Total y Utilidad. |
| Total de la hoja Ventas | Precio × cantidad por línea; los descuentos se anotan bajando el precio unitario. | Total = total cobrado de la venta (después de descuentos por unidad y general), lo pagado en el momento más la parte a crédito. |
| «Mami precio costo» (25-09, 28-09), «Regalo Ali» (21-09), «Mami rompió» (25-08) | Filas de la hoja Ventas con utilidad 0, mezcladas con las normales. | La venta al costo va en sus propias columnas (CON-03); regalos y roturas son consumos (P-39) y no están en ventas. Para comparar: Total planilla = normales + al costo + regalos y roturas al costo. |
| Fila sin proveedor de Resumen Ventas (Bs 8.809,50) | Productos sin proveedor en la hoja Ventas. | En el sistema todo producto tiene proveedor (`products.supplier_id` obligatorio): esas ventas solo pueden llegar con la importación (FIN-11), que debe asignarles un proveedor o dejarlas en «Sin proveedor». |
| Transferencias (8-10 «Retiro Caja 3000», categoría Retiro caja, Caja → Efectivo; «Prestamo Joaco 115», Otro) | Una sola fila en positivo con la categoría, así que Retiro caja y Deposito suman como si fueran ingresos. | Las transferencias van en un bloque aparte por cuenta de origen y destino; no suman en ingresos ni gastos porque no cambian el saldo consolidado. Para comparar: Retiro caja de la planilla = transferencias Caja → Efectivo del mes. |
| Categoría Envio (oct, «Santa Trinidad Envio −25») y «Envio activaloe −50» como Pedido | El envío está a veces en Envio y a veces en Pedido. | El envío de un pedido es Transporte (P-24); Pedido no incluye envíos. Para comparar, sumar Envio y los envíos anotados como Pedido. |
| Venta (6-10: «Ventas» 1.075 en Caja y «Ventas QR» 1.362 en Bisa) | Dos filas al cierre del día. | Movimientos `VENTA_INGRESO` de cada venta (FIN-07) más los cobros a la tienda amiga (DEU-06, P-34): la línea Venta del mes coincide en monto con la planilla. Una venta a crédito entra a Venta cuando se cobra, no cuando se vende. |
| Consumo (jul −393, jun −338, may −420) | Gasto de Caja a precio de venta, hasta julio. | Desde la salida, línea Consumo desde los consumos activos, al costo y marcada «sin movimiento de dinero» (CON-03, P-35). Lo de mayo a julio llega como movimiento histórico (FIN-11). |

## Permiso y pantalla

- Pantalla **Reportes** (`/reports`, menú Finanzas → Reportes), permiso nuevo `REPORTS`, solo Administrador
  (tabla 2.2). Cada reporte es una pestaña con su filtro de fechas.
- **Cuando** un operador llama a `/api/reports/**` **entonces** recibe 403, y la pantalla no aparece en su menú.
- **Cuando** se abre un reporte **entonces** el rango propuesto es el mes en curso hasta hoy.
- **Cuando** la fecha desde es posterior a la fecha hasta **entonces** «La fecha desde no puede ser posterior a la
  fecha hasta.»; si el rango pasa de 366 días, «El rango del reporte no puede superar un año.»

## FIN-10 · Ventas por día (con CON-03)

Reemplaza Resumen Ventas Diario. Una fila por día con ventas, agrupadas por mes con subtotal y total del rango.

| Grupo | Columnas |
| --- | --- |
| Ventas normales | Ventas (cantidad de tickets), Cantidad, Total, Costo, Utilidad, Margen % |
| Ventas al costo | Ventas, Cantidad, Total |
| Total | Total (normales + al costo), Utilidad |

- **Cuando** el 17-07 hay ventas normales por Bs 2.745,00 con costo Bs 2.104,16 **entonces** la fila del 17-07
  muestra Total Bs 2.745,00, Costo Bs 2.104,16, Utilidad Bs 640,84 y Margen 23,35 %.
- **Cuando** una venta normal tiene 2 líneas (2 × Bs 10,00 con costo Bs 6,00 y 0,250 kg × Bs 80,00 con costo
  Bs 60,00 por kilo) y un descuento general de Bs 5,00 **entonces** su total es Bs 35,00, su costo Bs 27,00
  (2 × 6,00 + 0,250 × 60,00), su utilidad Bs 8,00 y suma 2,250 a la Cantidad.
- **Cuando** el costo se toma de la venta **entonces** es la copia de cada línea (`sale_items.unit_cost`, POS-05):
  un cambio posterior del costo del producto no cambia el reporte.
- **Cuando** un día hay una venta al costo de Bs 104,00 (1 Aceite de coco 500 ml a Mami) **entonces** va en Ventas al
  costo con Total Bs 104,00, no cambia la Utilidad ni el Margen de las normales, y el Total del día la incluye
  (CON-03).
- **Cuando** una venta al costo quedó a crédito a Tía Neida **entonces** cuenta igual el día en que se vendió, por
  su total; el cobro posterior no es una venta.
- **Cuando** una venta está anulada **entonces** no cuenta, aunque se haya anulado otro día.
- **Cuando** una venta se registra a las 23:30 del 17-07 en Bolivia (03:30 del 18-07 UTC) **entonces** cuenta en
  el 17-07.
- **Cuando** un día no tiene ventas activas **entonces** no aparece; un mes sin ventas tampoco.
- **Cuando** se calcula el costo de un día **entonces** se suman cantidad × costo de sus líneas sin redondear y el
  resultado se redondea a 2 decimales; la utilidad del día es total − costo ya redondeado. Los subtotales del mes
  y el total del rango suman las filas de los días, para que la tabla cuadre a la vista. El margen es utilidad ÷
  total de las normales × 100, con 2 decimales, y queda vacío si el total de las normales es 0.
- **Cuando** se comparan en la fase 2 **entonces** el Total del día del sistema más los regalos y roturas al costo
  de ese día es igual al Total de la planilla, y la Utilidad de las normales a su Utilidad (los regalos tienen
  utilidad 0 en la planilla). La Cantidad solo se compara en días sin productos por peso.

## FIN-10 · Ventas por proveedor y mes (con CON-03)

Reemplaza Resumen Ventas. Una fila por proveedor con sus meses debajo y su total.

- **Cuando** se ve Bohemia de julio a septiembre **entonces** muestra por mes Cantidad, Total, Costo, Utilidad y
  Margen % de las ventas normales, y Cantidad y Total de las ventas al costo; el total de Bohemia suma sus meses.
- **Cuando** se asigna una línea a un proveedor **entonces** se usa el proveedor actual del producto (todo producto
  tiene uno); solo las líneas históricas sin proveedor (FIN-11) van en «Sin proveedor».
- **Cuando** una venta tiene descuento general **entonces** se reparte entre sus líneas en proporción a su
  subtotal (el resto por redondeo va a la línea de mayor subtotal), así la suma de todos los proveedores del día
  es igual al total de ventas por día.
- **Cuando** se calcula el margen **entonces** se usa solo lo normal: las ventas al costo no bajan el margen
  (CON-03).

## FIN-10 · Movimientos por categoría y mes (con CON-03)

Reemplaza Resumen Transacc. Por mes: ingresos por categoría (+), gastos por categoría (−), neto, y aparte las
transferencias y el consumo.

- **Cuando** se ve agosto **entonces** cada categoría suma sus movimientos activos del mes (fecha del movimiento,
  día de la tienda): Venta, Otro ingreso y Ajuste de ingreso en positivo; Pedido, Transporte, Comida, Otro gasto,
  Utilidad y Ajuste de gasto en negativo; el neto es la suma.
- **Cuando** hay un pedido pagado desde Caja con su envío **entonces** el pago suma en Pedido y el envío en
  Transporte (P-24).
- **Cuando** una persona pagó un pedido (DEU-02) **entonces** no hay movimiento hasta la devolución, que suma en
  Pedido (P-34).
- **Cuando** hay transferencias (retiro de caja Caja → Efectivo, depósito Efectivo → Bisa) **entonces** se
  muestran en un bloque «Transferencias» con origen → destino y monto del mes, sin sumar en el neto.
- **Cuando** el mes tiene saldos iniciales (`SALDO_INICIAL`) **entonces** no cuentan como ingreso.
- **Cuando** hay consumos activos en el mes **entonces** aparece la línea Consumo con su total al costo, marcada
  «sin movimiento de dinero», fuera del neto (CON-03, FIN-08).
- **Cuando** una categoría está desactivada **entonces** sus movimientos siguen apareciendo con su nombre.

## FIN-10 · Totales de ventas y utilidad (con CON-03)

- **Cuando** se ve el resumen del rango por mes **entonces** muestra ventas normales (total, costo, utilidad,
  margen), ventas al costo (total), consumos al costo, la utilidad de las ventas y, aparte, la utilidad menos los
  consumos del mes.
- **Cuando** en julio la utilidad de las ventas es Bs 8.505,78 y los consumos al costo Bs 300,00 **entonces** la
  utilidad menos consumos es Bs 8.205,78.

## CON-03 · Consumos al costo

- **Cuando** se ve el reporte de consumos **entonces** muestra por día y mes la cantidad de consumos, la cantidad
  de productos y el total al costo, y por proveedor y mes la cantidad y el total al costo de sus productos.
- **Cuando** un consumo está anulado **entonces** no cuenta.
- **Cuando** se compara con la hoja Consumos en la fase 2 **entonces** se comparan cantidades por producto, no
  montos: la planilla los anota a precio de venta (P-35).

## FIN-10 · Exportar a Excel

- **Cuando** se pulsa «Exportar a Excel» en un reporte **entonces** se descarga un `.xlsx` con el mismo rango, las
  mismas columnas y filas (incluidos subtotales por mes y total), montos como números con 2 decimales, cantidades
  con 3 y el nombre `akhana-<reporte>-<desde>-<hasta>.xlsx`.
- Supuesto: lo genera el backend (Apache POI) en `GET /api/reports/<reporte>/export`, para que los números del
  archivo sean exactamente los de la pantalla. Se agrega cuando estén los cuatro reportes.

## Históricos (FIN-11)

- **Cuando** se importen las ventas y movimientos desde julio **entonces** alimentan estos mismos reportes: cada día
  de la hoja Ventas es una venta histórica con sus líneas y costo (P-32), y cada fila de Transacc. un movimiento
  histórico con su categoría. Así el reporte de julio a octubre se puede comparar entero con las tablas
  dinámicas. Se define con FIN-11.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/reports/sales-by-day?from=&to=` | Ventas por día, con subtotales por mes y total |
| GET | `/api/reports/sales-by-supplier?from=&to=` | Ventas por proveedor y mes |
| GET | `/api/reports/transactions-by-category?from=&to=` | Movimientos por categoría y mes, transferencias y consumo |
| GET | `/api/reports/totals?from=&to=` | Totales de ventas, utilidad y consumos por mes |
| GET | `/api/reports/consumptions?from=&to=` | Consumos al costo por día, mes y proveedor |
| GET | `/api/reports/<reporte>/export?from=&to=` | El mismo reporte en `.xlsx` |

Todas con permiso `REPORTS`. `from` y `to` son días de la tienda (`to` incluido); si faltan, el mes en curso
hasta hoy.

## Tareas

- [x] Spec validada con las tablas dinámicas (26-10-2026).
- [x] Permiso `REPORTS` (Administrador), pantalla `/reports` en diferido y menú Finanzas → Reportes.
- [x] Ventas por día: consulta agrupada por día de la tienda y tipo de venta, servicio con subtotales por mes,
  `GET /api/reports/sales-by-day`, pestaña en la pantalla. Pruebas en `ReportServiceTest`,
  `SaleRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest` y `report.service.spec.ts`.
- [ ] Ventas por proveedor y mes, con el reparto del descuento general.
- [ ] Movimientos por categoría y mes, con transferencias y la línea Consumo.
- [ ] Totales de ventas y utilidad, y consumos al costo.
- [ ] Exportar a Excel los cinco reportes.
- [ ] Fase 2: comparar dos semanas con las tablas dinámicas.
