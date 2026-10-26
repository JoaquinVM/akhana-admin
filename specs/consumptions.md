# Consumos y ventas al costo

Detalle del módulo Consumos de la V1 (sección 3.5 de `specs/requirements.md`), con los mismos IDs.
Alcance: CON-01 a CON-04, más la venta al costo a crédito a la tienda amiga (DEU-05), que se construye junto con
la venta al costo (CON-02). Los clientes, los cobros y los saldos por cobrar (DEU-05 a DEU-07) están en
`specs/debts.md`; aquí solo se repite lo que toca a la venta.

Dos cosas distintas que hoy la planilla mezcla:

- **Consumo** (CON-01): productos que se usan o comen en la tienda. No entra ni sale dinero.
- **Venta al costo** (CON-02): una venta del POS en la que se cobra el costo, sin utilidad. Entra dinero (o queda
  a crédito).

Moneda Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales y las reglas de CAT-08 (un producto
por unidad solo acepta enteros, con el mismo mensaje que en ventas y compras). Nada se edita: se anula con motivo
(RG-03) y quién y cuándo los pone el servidor (RG-02). Las preguntas abiertas de este módulo son P-35 a P-40
(sección 5.1 de `specs/requirements.md`); los escenarios usan el supuesto de cada una.

## Lo que muestra la planilla (validación del 25-10-2026)

Revisadas las hojas Consumos, HOJITA, Ventas, Transacc. y Productos de *Akhana Recaudaciones*.

| Caso real | Qué hace hoy la planilla | Qué hará el sistema |
| --- | --- | --- |
| 12-10: 1 Roquin y 1 Leche Impostor consumidos por Ali (hoja Consumos, Bs 4 cada uno) | Anota el precio de venta (Roquin: costo Bs 2,73, precio Bs 4). Sin movimiento de dinero desde mediados de julio. | Consumo valorado al costo: Roquin Bs 2,73 (P-27, P-35). No mueve dinero. |
| 4-05 a 28-07: «Roquin −4», «Frambombones Abril −31» como gasto Consumo de Caja (Transacc., 61 filas) | El producto se cobraba como venta y luego se sacaba el mismo monto de Caja como gasto. | Ya no: el consumo no pasa por la venta ni por Caja. Lo de julio se migra tal cual como movimiento histórico (FIN-11, P-40). |
| 17 a 28-07: «Agua alcalina 2L» en la hoja Consumos y como gasto Consumo en Transacc. | El mismo consumo está en las dos hojas. | Se migra solo el movimiento de Transacc. (P-40), para no contarlo dos veces. |
| 12 a 22-10: «Almuerzo» Bs 28 en la hoja Consumos | El almuerzo también está como gasto Comida de Caja («Almuerzo Ali −28»). | No es un consumo: sigue como gasto de caja Comida (P-37). |
| 25-09 y 28-09: «Aceite orgánico Borges», «Brownie vegano» con comentario «Mami precio costo» (hoja Ventas, utilidad 0) | Venta en el día con precio = costo, mezclada con las ventas normales. | Venta al costo pagada en el momento, separada en los reportes (CON-02, CON-03, P-39). |
| 21-09: «Manteca corporal», «Mono grande» con «Regalo Ali»; 25-08 «Mermeladas Frutilla» con «Mami rompió» (utilidad 0) | Se anotan como venta al costo. | Regalo y rotura son consumos con comentario (P-39); la rotura será merma en la V2 (INV-02). |
| 22-10: «Fit Mit Tia Neida −579» Otro gasto de Caja; 23-10: «Fit Mit Tia Neida 579» Otro Ingreso a Caja | Akhana paga a un proveedor mercadería para Tía Neida y ella la devuelve después. Pasa unas 10 veces desde junio (Green Leaf 700, Granel 360, Aceite de almendras 161…). | Supuesto P-38: Tía Neida es la tienda amiga; la mercadería entra como pedido de Akhana y sale como venta al costo a crédito; su pago es un cobro (DEU-06). |
| 14-10: «Citrato de magnesio (Nerdia)» × 2 a Bs 120 con «tia neida» (costo Bs 115) | Venta normal con descuento, no al costo. | Venta normal; no hay crédito fuera de la venta al costo (P-12). |

## Permiso

- Pantalla nueva **Consumos** (`/consumptions`, en el menú POS), permiso `CONSUMPTIONS`, para Operador y
  Administrador (tabla 2.2, P-19). Quien entra puede registrar y anular (P-33). Desde Consumos se buscan
  productos, así que la lectura de productos también acepta `CONSUMPTIONS`.
- La venta al costo se hace en el POS (permiso `POS`); el POS ya lee clientes activos (`specs/debts.md`).
- Los reportes (CON-03) son de Finanzas y reportes: solo Administrador.

## CON-01 · Consumo interno

Un consumo (`consumptions`) tiene número correlativo `CON-000001` (secuencia, índice único), fecha, comentario
opcional y líneas (`consumption_items`): producto, cantidad, copia de nombre, código y costo unitario del producto
en ese momento (`buy_price`), y subtotal = cantidad × costo. Total = suma de líneas.

- **Cuando** se registra el consumo de 2 Roquin (costo Bs 2,73) y 1 Bombitas Proteína (costo Bs 10,00)
  **entonces** se crea `CON-000001` con líneas de Bs 5,46 y Bs 10,00, total Bs 15,46, y no se crea ningún
  movimiento financiero ni cambia el efectivo esperado de la caja.
- **Cuando** se consumen 0,100 kg de un fruto seco con costo Bs 60,00 por kilo **entonces** la línea vale
  Bs 6,00.
- **Cuando** después cambia el costo del Roquin (COM-03 o catálogo) **entonces** el consumo ya registrado sigue
  valiendo Bs 2,73 por unidad (copia del costo, como POS-05).
- **Cuando** se registra un consumo **entonces** no exige caja abierta: no mueve dinero.
- **Cuando** no se indica fecha **entonces** se toma hoy (hora de Bolivia); se puede elegir una fecha anterior
  para anotar consumos atrasados, pero no una futura: «La fecha del consumo no puede ser futura.»
- **Cuando** el consumo no tiene líneas **entonces** se rechaza: «El consumo debe tener al menos una línea.»
- **Cuando** el mismo producto se agrega dos veces **entonces** se suma la cantidad en una línea, como en el POS
  (POS-01).
- **Cuando** el producto está eliminado **entonces** se rechaza: «El producto '<nombre>' no existe o fue
  eliminado.»; un producto inactivo se acepta (puede quedar en la tienda aunque ya no se venda).
- **Cuando** el producto tiene costo 0 **entonces** se acepta con advertencia en pantalla («Este producto no
  tiene costo registrado; el consumo vale Bs 0,00.»).
- **Cuando** se indica quién consumió (supuesto P-36) **entonces** se guarda como texto libre opcional
  («Para quién», ej. Ali) y se ve en el listado.
- **Cuando** es un regalo o una rotura (supuesto P-39) **entonces** se registra como consumo con el comentario
  («Regalo Ali», «Mami rompió»).
- **Cuando** se quiere registrar un almuerzo **entonces** no se busca en Consumos: el almuerzo no es un producto
  del catálogo y sigue siendo un gasto de caja Comida en el POS (supuesto P-37).

### Listado y anulación

- **Cuando** se abre Consumos **entonces** se ven los consumos del más reciente al más antiguo con número, fecha,
  para quién, total al costo y estado; filtra por rango de fechas, producto, estado y texto. Se abre el detalle
  con sus líneas y la auditoría.
- **Cuando** se anula con motivo un consumo **entonces** queda `ANULADO` (quién, cuándo, motivo) y deja de contar
  en los reportes. No hay dinero que revertir, así que no depende de la caja.
- **Cuando** falta el motivo **entonces** «El motivo de anulación es obligatorio.»; si ya está anulado, «El consumo
  ya está anulado.»

## CON-02 · Venta al costo

Una venta del POS marcada como tipo **Al costo** (`sales.sale_type`: `NORMAL` por defecto, o `AL_COSTO`). Es
toda la venta o nada: el POS tiene un interruptor «Venta al costo» para el carrito completo. Usa la caja, la
numeración `VTA-000001` (POS-06), los pagos y la anulación (POS-07) de cualquier venta.

- **Cuando** se activa «Venta al costo» con 1 Aceite de coco 500 ml (costo Bs 104,00, precio Bs 125,00)
  **entonces** la línea se cobra Bs 104,00: el servidor pone precio unitario = costo unitario = `buy_price` del
  producto, ignorando el precio que envíe el cliente, y la utilidad de la venta es 0.
- **Cuando** la venta al costo trae descuento por unidad o descuento general **entonces** se rechaza: «Una venta
  al costo no admite descuentos.»
- **Cuando** un producto del carrito tiene costo 0 **entonces** se rechaza: «'<producto>' no tiene costo
  registrado; no se puede vender al costo.»
- **Cuando** se paga en el momento (efectivo, QR o mixto) **entonces** entra a Caja o a la cuenta QR como
  cualquier venta (FIN-07), con la categoría Venta, y el efectivo suma al esperado (CAJ-03).
- **Cuando** no hay caja abierta **entonces** se rechaza como toda venta (CAJ-01).
- **Cuando** la venta al costo es a cualquier persona (ej. Mami) pagada en el momento **entonces** se acepta sin
  cliente (supuesto P-39); el cliente solo es obligatorio si hay crédito.
- **Cuando** se anula con motivo con su caja abierta **entonces** se anulan sus ingresos como hoy (POS-07).
- **Cuando** se mira en el historial o en el detalle (POS-08, POS-09) **entonces** se ve marcada «Al costo» y el
  historial filtra por tipo de venta.

### Venta al costo a crédito a la tienda amiga (DEU-05)

Reglas de clientes, saldos y cobros en `specs/debts.md`. La venta guarda el cliente (`sales.customer_id`) y la
parte a crédito (`sales.credit_amount`, por defecto 0). Cobrado en el momento + crédito = total.

- **Cuando** una venta al costo de Bs 579,00 (la mercadería de Fit Mit) a Tía Neida queda toda a crédito
  **entonces** la venta tiene `credit_amount = 579.00` y método `CREDITO`, no crea ningún movimiento financiero
  ni cambia el efectivo esperado, y el saldo por cobrar de Tía Neida sube Bs 579,00.
- **Cuando** al día siguiente Tía Neida paga Bs 579,00 en efectivo **entonces** se registra como cobro en Deudas
  (DEU-06): ingreso a Caja con la categoría Venta, sube el efectivo esperado y el saldo por cobrar queda en 0.
- **Cuando** una venta al costo de Bs 120,00 se paga Bs 40,00 por QR y Bs 80,00 a crédito **entonces** solo los
  Bs 40,00 entran a la cuenta QR, el método es QR y el saldo por cobrar sube Bs 80,00.
- **Cuando** una venta normal trae crédito **entonces** se rechaza: «Solo una venta al costo puede quedar a
  crédito.»
- **Cuando** hay crédito sin cliente o con un cliente inactivo **entonces** se rechaza: «Elija un cliente activo
  para la venta a crédito.»
- **Cuando** el crédito es negativo o supera el total **entonces** se rechaza: «El crédito debe estar entre
  Bs 0,00 y el total de la venta.»; cobrado + crédito distinto del total, el mismo rechazo que hoy para un pago
  incompleto.
- **Cuando** se anula una venta a crédito ya cobrada en parte **entonces** se rechaza: «Ya se cobró parte de esta
  venta a <cliente>; anule primero el cobro.»
- Bloqueo: caja, cliente, cuentas (T-14). El cliente se bloquea para que el saldo por cobrar no cambie a la vez.

### Encargos de la tienda amiga (supuesto P-38)

Hoy Akhana paga a un proveedor mercadería para Tía Neida («Fit Mit Tia Neida −579», Otro gasto) y ella la
devuelve («Fit Mit Tia Neida 579», Otro Ingreso). En el sistema eso son tres registros que ya existen:

1. Pedido de Akhana al proveedor (COM-02) con sus líneas, pagado desde Caja o Bisa (COM-05).
2. Venta al costo a crédito a Tía Neida con esos productos.
3. Cobro cuando ella paga (DEU-06).

- **Cuando** se sigue ese camino **entonces** los reportes por categoría muestran Pedido −579 y Venta +579 donde
  la planilla muestra Otro gasto −579 y Otro Ingreso +579: para la comparación de la fase 2 hay que mirar el
  total, no la categoría. Queda anotado en P-38.

## CON-03 · Reportes separados

Se construyen con los reportes (FIN-10); aquí solo se fija qué separan.

- **Cuando** se ve el reporte de ventas por día o por proveedor **entonces** muestra en columnas aparte las
  ventas normales (total, costo, utilidad) y las ventas al costo (total = costo, utilidad 0); el total general
  suma ambas.
- **Cuando** se ve la utilidad por proveedor y mes **entonces** las ventas al costo no bajan el margen porcentual
  de las normales: el porcentaje se calcula solo sobre las normales.
- **Cuando** se ven los consumos **entonces** hay un reporte de consumos al costo por día, mes y proveedor, y en
  el resumen por categoría aparece la línea Consumo calculada desde los consumos activos, marcada «sin movimiento
  de dinero»: no cambia el saldo de ninguna cuenta (FIN-08).
- **Cuando** se calcula la utilidad del mes **entonces** se muestra la utilidad de las ventas y, aparte, la
  utilidad menos los consumos del mes.
- **Cuando** una venta al costo, un consumo o un cobro está anulado **entonces** no cuenta.
- **Cuando** se comparan los reportes con la planilla en la fase 2 **entonces** la línea Consumo no coincide en
  monto (la planilla usa el precio de venta, el sistema el costo, P-35); se comparan cantidades por producto.

## CON-04 · Salidas de stock (V2)

- **Cuando** exista el inventario (INV-01) **entonces** cada línea de un consumo y de una venta al costo activos
  genera una salida de stock del tipo correspondiente (INV-02), y su anulación un reingreso. Como la V1 ya guarda
  producto y cantidad por línea, no hay que recapturar nada.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/consumptions?from=&to=&productId=&status=&search=` | Listado de consumos (CON-01) |
| GET | `/api/consumptions/{id}` | Detalle con líneas y auditoría |
| POST | `/api/consumptions` | Registrar consumo (CON-01) |
| POST | `/api/consumptions/{id}/void` | Anular consumo con motivo |
| POST | `/api/sales` | Ya existe: acepta `saleType`, `customerId` y `creditAmount` (CON-02, DEU-05) |
| GET | `/api/sales?saleType=` | Historial filtrado por tipo (POS-09) |

## Tareas

Consumos (se puede construir ya):

- [x] Migración: `consumptions` (número por secuencia `consumption_number_seq`, fecha, para quién, comentario,
  total, estado, auditoría de alta y de anulación) y `consumption_items` (cantidad `NUMERIC(12,3)`, copia de
  nombre, código y costo, subtotal); `CHECK` de estado y de cantidad > 0.
- [x] Permiso `CONSUMPTIONS` (Operador y Administrador) y lectura de productos con ese permiso.
- [x] `ConsumptionService`: alta con costo del producto y reglas de CAT-08, listado con filtros, detalle y
  anulación.
- [x] Pruebas: `ConsumptionServiceTest` (costo copiado, cantidades, fecha futura, anulación, no mueve dinero),
  `ConsumptionRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest`.
- [x] Frontend: `core/consumption`, `pages/consumptions` (listado, formulario con buscador de productos y
  presentaciones por peso, detalle, anulación), menú POS → Consumos.

Hecho el 25-10-2026: migración `V11__consumptions.sql` (estados `REGISTRADO` y `ANULADO`; «Para quién» en
`consumed_for`). El texto del listado busca en número, para quién, comentario y nombre de producto de las líneas.
La ruta `/consumptions` se carga en diferido porque el bundle inicial del frontend está en el límite de 1 MB.

Venta al costo y crédito (junto con las cuentas por cobrar de `specs/debts.md`):

- [x] Migración: `sales.sale_type` (`NORMAL`, `AL_COSTO`, por defecto `NORMAL`), más lo de cuentas por cobrar de
  `specs/debts.md` (`customers`, `customer_collections`, `sales.customer_id`, `sales.credit_amount`, `CREDITO`,
  `financial_transactions.customer_collection_id`).
- [x] `SaleService`: precio = costo en la venta al costo, sin descuentos, costo 0 rechazado, crédito con cliente
  activo, bloqueo caja → cliente → cuentas y regla de anulación con cobros.
- [x] `CustomerService` y cobros (DEU-05 a DEU-07, tareas de `specs/debts.md`).
- [x] Pruebas: `SaleServiceTest` (al costo, crédito total y parcial, rechazos, anulación), `CustomerServiceTest`,
  `CustomerRepositoryTest` y `FinancialTransactionRepositoryTest` (contra PostgreSQL: cobros a Caja suman al
  esperado; la venta a crédito no, porque solo cuenta lo cobrado), `EndpointAuthorizationTest`.

Backend hecho el 25-10-2026: migración `V12__at_cost_sales_receivables.sql`. Si toda la venta queda a crédito el
servidor guarda el método `CREDITO` aunque el cliente envíe otro; enviar `CREDITO` con una parte pagada se rechaza
(«El pago no cubre el total: lo cobrado más el crédito debe ser igual al total de la venta.»). En una venta normal
se ignora `customerId`. El filtro `GET /api/sales?saleType=` queda con el historial (POS-09).
- [ ] Frontend: interruptor «Venta al costo» en el POS con cliente y crédito en el pago; marca «Al costo» en el
  detalle y filtro por tipo en el historial; pestaña Clientes en Deudas. El cobro vive en el modal de
  `pages/pos/register-sale` (no en `pos.component.html`): antes, dividirlo en un componente propio
  (`pages/pos/components/checkout-modal`).

Reportes (con FIN-10):

- [ ] Ventas normales y al costo en columnas aparte; reporte de consumos al costo; línea Consumo «sin movimiento
  de dinero» en el resumen por categoría.
