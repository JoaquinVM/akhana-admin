# Deudas y cuentas por cobrar

Detalle del módulo Deudas de la V1 (sección 3.4 de `specs/requirements.md`), con los mismos IDs.
Alcance: DEU-01 a DEU-07. Incluye el pago de un pedido hecho por una persona (origen «persona» de COM-05, P-26),
que Compras dejó pendiente.

Dos lados del mismo módulo:

- **Deudas** (DEU-01 a DEU-04): lo que el negocio debe a personas que pagaron pedidos por él (ej. «Mami pagó»).
- **Cuentas por cobrar** (DEU-05 a DEU-07): lo que la tienda amiga debe por ventas al costo a crédito.

Moneda Bs, montos con escala 2 `HALF_UP`; un monto que redondea a 0,00 se rechaza: «El monto debe ser
estrictamente mayor a 0.00 Bs.» Toda operación que mueve dinero o saldos es atómica (RG-05). Nada se edita: se
anula con motivo (RG-03) y quién y cuándo los pone el servidor (RG-02). La fecha de pagos, devoluciones y cobros
es la del servidor (T-18). Al migrar no hay deudas iniciales (P-20) y las notas sueltas de la planilla se ignoran
(P-28).

## Permiso

Pantalla nueva **Deudas** (`/debts`), permiso `DEBTS`, para Operador y Administrador: las devoluciones y los
cobros se hacen en la caja, como los pagos de pedidos (tabla 2.2). Tiene dos pestañas: Personas (deudas) y
Clientes (por cobrar). Quien entra puede hacer todo en ella, incluidas las anulaciones (P-33).

Lecturas cruzadas: Compras lista las personas activas para el pago por persona y el POS lista los clientes
activos para la venta a crédito, así que esos listados también aceptan `PURCHASES` y `POS`. Las cuentas para
devolver o cobrar se leen con un endpoint propio (`GET /api/debts/accounts`): el operador no entra a Finanzas.

## Saldos

Los saldos no se guardan: se calculan desde los registros activos, como el estado de pago de un pedido (COM-04).

- Deuda con una persona = pagos de pedidos activos hechos por ella − devoluciones activas.
- Saldo por cobrar a un cliente = parte a crédito de sus ventas activas − cobros activos.

Ninguno de los dos puede quedar negativo: toda operación que lo dejaría bajo 0 se rechaza.

## DEU-01 · Personas

Registro de personas a las que el negocio puede deber dinero (`creditors`): nombre, teléfono, descripción y
estado (Activo, Inactivo, Eliminado), como proveedores (COM-01).

- **Cuando** se crea una persona **entonces** el nombre es obligatorio y único sin distinguir mayúsculas,
  excluyendo eliminados: «Ya existe una persona con el nombre '<nombre>'.»
- **Cuando** se edita **entonces** cambian nombre, teléfono y descripción con auditoría de modificación; la
  deuda no se toca.
- **Cuando** se elimina o inactiva una persona con deuda mayor a 0 **entonces** se rechaza: «<nombre> tiene una
  deuda de Bs 150.00; devuélvala antes de darla de baja.»
- **Cuando** se elimina con deuda 0 **entonces** queda Eliminada (baja lógica) y su historial se sigue viendo.
- **Cuando** una persona está inactiva o eliminada **entonces** no aparece para nuevos pagos de pedidos; sus
  pagos ya registrados siguen valiendo.

## DEU-02 · Pago de un pedido hecho por una persona (COM-05, P-26)

En el pago de un pedido (`POST /api/purchases/{id}/payments`) el origen es una cuenta **o** una persona activa:
`purchase_payments` gana `creditor_id` y `account_id` pasa a opcional, con un `CHECK` de que haya exactamente
uno de los dos.

- **Cuando** Mami paga Bs 200,00 de un pedido de Bs 300,00 **entonces** se crea el pago con origen Mami, el
  pedido queda A cuenta con saldo Bs 100,00 (COM-04) y la deuda con Mami sube Bs 200,00.
- **Cuando** paga el saldo completo **entonces** el pedido queda Pagado: el pago por persona cuenta igual que
  uno desde una cuenta.
- **Cuando** se registra el pago por persona **entonces** no se crea movimiento financiero ni cambia ninguna
  cuenta ni la caja: el dinero no salió del negocio. Sale cuando se le devuelve (DEU-03).
- **Cuando** el monto supera el saldo del pedido **entonces** se rechaza con el mismo mensaje que COM-05.
- **Cuando** la persona está inactiva o eliminada **entonces** se rechaza: «La persona '<nombre>' no está
  activa.»
- **Cuando** el pedido tiene pagos de distintos orígenes (Bisa y Mami) **entonces** el detalle del pedido muestra
  cada pago con su origen.
- **Cuando** se registra un pedido **entonces** el formulario permite elegir como origen del pago inicial una
  cuenta o una persona. El pago inicial viaja en el pedido (`PurchaseRequest.initialPayment`, mismo formato que
  un pago) y se guarda en la misma transacción: si se rechaza, no queda el pedido (RG-05).
- **Cuando** el pago trae cuenta y persona, o ninguna **entonces** se rechaza: «Elija una cuenta o una persona
  como origen del pago.» La nota del pago solo se guarda en el movimiento, así que un pago por persona no la
  lleva.
- Bloqueo: pedido, luego persona (T-14). Al registrar un pedido con pago inicial por persona, se bloquea la caja
  (si el envío sale de Caja), luego las personas del pago y del envío y luego la cuenta del envío.

### Envío pagado por una persona

- **Cuando** Mami paga el envío de Bs 25,00 de un pedido **entonces** su deuda sube Bs 25,00, sin movimiento de dinero,
  y el estado de pago del pedido no cambia: el envío no cuenta (COM-04). Se le devuelve como cualquier deuda (DEU-03,
  concepto Pedido).
- **Cuando** se cambia el envío de ese pedido o se anula el pedido **entonces** su envío queda anulado con el motivo y
  la deuda baja, salvo que ya se le haya devuelto: «Ya se devolvió a Mami parte de este envío; anule primero la
  devolución.» (COM-12, COM-09).

### Anulación de un pago por persona (COM-09)

- **Cuando** se anula con motivo un pago de Bs 200,00 de Mami **entonces** el pago queda `ANULADO`, el pedido
  recalcula su estado y la deuda con Mami baja Bs 200,00. No hay movimiento que revertir.
- **Cuando** la anulación dejaría la deuda negativa (ya se le devolvió ese dinero) **entonces** se rechaza:
  «Ya se devolvió a <nombre> parte de este pago; anule primero la devolución.»
- **Cuando** se anula el pedido completo (COM-09) **entonces** también se anulan sus pagos por persona con la
  misma regla: si alguno dejaría una deuda negativa, no se anula nada y responde el mismo mensaje.
- Bloqueo: pedido, caja, persona, cuentas.

## DEU-03 · Devolución a la persona

Registro de lo que el negocio le devuelve a la persona (`creditor_repayments`): persona, monto, cuenta de origen
(Caja con la caja abierta, Bisa u otra cuenta activa), fecha y comentario. Desde el trabajo 062 se paga marcando
deudas (P-65): cada devolución guarda qué pagó en `creditor_repayment_items` (V31), una fila por deuda con su monto:
un pago de pedido (`purchase_payment_id`), un envío (`shipping_charge_id`) o una venta con sus productos (`sale_id`,
la suma de sus líneas en esa venta), exactamente uno. Una deuda está pagada mientras las filas de devoluciones activas
la cubren; lo que falta de ella es su monto menos esas filas. La deuda total se sigue calculando desde los totales.

- **Cuando** se abre el detalle de Mami con deuda **entonces** arriba del historial se listan sus deudas pendientes
  (pagos de pedidos, envíos y ventas de sus productos), de la más antigua a la más nueva, con lo que falta de cada una;
  se marcan una por una (casilla o la fila) o todas, y «A pagar · 2 de 4» muestra la suma de lo marcado.
- **Cuando** se marcan las ventas VTA-000260 y VTA-000357 (Bs 30,00 cada una) y se paga desde Bisa **entonces** se
  crea una devolución con concepto Pago a tercero por Bs 60,00, con una fila por venta, y un gasto (`GASTO_MANUAL`) en
  Bisa con la categoría Pago a tercero, descripción «Pago a tercero a Mami», vinculado a la devolución
  (`financial_transactions.creditor_repayment_id`); baja el saldo de Bisa y la deuda con Mami, y las dos ventas quedan
  pagadas ese día.
- **Cuando** se marcan un pedido (Bs 122,00), un envío (Bs 31,00) y una venta (Bs 27,00) **entonces** se registran en
  la misma operación dos devoluciones con sus gastos: Pago a tercero por Bs 27,00 (la venta) y Devolución por
  Bs 153,00 (pedido y envío, categoría Pedido, P-34, descripción «Devolución a Mami»). Si una falla, no queda ninguna.
  El formulario lo avisa antes de pagar. El Pago a tercero nunca supera lo pendiente de sus ventas: sale solo de ventas
  marcadas.
- **Cuando** no se marca nada **entonces** el botón queda desactivado y el servidor rechaza «Elija las deudas que se
  pagan.» No hay monto libre ni concepto para elegir.
- **Cuando** lo marcado ya no está pendiente (otra pestaña lo pagó, se anuló, es de otra persona o no existe) o el total
  no coincide con el que mostró la pantalla **entonces** se rechaza «Las deudas elegidas cambiaron; vuelva a abrir el
  detalle.» y no se mueve dinero. El servidor calcula el monto con lo que falta de cada deuda, leído después del
  bloqueo de la persona.
- **Cuando** una deuda quedó pagada en parte (solo por datos anteriores a 062) **entonces** se lista con lo que falta
  («Bs 12,00 de Bs 27,00») y marcarla paga ese resto.
- **Cuando** la devolución sale de Caja y supera el efectivo esperado **entonces** el formulario lo avisa sin
  bloquear (CAJ-03).
- **Cuando** se elige Caja y el servidor responde que no hay caja abierta **entonces** debajo de la lista aparece «La caja
  está cerrada. Ábrala y vuelva a elegir Caja.» y se desactiva el botón de guardar; un fallo de la consulta no la muestra ni
  bloquea. Lo mismo al cobrar a un cliente. Las listas dicen «Caja», sin «(caja abierta)» (CAJ-03).
- **Cuando** se devuelve desde Caja **entonces** exige caja abierta («Los movimientos de la cuenta Caja
  requieren una caja abierta.»), el gasto se vincula a ella y baja el efectivo esperado (CAJ-03,
  `ExpectedCashCalculator`, como un gasto de Caja).
- **Cuando** lo marcado supera la deuda (solo con datos anteriores a 062 que no se marcaron del todo) **entonces** se
  rechaza: «La devolución supera la deuda con <nombre> (Bs 50.00).»
- **Cuando** la persona está inactiva **entonces** se acepta: hay que poder devolverle lo que se le debe.
- **Cuando** el gasto de la devolución se mira desde Finanzas o el POS **entonces** no se edita ni anula allí:
  «Los movimientos de una deuda se gestionan desde Deudas.»
- Bloqueo: caja, persona, cuentas (T-14, el mismo orden que las ventas desde el 29-10-2026).
- El vínculo entre devolución y gasto vive solo en `financial_transactions.creditor_repayment_id` (índice único:
  un gasto por devolución), sin columna inversa, para no tener una referencia circular.

### Anulación de una devolución

- **Cuando** se anula con motivo una devolución **entonces** queda `ANULADO` (quién, cuándo, motivo), su gasto
  queda anulado con el mismo motivo, la cuenta recupera el monto y la deuda vuelve a subir; si era de Caja con
  la caja abierta, el efectivo esperado sube. Lo que pagó vuelve a quedar pendiente y se puede volver a marcar; sus
  filas no se borran y la devolución anulada sigue diciendo qué había pagado. El aviso lo dice: «Vuelven a quedar
  pendientes: PED-000015, PED-000014.»

### Lo ya pagado no se anula (P-65)

- **Cuando** se anula un pago por persona, un pedido completo, un cambio del envío pagado por la persona (monto u
  origen) o una venta con sus productos (POS-07) y eso ya figura como pagado por una devolución activa **entonces** se
  rechaza con el mensaje de siempre («Ya se devolvió a Mami parte de este pago / envío / esta venta; anule primero la
  devolución.»), aunque la deuda no quede negativa. Anulada la devolución, se puede.

### Devoluciones anteriores a 062 (V32, P-66)

- **Cuando** corre la migración **entonces** cada devolución activa cubre las deudas activas más antiguas de su
  concepto (Pedido: pagos y envíos; Pago a tercero: ventas), por día de operación y fecha real, y lo que sobre de ella,
  las más antiguas de cualquier tipo. Solo toca personas sin ninguna fila, así que correrla otra vez no cambia nada.
- En producción (12-11-2026) las cinco devoluciones de Mami cubrían deudas enteras: Pago a tercero del 03/11 (Bs 326)
  HIS-000085, HIS-000086, HIS-000088, VTA-000035 y VTA-000064; Pago a tercero del 05/11 (Bs 20) VTA-000126; Devolución
  del 03/11 (Bs 122) PED-H00487; del 05/11 (Bs 3.242,45) PED-000015, PED-000014, PED-000019 y PED-000018; del 12/11
  (Bs 1.280) PED-H00118, PED-000032 y PED-000024. Quedan pendientes VTA-000260, VTA-000357, VTA-000506 y VTA-000519
  (Bs 102 = la deuda).
- **Cuando** la devolución se hizo en una caja ya cerrada **entonces** se rechaza: «La devolución se hizo en una
  caja cerrada; no se puede anular.» (CAJ-06, T-16).
- **Cuando** falta el motivo **entonces** «El motivo de anulación es obligatorio.»; si ya está anulada, «La
  devolución ya está anulada.»

## DEU-04 · Saldo e historial por persona

- **Cuando** se abre la pestaña Personas **entonces** se ve cada persona con su deuda y el total adeudado; filtra
  por estado y texto.
- **Cuando** se abre el detalle de Mami **entonces** se ve su historial en orden de fecha: los pagos de pedidos
  (fecha, pedido `PED-000001`, proveedor, monto), los envíos que pagó («Pagó el envío del pedido PED-000001», con el
  proveedor), las ventas de sus productos y las devoluciones (fecha, cuenta, monto, comentario), cada uno
  con el saldo después del movimiento. Los anulados se ven marcados, con su motivo, y no cuentan en el saldo.
- **Cuando** una deuda está pagada **entonces** se ve «Pagada el 05/11» (el día de la devolución que la terminó de
  pagar); si no, «Pendiente», o «Falta Bs 12,00» si se pagó en parte. Cada devolución dice qué pagó: «Pagó: PED-000015,
  PED-000014», en el orden de las deudas (`pendingAmount`, `paidOn` y `paidItems` de cada entrada).
- **Cuando** Mami pagó Bs 200,00 y Bs 100,00 y se le devolvieron Bs 150,00 **entonces** su deuda es Bs 150,00.
- **Cuando** una venta de un producto de Mami (POS-13) o una devolución desde Caja se cargó pasada la medianoche en
  la caja del 31/10 **entonces** el historial la ordena y la muestra en el 31/10, con la marca «Día 31/10 · cargada
  el 01/11 00:20»; el saldo no cambia (CAJ-11). Una devolución desde Bisa queda en el día de su registro.
- Desde el detalle se pagan las deudas marcadas (DEU-03) y se abre el pedido de cada pago o envío.

## DEU-05 · Venta al costo a crédito

Depende de la venta al costo (CON-02, módulo Consumos): se construye junto con ella o después. Solo la venta al
costo admite crédito y solo a un cliente registrado (P-12: no hay crédito a otros clientes en la V1).

Clientes (`customers`): nombre, teléfono, descripción y estado, con las mismas reglas que las personas
(DEU-01): nombre único sin distinguir mayúsculas, baja lógica y no se da de baja con saldo por cobrar mayor a 0
(«<nombre> tiene un saldo por cobrar de Bs 80.00.»). En la V1 el único cliente es la tienda amiga.

La venta guarda el cliente (`sales.customer_id`) y la parte a crédito (`sales.credit_amount`, por defecto 0,
`CHECK >= 0` y no mayor al total). Lo cobrado en el momento + crédito = total de la venta.

- **Cuando** una venta al costo de Bs 120,00 a la tienda amiga se paga Bs 40,00 en efectivo y Bs 80,00 a
  crédito **entonces** se crea la venta con `credit_amount = 80.00`, solo los Bs 40,00 entran a Caja (FIN-07) y
  el saldo por cobrar de la tienda amiga sube Bs 80,00.
- **Cuando** toda la venta queda a crédito **entonces** no se crea ningún movimiento financiero y la venta queda
  con método de pago Crédito (`payment_method = 'CREDITO'`); si una parte se paga, el método es el de esa parte
  (efectivo, QR o mixto).
- **Cuando** la venta es normal (no al costo) y trae crédito **entonces** se rechaza: «Solo una venta al costo
  puede quedar a crédito.»
- **Cuando** hay crédito sin cliente o con un cliente inactivo **entonces** se rechaza: «Elija un cliente activo
  para la venta a crédito.»
- **Cuando** el crédito supera el total o es negativo **entonces** se rechaza.
- La venta a crédito exige caja abierta como toda venta (CAJ-01).

### Anulación de una venta a crédito (POS-07)

- **Cuando** se anula con motivo una venta con crédito, con su caja abierta **entonces** se anulan sus ingresos
  como hoy y el saldo por cobrar baja en su crédito.
- **Cuando** la anulación dejaría el saldo por cobrar negativo (ya se cobró) **entonces** se rechaza: «Ya se
  cobró parte de esta venta a <cliente>; anule primero el cobro.»

## DEU-06 · Cobros

Registro de lo que paga el cliente después (`customer_collections`): cliente, monto, cuenta (Caja con la caja
abierta, o la cuenta QR), fecha y comentario.

- **Cuando** la tienda amiga paga Bs 50,00 en efectivo **entonces** se crea el cobro y un ingreso en Caja
  vinculado a la caja abierta y al cobro (`financial_transactions.customer_collection_id`), con la categoría
  Venta (P-34), descripción «Cobro a <cliente>»; sube el efectivo esperado y baja el saldo por cobrar.
- **Cuando** paga por QR **entonces** el ingreso entra a la cuenta QR (FIN-03).
- **Cuando** se elige otra cuenta **entonces** se rechaza: «Los cobros entran a Caja o a la cuenta QR.»
- **Cuando** no hay caja abierta y el cobro es a Caja **entonces** se rechaza: «Los movimientos de la cuenta
  Caja requieren una caja abierta.»
- **Cuando** el monto supera el saldo por cobrar **entonces** se rechaza: «El cobro supera el saldo por cobrar de
  <cliente> (Bs 30.00).»
- **Cuando** el ingreso del cobro se mira desde Finanzas o el POS **entonces** no se edita ni anula allí: «Los
  movimientos de una deuda se gestionan desde Deudas.»
- El cobro a Caja es un ingreso de caja de CAJ-03: la cuenta Caja lo acepta (amplía FIN-02) y
  `ExpectedCashCalculator` lo suma.
- Bloqueo: caja, cliente, cuentas (T-14, el mismo orden que las ventas desde el 29-10-2026).

### Anulación de un cobro

- **Cuando** se anula con motivo un cobro **entonces** queda `ANULADO`, su ingreso queda anulado, la cuenta baja
  el monto y el saldo por cobrar vuelve a subir; si era de Caja con la caja abierta, el efectivo esperado baja.
- **Cuando** el cobro se hizo en una caja ya cerrada **entonces** se rechaza: «El cobro se hizo en una caja
  cerrada; no se puede anular.»
- Motivo obligatorio y doble anulación con los mismos mensajes que DEU-03.

## DEU-07 · Saldos por cobrar por cliente

- **Cuando** se abre la pestaña Clientes **entonces** se ve cada cliente con su saldo por cobrar y el total.
- **Cuando** se abre el detalle de la tienda amiga **entonces** se ve su historial en orden de fecha: ventas al
  costo (fecha, número `VTA-000123`, total, parte a crédito) y cobros (fecha, cuenta, monto, comentario), con el
  saldo después de cada uno. Los anulados se ven marcados, con su motivo, y no cuentan.
- **Cuando** una venta al costo con cliente se pagó toda en el momento **entonces** aparece en su historial como
  «pagada» (total, sin parte a crédito) y no cambia el saldo (P-49, 28-10-2026).
- **Cuando** una venta al costo a crédito o un cobro en Caja se cargó pasada la medianoche en la caja del 31/10
  **entonces** el historial lo ordena y lo muestra en el 31/10 con la marca de CAJ-11; el saldo no cambia. Un cobro
  por QR queda en el día de su registro.
- Desde el detalle se registra un cobro (DEU-06) y se abre el detalle de cada venta (POS-08).

## Categorías de sistema

Decidido en P-34: para que los reportes por categoría (FIN-10) coincidan con la planilla, la devolución a una
persona es un gasto con la categoría Pedido (es el pago diferido de un pedido) y el cobro un ingreso con la
categoría Venta. No se crean categorías nuevas: Pedido ya es de sistema (`PURCHASE_PAYMENT`) y Venta ya existe
como fila de sistema (T-17). Los reportes distinguen devoluciones y cobros por su vínculo
(`creditor_repayment_id`, `customer_collection_id`), no por la categoría.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/creditors?status=&search=` | Personas con su deuda (DEU-01, DEU-04); también `PURCHASES` |
| POST / PUT / DELETE | `/api/creditors[/{id}]` | Alta, edición y baja lógica de personas (DEU-01) |
| GET | `/api/creditors/{id}/history` | Pagos, envíos, ventas y devoluciones con saldo, lo que falta de cada deuda y qué pagó cada devolución (DEU-04) |
| POST | `/api/creditors/{id}/repayments` | Pagar las deudas marcadas: `accountId`, `amount` (el total que mostró la pantalla), `comment`, `items` (`type` PAYMENT, SHIPPING o SALE e `id`) (DEU-03) |
| POST | `/api/creditors/{id}/repayments/{repaymentId}/void` | Anular devolución (DEU-03) |
| GET | `/api/customers?status=&search=` | Clientes con su saldo (DEU-07); también `POS` |
| POST / PUT / DELETE | `/api/customers[/{id}]` | Alta, edición y baja lógica de clientes (DEU-05) |
| GET | `/api/customers/{id}/history` | Ventas al costo (a crédito o pagadas) y cobros con saldo (DEU-07, P-49) |
| POST | `/api/customers/{id}/collections` | Registrar cobro (DEU-06) |
| POST | `/api/customers/{id}/collections/{collectionId}/void` | Anular cobro (DEU-06) |
| GET | `/api/debts/accounts` | Cuentas activas para devolver o cobrar |
| POST | `/api/purchases/{id}/payments` | Ya existe: acepta `creditorId` en lugar de `accountId` (DEU-02) |

## Tareas

Deudas con personas (se puede construir ya):

- [x] Migración `V10__creditors.sql`: `creditors`, `creditor_repayments`, `purchase_payments.creditor_id` con
  `account_id` opcional y `CHECK` de un solo origen y `financial_transactions.creditor_repayment_id`.
- [x] Permiso `DEBTS` (Operador y Administrador) y lectura de personas con `PURCHASES`.
- [x] `CreditorService`: ABM, deuda calculada, historial, devolución y su anulación.
- [x] `PurchaseService`: pago por persona y anulación de pago y de pedido con pagos por persona.
- [x] Finanzas y POS rechazan editar o anular movimientos de deudas.
- [x] Pruebas: `CreditorServiceTest`, `PurchaseServiceTest` (pago por persona), `CreditorRepositoryTest`
  (contra PostgreSQL), `EndpointAuthorizationTest`.
- [x] Frontend: `core/debt`, `pages/debts` (pestaña Personas con detalle e historial, devolución y anulación),
  origen persona en el pago y en el formulario de pedidos, menú Deudas.
- [x] Envío pagado por una persona: `purchase_shipping_charges` suma a la deuda y al historial (`SHIPPING`). Pruebas
  en `ShippingPaidByPersonIntegrationTest` y `creditor-detail-modal.component.spec.ts`.
- [x] Probar P-48 en la app (28-10-2026, rama `feat/app-checks`): Mami con Bs 30,00 de sus ventas sin pagar y el
  pedido PED-000071 (Bs 17,00) pagado por ella. «Devolver» propuso «Pago a tercero» por Bs 30,00; registrado desde
  Caja, propuso «Pedido» por Bs 17,00; registrado desde Bisa, la deuda quedó en 0. Sin hallazgos. Reemplazado por
  el pago marcando deudas (062).
- [x] Pago marcando deudas (062, P-65, P-66): `V31__creditor_repayment_items.sql`, `V32__creditor_repayment_items_backfill.sql`,
  `CreditorService` (pago por deudas marcadas y lo que falta de cada una), bloqueo de anular lo pagado en
  `PurchaseService` y `SaleService`, `creditor-repayment-form` en el detalle. Pruebas: `CreditorServiceTest`,
  `PurchaseServiceTest`, `SaleServiceFreeLineTest`, `CreditorRepositoryTest`, `ShippingPaidByPersonIntegrationTest`,
  `CreditorRepaymentItemsMigrationIntegrationTest`, `creditor-repayment-form.component.spec.ts`,
  `creditor-detail-modal.component.spec.ts`.

Cuentas por cobrar (junto con o después de CON-02):

- [x] Migración `V12__at_cost_sales_receivables.sql`: `customers`, `customer_collections`, `sales.customer_id`,
  `sales.credit_amount`, `CREDITO` en el `CHECK` de `sales.payment_method` y
  `financial_transactions.customer_collection_id`.
- [x] `SaleService`: crédito en la venta al costo y regla de anulación; `ExpectedCashCalculator` suma los cobros
  a Caja (ingresos de Caja, que solo vienen de cobros).
- [x] `CustomerService`: ABM, saldo calculado, historial, cobro y su anulación; Finanzas y POS rechazan editar o
  anular el ingreso de un cobro.
- [x] Pruebas: `CustomerServiceTest`, `SaleServiceTest` (crédito), `CustomerRepositoryTest` y
  `FinancialTransactionRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest`.
- [x] Frontend: pestaña Clientes con detalle e historial, cobro (solo Caja o la cuenta QR) y anulación; crédito y
  cliente en el pago de la venta al costo del POS (26-10-2026). `PaymentAccountResponse` suma `qrAccount`.
- [x] Probar P-49 en la app (28-10-2026, rama `feat/app-checks`): VTA-000136 a Tía Neida pagada en efectivo aparece
  en su historial como «pagada» y su saldo sigue en Bs 17,00 (DEU-07).


## Notas de implementación

Detalle de cómo se construyó cada requerimiento, movido desde `requirements.md` el 29-10-2026 sin cambios.

- **DEU-01** Registro de personas a las que el negocio puede deber dinero (ej. Mami): `creditors`, con nombre único sin distinguir mayúsculas, teléfono, descripción y baja lógica; no se da de baja con deuda mayor a 0. Hecha el 25-10-2026 (migración `V10__creditors.sql`, índice único `LOWER(name)` sin eliminados). Pantalla Deudas (`/debts`, en el menú Compras, carga diferida), pestaña Personas; Compras lee las personas activas con `PURCHASES`.
- **CAJ-11** (31-10-2026) Pagos por persona, devoluciones y cobros guardan `operation_date`: desde Caja, el día de la caja abierta (el mismo en la devolución o el cobro y en su movimiento); desde otra cuenta o sin movimiento, el día de su registro. Los historiales de personas y clientes ordenan por día de operación, luego fecha real y alta, y cada entrada trae `operationDate` (la fecha real sigue en `date`); las ventas de Mami y las ventas al costo toman el día de su venta. Pruebas en `CreditorServiceTest`, `CustomerServiceTest`, `creditor-detail-modal.component.spec.ts` y `customer-detail-modal.component.spec.ts`. Detalle en `cash.md`.
