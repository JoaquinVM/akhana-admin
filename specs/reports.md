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
| «Mami precio costo» (25-09, 28-09), «Regalo Ali» (21-09), «Mami rompió» (25-08) | Filas de la hoja Ventas con utilidad 0, mezcladas con las normales. | La venta al costo va en sus propias columnas (CON-03); regalos y roturas también son ventas al costo y la migración los carga así (P-39, 28-10-2026; en la planilla dicen «precio costo»). Para comparar: Total planilla = normales + al costo, sin sumar consumos aparte. |
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
- **Cuando** hay líneas libres (POS-11 a POS-13) **entonces** van en las columnas Por asignar y Sin identificar
  (suman al total del día, no a la utilidad) y De terceros (fuera del total); detalle en `specs/sales.md`,
  «Reportes con ventas por asignar, sin identificar y de terceros».
- **Cuando** se calcula el costo de un día **entonces** se suman cantidad × costo de sus líneas sin redondear y el
  resultado se redondea a 2 decimales; la utilidad del día es total − costo ya redondeado. Los subtotales del mes
  y el total del rango suman las filas de los días, para que la tabla cuadre a la vista. El margen es utilidad ÷
  total de las normales × 100, con 2 decimales, y queda vacío si el total de las normales es 0.
- **Cuando** se comparan en la fase 2 **entonces** el Total del día del sistema (normales + al costo, con los
  regalos y roturas como ventas al costo, P-39 del 28-10-2026) es igual al Total de la planilla, y la Utilidad de
  las normales a su Utilidad (las ventas al costo tienen utilidad 0 en las dos). La Cantidad solo se compara en días sin productos por peso.

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
- **Cuando** se calcula el costo **entonces** se suman cantidad × costo de las líneas del proveedor en el mes sin
  redondear y se redondea una vez; el total del proveedor y el del rango suman sus meses. El total cobrado del
  rango es igual al de ventas por día; el costo puede diferir en centavos porque allí se redondea por día.
- **Cuando** se busca un proveedor en la pestaña **entonces** solo se filtran las filas a la vista; el total del
  rango sigue siendo el de todos.
- Hecho el 26-10-2026: `GET /api/reports/sales-by-supplier?from=&to=` (mismo rango y validaciones que ventas por
  día). El descuento general de cada venta es la suma de los subtotales de sus líneas menos el total de la venta.
  Proveedores por nombre, «Sin proveedor» al final. Validado con las siete filas de Bohemia de la hoja Ventas
  (Catuai 80 con costo 72 el 28-07, 30-07, 18-08 ×2 y 25-09; Blend 75 con costo 65 dos veces el 29-09): da
  jul 2 / 160 / 16, ago 2 / 160 / 16, sep 3 / 230 / 28, total 7 / 550 / 60, igual que Resumen Ventas.
  Hallazgo: la tabla dinámica no incluye la venta del 16-10 (fila 5474, Java 97 con utilidad 10), así que en la
  fase 2 hay que revisar su rango de origen o filtro antes de comparar octubre.

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
- Hecho el 26-10-2026: `GET /api/reports/transactions-by-category?from=&to=` (mismo rango y validaciones que ventas
  por día). Ingresos = `VENTA_INGRESO` e `INGRESO_MANUAL` (incluye cobros a clientes y el Ajuste por sobrante);
  gastos = `GASTO_MANUAL`, en negativo. `TRANSFERENCIA` va al bloque por par de cuentas y `SALDO_INICIAL` no
  aparece. Consumo = consumos `REGISTRADO` por `consumption_date`, su total al costo, en negativo y en cursiva en la
  pantalla. Categorías por nombre, «Sin categoría» al final; un mes aparece si tiene movimientos, transferencias o
  consumos. En la pantalla una columna por mes y una con el total del rango, como la tabla dinámica.
  Validado el 26-10-2026 cargando las 295 filas de agosto de la hoja Transacc. en PostgreSQL (transacción revertida)
  y pasando el reporte: Venta 67.031,50, Pedido −67.097,56, Comida −2.530, Otro gasto −2.607, Otro Ingreso 835,72,
  Transporte −320; transferencias Caja → Efectivo 19.500 (Retiro caja), Efectivo → Bisa 7.500 (Deposito),
  Bisa → Efectivo 1.382,03 y Caja → Bisa 893. Neto −5.332,64; con las transferencias da 23.942,39, el «Total agosto».
  Hallazgos para la fase 2: la categoría Otro de la planilla (2.275,03 en agosto) son solo transferencias (préstamos
  Bisa → Efectivo, Caja → Bisa), así que en el sistema está en Transferencias y no en ingresos; Ajuste de la planilla
  (−645,30) = Ajuste de ingreso (753,50) + Ajuste de gasto (−1.398,80), porque CAJ-09 usa dos categorías; y las
  devoluciones anotadas como Gasto con monto positivo en Otro Ingreso son ingresos en el sistema.

## FIN-10 · Totales de ventas y utilidad (con CON-03)

- **Cuando** se ve el resumen del rango por mes **entonces** muestra ventas normales (total, costo, utilidad,
  margen), ventas al costo (total), consumos al costo, la utilidad de las ventas y, aparte, la utilidad menos los
  consumos del mes.
- **Cuando** en julio la utilidad de las ventas es Bs 8.505,78 y los consumos al costo Bs 300,00 **entonces** la
  utilidad menos consumos es Bs 8.205,78.
- **Cuando** un mes tiene consumos pero no ventas **entonces** aparece con ventas en 0, margen vacío y la utilidad
  menos consumos en negativo.
- Hecho el 26-10-2026: `GET /api/reports/totals?from=&to=` (mismo rango y validaciones que ventas por día) toma los
  subtotales por mes y el total del rango de ventas por día, así ambos reportes muestran siempre el mismo total,
  costo y utilidad, y les agrega los consumos `REGISTRADO` del mes (cantidad y total al costo, la misma consulta de
  movimientos por categoría). Utilidad = utilidad de las normales (las ventas al costo, por asignar y sin
  identificar no la cambian); utilidad menos
  consumos = utilidad − consumos al costo. Un mes aparece si tiene ventas o consumos. Pestaña «Totales» con una fila
  por mes y el total del rango. Validado con julio de Resumen Ventas Diario en `ReportServiceTest`: normales
  Bs 37.584 con utilidad Bs 8.505,78, más una venta al costo de Bs 104 da el Total 37.688 de la planilla; con
  consumos por Bs 300 la utilidad menos consumos es Bs 8.205,78. Falta compararlo con las ventas reales de julio
  cuando se importen (FIN-11).

## CON-03 · Consumos al costo

- **Cuando** se ve el reporte de consumos **entonces** muestra por día y mes la cantidad de consumos, la cantidad
  de productos y el total al costo, y por proveedor y mes la cantidad y el total al costo de sus productos.
- **Cuando** un consumo está anulado **entonces** no cuenta.
- **Cuando** se compara con la hoja Consumos en la fase 2 **entonces** se comparan cantidades por producto, no
  montos: la planilla los anota a precio de venta (P-35, confirmado el 28-10-2026).
- Hecho el 26-10-2026: `GET /api/reports/consumptions?from=&to=` (mismo rango y validaciones que ventas por día)
  toma los consumos `REGISTRADO` por `consumption_date`. Por día: cantidad de consumos, cantidad de productos (suma
  de las cantidades de sus líneas, kilos en los productos por peso) y total al costo (`total_amount`); subtotal por
  mes y total del rango suman los días; solo días con consumos. Por proveedor y mes: cada línea va al proveedor
  actual del producto, como en ventas por proveedor, con la suma de sus cantidades y de sus subtotales al costo;
  proveedores por nombre, «Sin proveedor» al final. Ambos bloques dan el mismo total del rango porque el total de un
  consumo es la suma de sus líneas (CON-01). Pestaña «Consumos» con las dos tablas y búsqueda de proveedor que solo
  filtra la vista.

## FIN-10 · Exportar a Excel

- **Cuando** se pulsa «Exportar a Excel» en un reporte **entonces** se descarga un `.xlsx` con el mismo rango, las
  mismas columnas y filas (incluidos subtotales por mes y total), montos como números con 2 decimales, cantidades
  con 3 y el nombre `akhana-<reporte>-<desde>-<hasta>.xlsx`.
- Supuesto: lo genera el backend (Apache POI) en `GET /api/reports/<reporte>/export`, para que los números del
  archivo sean exactamente los de la pantalla. Se agrega cuando estén los cuatro reportes.
- **Cuando** el rango no es válido **entonces** no se descarga nada y se muestra el mismo mensaje que en la pestaña.
- Hecho el 26-10-2026: `GET /api/reports/<reporte>/export?from=&to=` para `sales-by-day`, `sales-by-supplier`,
  `transactions-by-category`, `totals` y `consumptions`, permiso `REPORTS`. `ReportExportServiceImpl` arma el
  `.xlsx` con Apache POI (`poi-ooxml`) a partir de la misma respuesta de `ReportService`, así que el rango propuesto
  y las validaciones son los del reporte y el nombre usa el rango resuelto (sin fechas, el mes en curso), por
  ejemplo `akhana-sales-by-day-2026-10-01-2026-10-26.xlsx`. Cada hoja tiene un título con el rango, los encabezados
  agrupados como en la pantalla (Ventas normales, Ventas al costo…) y congelados, y las filas en el mismo orden con
  subtotales y totales en negrita. Montos con formato `#,##0.00`, cantidades `#,##0.000`, conteos enteros, días como
  fecha `dd/mm/yyyy`, margen como porcentaje con 2 decimales; un margen o un monto sin movimientos («—» en la
  pantalla) queda como celda vacía. Movimientos por categoría: una columna por mes y el total del rango, bloques
  Ingresos, Gastos, Neto, Transferencias y la línea «Consumo al costo (sin movimiento de dinero)». Consumos: dos
  hojas, Por día y Por proveedor y mes. La búsqueda de proveedor solo filtra la pantalla: el archivo trae todos.
  En la pantalla, botón «Exportar a Excel» en cada pestaña (`app-report-export-button`); el error del servidor
  llega como Blob y se lee para mostrar su mensaje. Pruebas en `ReportExportServiceTest`,
  `EndpointAuthorizationTest`, `report.service.spec.ts` y `report-export-button.component.spec.ts`.

## Históricos (FIN-11)

- **Cuando** se importen las ventas y movimientos históricos **entonces** alimentan estos mismos reportes: cada día
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
| GET | `/api/reports/<reporte>/export?from=&to=` | El mismo reporte en `.xlsx` (`<reporte>`: los cinco de arriba) |

Todas con permiso `REPORTS`. `from` y `to` son días de la tienda (`to` incluido); si faltan, el mes en curso
hasta hoy.

## Día de operación (CAJ-11)

- **Cuando** una venta o un movimiento de la caja del 31/10 se cargó a las 00:20 del 1/11 **entonces** ventas por
  día, ventas por proveedor, totales y movimientos por categoría y mes lo cuentan el 31/10 (y en octubre). Los
  reportes y sus Excel no llevan la marca «cargada el».
- **Cuando** se despliega CAJ-11 **entonces** ningún día cambia, salvo el 28, 29 y 30/10 por la corrección de las
  cajas #146 a #148 (`cash.md`). Los consumos siguen por su fecha (`consumption_date`).
- Ventas por día y por proveedor agrupan y filtran por `sales.operation_date`; movimientos y transferencias por
  `financial_transactions.operation_date`. Pruebas en `SaleRepositoryTest`, `FinancialTransactionRepositoryTest`
  (contra PostgreSQL), `ReportServiceTest` y `CashOperationDayMigrationIntegrationTest`.

## Tareas

- [x] Spec validada con las tablas dinámicas (26-10-2026).
- [x] Permiso `REPORTS` (Administrador), pantalla `/reports` en diferido y menú Finanzas → Reportes.
- [x] Ventas por día: consulta agrupada por día de la tienda y tipo de venta, servicio con subtotales por mes,
  `GET /api/reports/sales-by-day`, pestaña en la pantalla. Pruebas en `ReportServiceTest`,
  `SaleRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest` y `report.service.spec.ts`.
- [x] Ventas por proveedor y mes, con el reparto del descuento general (26-10-2026). Pruebas en
  `ReportServiceTest`, `SaleRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest`,
  `report.service.spec.ts` y `sales-by-supplier-report.component.spec.ts`.
- [x] Movimientos por categoría y mes, con transferencias y la línea Consumo (26-10-2026). Pruebas en
  `ReportServiceTest`, `FinancialTransactionRepositoryTest` y `ConsumptionRepositoryTest` (contra PostgreSQL),
  `EndpointAuthorizationTest`, `report.service.spec.ts` y `transactions-by-category-report.component.spec.ts`.
- [x] Totales de ventas y utilidad por mes (26-10-2026). Pruebas en `ReportServiceTest`,
  `EndpointAuthorizationTest`, `report.service.spec.ts` y `totals-report.component.spec.ts`.
- [x] Consumos al costo por día, mes y proveedor (26-10-2026). Pruebas en `ReportServiceTest`,
  `ConsumptionRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest`, `report.service.spec.ts` y
  `consumptions-report.component.spec.ts`.
- [x] Exportar a Excel los cinco reportes (26-10-2026). Suite completa del backend al cerrar el módulo.
- [x] Tercera revisión (29-10-2026): los cinco `.xlsx` del 01-07 al 29-10 de la base local (con el historial) se
  abrieron y compararon con la respuesta de la pantalla: mismos totales, costo, utilidad, margen, días, neto y
  consumos.
- [ ] Fase 2: comparar dos semanas con las tablas dinámicas.
- [x] Regalos y roturas a la venta al costo en la migración (P-39, 28-10-2026): la comparación de la fase 2 ya no
  los suma aparte como consumos.
- [ ] En la fase 2, revisar que ventas por día del 25-08 y del 21-09 den el Total de la planilla (normales + al
  costo).
