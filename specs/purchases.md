# Compras (pedidos)

Detalle del módulo Compras de la V1 (sección 3.3 de `specs/requirements.md`), con los mismos IDs.
Alcance: COM-01 a COM-09. El pago hecho por una persona (origen «persona» de COM-05, que nace con DEU-01/DEU-02)
queda para el módulo Deudas.

Moneda Bs, montos con escala 2 `HALF_UP`; cantidades con hasta 3 decimales (escala 3), como las ventas (CAT-08).
Toda operación que mueve dinero es atómica (RG-05) y bloquea en orden: pedido, caja, persona, cuentas (T-14). Al registrar un pedido, la caja se bloquea primero si el envío
o el primer pago salen de Caja, y las cuentas del envío y del pago se bloquean juntas por id. Un pago desde Caja que
supera el efectivo esperado se avisa sin bloquear (CAJ-03).

## Permiso

Pantalla nueva **Compras** (`/purchases`), permiso `PURCHASES`, para Operador y Administrador (tabla 2.2).
Desde Compras se leen proveedores, productos y categorías de producto, y se crean productos (COM-03), así que
esos endpoints también aceptan `PURCHASES`. Las cuentas para pagar se leen con un endpoint propio de Compras
(`GET /api/purchases/payment-accounts`): el operador no entra a Finanzas.

## COM-01 · Proveedores

Ya existe (ABM con baja lógica, código y nombre únicos sin distinguir mayúsculas). Sin cambios.

- **Cuando** se registra un pedido con un proveedor eliminado o inactivo **entonces** se rechaza: «El proveedor
  '<nombre>' no está activo.»

## COM-02 · Pedido

Un pedido tiene número correlativo `PED-000001` (secuencia `purchase_number_seq`, índice único), proveedor,
fecha del pedido, líneas, costo de envío, total y comentario. Auditoría de alta en el servidor.

- **Cuando** se registra un pedido **entonces** recibe el siguiente número de la secuencia; dos pedidos
  simultáneos reciben números distintos.
- **Cuando** el pedido no tiene líneas **entonces** se rechaza: «El pedido debe tener al menos una línea.»
- **Cuando** la fecha del pedido es posterior a hoy (hora de Bolivia) **entonces** se rechaza: «La fecha del
  pedido no puede ser futura.» Sin fecha, se toma hoy.
- **Cuando** una línea tiene cantidad 1,5 de un producto por kilo y costo Bs 40,00 **entonces** se guarda
  cantidad `1.500`, costo `40.00` y subtotal `60.00` (cantidad × costo, escala 2 `HALF_UP`).
- **Cuando** una línea de un producto por unidad tiene cantidad decimal **entonces** se rechaza: «La cantidad de
  '<producto>' debe ser entera: se vende por unidad.»; con 0, negativa o más de 3 decimales, el mismo mensaje
  que en ventas.
- **Cuando** el costo unitario de una línea es negativo **entonces** se rechaza; un costo 0 se acepta
  (bonificación).
- **Cuando** una línea usa un producto eliminado **entonces** se rechaza; los inactivos se aceptan (se compran
  aunque no se vendan aún).
- **Cuando** se registra el pedido **entonces** cada línea guarda copia del nombre y código del producto, y el
  costo registrado del producto en ese momento (`previous_unit_cost`), para ver después si hubo alerta.
- **Cuando** las líneas suman Bs 300,00 y el envío es Bs 25,00 **entonces** el pedido guarda
  `items_total = 300.00`, `shipping_cost = 25.00` y `total_amount = 325.00` (costo con y sin envío, P-24).

### Envío como gasto de Transporte (P-24)

- **Cuando** el envío es mayor a 0 **entonces** se exige la cuenta con la que se pagó y se registra un gasto
  (`GASTO_MANUAL`) en esa cuenta, con la categoría de sistema Transporte, descripción «Envío pedido PED-000001»
  y vinculado al pedido (`financial_transactions.purchase_id`). Baja el saldo de la cuenta.
- **Cuando** el envío se paga desde Caja **entonces** exige caja abierta, se vincula a ella y descuenta del
  efectivo esperado (CAJ-03).
- **Cuando** el envío es 0 **entonces** no se crea movimiento.
- **Cuando** no existe la categoría Transporte **entonces** el pedido no se guarda: «No existe la categoría
  Transporte; no se puede registrar el envío.»
- El envío no cuenta para el estado de pago: se lo paga a quien transporta, no al proveedor.

## COM-03 · Producto nuevo, alerta de costo y precio sugerido

- **Cuando** el producto de una línea no existe **entonces** desde la línea se abre el formulario de producto
  (con el proveedor del pedido y el costo de la línea ya cargados), se crea con la API de productos y queda
  elegido en la línea.
- **Cuando** el costo de la línea difiere del costo registrado del producto **entonces** la pantalla muestra una
  alerta con ambos valores y pregunta si se actualiza el costo del producto.
- **Cuando** el usuario no confirma **entonces** el pedido se guarda y el costo y precio del producto no cambian.
- **Cuando** confirma **entonces** el sistema propone un precio de venta que mantiene la utilidad porcentual
  actual: `ceil(nuevo costo × precio actual ÷ costo actual)` al boliviano entero. Ej.: costo 10,00 → 12,30 con
  precio 15,00 (50 %) da 18,45 → **Bs 19,00**. El usuario acepta el sugerido, lo edita o mantiene el actual.
- **Cuando** el producto tiene costo 0 **entonces** no hay utilidad porcentual que mantener: no se sugiere precio
  y se propone mantener el actual.
- **Cuando** se guarda el pedido con la actualización confirmada **entonces**, en la misma transacción, el
  producto pasa al costo de la línea y al precio elegido, se recalculan utilidad fija y porcentual, y queda una
  fila de auditoría en `product_price_changes` (costo y precio anterior y nuevo, origen `PURCHASE`, pedido,
  usuario y fecha). La línea queda marcada `cost_updated`.
- **Cuando** el precio elegido es menor o igual a 0 **entonces** se rechaza; menor al costo se acepta (venta a
  pérdida, CAT-02).
- **Cuando** el mismo producto aparece en dos líneas con actualización **entonces** se rechaza: «El producto
  '<nombre>' tiene más de una línea que actualiza su costo.»
- **Cuando** cambia el costo **entonces** las ventas anteriores conservan su costo por línea (POS-05).
- La sugerencia la calcula el servidor: `GET /api/purchases/price-suggestion?productId=&newCost=`.

## COM-04 · Estado de pago

Calculado con lo pagado (suma de pagos activos) frente al total de las líneas (`items_total`):

| Pagado | Consignación | Estado |
| --- | --- | --- |
| 0 | no | Por pagar (`POR_PAGAR`) |
| 0 | sí | Consignación (`CONSIGNACION`) |
| > 0 y < total | — | A cuenta (`A_CUENTA`) |
| ≥ total | — | Pagado (`PAGADO`) |

- **Cuando** un pedido de Bs 300,00 recibe un pago de Bs 100,00 **entonces** queda A cuenta con saldo Bs 200,00.
- **Cuando** se paga el saldo **entonces** queda Pagado con saldo 0.
- **Cuando** el pedido es en consignación y no tiene pagos **entonces** se ve como Consignación (queda por pagar
  hasta que se registre el pago, P-21); al pagarlo sigue las mismas reglas.
- **Cuando** las líneas suman 0 **entonces** el pedido queda Pagado.
- El listado de pedidos filtra por proveedor, estado de pago y texto (número o comentario).

## COM-05 · Pagos

Un pedido admite varios pagos. Cada pago guarda fecha (la del servidor, como los movimientos, T-18), monto y
cuenta de origen (Caja con la caja abierta, Bisa u otra cuenta activa).

- **Cuando** se paga Bs 100,00 desde Bisa **entonces** se crea el pago y un gasto en Bisa con la categoría de
  sistema Pedido, descripción «Pago pedido PED-000001», vinculado al pedido; baja el saldo de Bisa.
- **Cuando** el monto es 0 o redondea a 0,00 **entonces** se rechaza: «El monto debe ser estrictamente mayor a
  0.00 Bs.»
- **Cuando** el monto supera el saldo pendiente **entonces** se rechaza: «El pago supera el saldo pendiente del
  pedido (Bs 200.00).»
- **Cuando** el pedido ya está pagado **entonces** se rechaza con el mismo mensaje (saldo Bs 0.00).
- **Cuando** la cuenta no existe o está eliminada **entonces** se rechaza (404).
- **Cuando** dos pagos del mismo pedido llegan a la vez **entonces** el pedido se bloquea primero y el segundo ve
  el saldo ya descontado.
- **Cuando** no existe la categoría Pedido **entonces** el pago no se guarda: «No existe la categoría Pedido; no
  se puede registrar el pago.»
- Pago hecho por una persona (DEU-02): pendiente, llega con el módulo Deudas.

## COM-06 · Pago desde la caja

- **Cuando** se paga desde Caja con la caja abierta **entonces** el gasto se registra en la cuenta Caja,
  vinculado a la caja abierta, y el efectivo esperado baja en el monto (CAJ-03, `ExpectedCashCalculator`).
- **Cuando** no hay caja abierta **entonces** se rechaza: «Los movimientos de la cuenta Caja requieren una caja
  abierta.»
- **Cuando** se mira el POS **entonces** el pago aparece entre los gastos de la caja, pero no se anula desde
  allí ni desde Finanzas: «Los movimientos de un pedido se gestionan desde Compras.» (se anulan con COM-09). Lo
  mismo para el gasto de envío.

## COM-07 · Entrega

Cada pedido tiene tipo de entrega (`EN_PUERTA` en puerta, `TERMINAL` en terminal, `EN_TIENDA` en tienda), estado
de entrega (`PENDIENTE`, `ENTREGADO`) y fecha de entrega (migración `V9__purchase_delivery.sql`).

- **Cuando** se registra un pedido sin tipo de entrega **entonces** queda En tienda; sin estado, Pendiente.
- **Cuando** el pedido queda Pendiente **entonces** la fecha es opcional y es la fecha estimada: puede ser
  futura.
- **Cuando** el pedido queda Entregado **entonces** la fecha es la de la entrega: sin fecha se toma hoy (hora de
  Bolivia) y si es futura se rechaza: «La fecha de entrega no puede ser futura.»; tampoco puede ser anterior a la
  fecha del pedido: «La fecha de entrega no puede ser anterior a la fecha del pedido.»
- **Cuando** se cambia la entrega de un pedido registrado (`PUT /api/purchases/{id}/delivery`) **entonces** se
  guardan tipo, estado y fecha con la auditoría de modificación (`updated_by`, `updated_at`); no mueve dinero.
- **Cuando** se vuelve un pedido Entregado a Pendiente **entonces** se acepta (corrección) y la fecha pasa a ser
  la estimada.
- **Cuando** el pedido está anulado **entonces** la entrega no se cambia: «El pedido está anulado.»
- El listado filtra también por estado de entrega.

## COM-08 · Pedidos por pagar

Vista con los pedidos registrados (no anulados) con saldo mayor a 0 (Por pagar, Consignación, A cuenta),
agrupados por proveedor (`GET /api/purchases/payables`).

- **Cuando** el proveedor Granel tiene un pedido de Bs 300,00 con Bs 100,00 pagados y otro de Bs 50,00 sin pagos
  **entonces** la vista muestra Granel con saldo Bs 250,00 y dos pedidos (saldo Bs 200,00 y Bs 50,00).
- **Cuando** un pedido está Pagado o anulado **entonces** no aparece.
- **Cuando** un pedido está en Consignación **entonces** aparece con su saldo (queda por pagar, P-21).
- **Cuando** se mira la vista **entonces** el total general es la suma de los saldos de todos los proveedores;
  los proveedores van por nombre y sus pedidos del más antiguo al más reciente.
- **Cuando** se filtra por proveedor **entonces** solo aparece ese proveedor.
- Desde la vista se abre el detalle del pedido para pagarlo (COM-05).

## COM-09 · Anulación de pedidos y pagos

Un pedido o un pago registrado no se edita: se corrige anulándolo con motivo (RG-03). Lo hace quien entra a
Compras, operador o administrador (tabla 2.2, P-33). La anulación revierte el dinero: los gastos vinculados al
pedido (`financial_transactions.purchase_id`) quedan `ANULADO` con el mismo motivo, usuario y fecha, la cuenta
recupera el monto y, si eran de Caja, el efectivo esperado se recalcula. Bloqueo en orden: pedido, caja de sus
movimientos, personas, cuentas (T-14).

### Anular un pago (`POST /api/purchases/{id}/payments/{paymentId}/void`)

- **Cuando** se anula un pago de Bs 100,00 desde Bisa con motivo **entonces** el pago queda `ANULADO` (quién,
  cuándo, motivo), su gasto Pedido queda anulado, Bisa sube Bs 100,00 y el pedido baja lo pagado a Bs 0,00: el
  estado se recalcula (Por pagar, Consignación o A cuenta).
- **Cuando** el pago era desde Caja y su caja sigue abierta **entonces** el efectivo esperado sube en el monto.
- **Cuando** el pago era desde Caja y su caja ya está cerrada **entonces** se rechaza: «El pago se hizo en una
  caja cerrada; no se puede anular.» (CAJ-06, T-16: una caja cerrada no cambia).
- **Cuando** el motivo falta o está en blanco **entonces** se rechaza: «El motivo de anulación es obligatorio.»
- **Cuando** el pago ya está anulado **entonces** se rechaza: «El pago ya está anulado.»
- **Cuando** el pago no es de ese pedido **entonces** se rechaza (404).
- **Cuando** el pedido está anulado **entonces** se rechaza: «El pedido está anulado.»

### Anular un pedido (`POST /api/purchases/{id}/void`)

- **Cuando** se anula un pedido con motivo **entonces** queda `ANULADO` (quién, cuándo, motivo), se anulan todos
  sus pagos activos y todos sus gastos activos (pagos y envío), y cada cuenta recupera lo suyo. Lo pagado queda
  en Bs 0,00 y sale de la vista por pagar.
- **Cuando** alguno de sus gastos de Caja es de una caja cerrada **entonces** no se anula nada: «El pedido tiene
  movimientos de una caja cerrada; no se puede anular.»
- **Cuando** el pedido ya está anulado **entonces** se rechaza: «El pedido ya está anulado.»
- **Cuando** el pedido había actualizado el costo o precio de un producto (COM-03) **entonces** el producto no
  vuelve atrás: el cambio sigue auditado y se corrige desde el catálogo.
- Un pedido anulado se sigue viendo en el listado (filtro Anulado) y en su detalle, con el motivo; no admite
  pagos ni cambios de entrega.

## Categorías de sistema

Pedido y Transporte son categorías de gasto que el código necesita, como Ventas: la migración les pone
`financial_categories.system_key` (`PURCHASE_PAYMENT`, `PURCHASE_SHIPPING`), reutilizando las que ya existan con
ese nombre. Finanzas no las elimina: «Las categorías de sistema no se pueden eliminar.»

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/purchases?supplierId=&paymentStatus=&deliveryStatus=&status=&search=` | Listado (sin líneas) |
| GET | `/api/purchases/payables?supplierId=` | Pedidos por pagar por proveedor (COM-08) |
| GET | `/api/purchases/{id}` | Detalle con líneas y pagos |
| POST | `/api/purchases` | Registrar pedido (COM-02, COM-03) |
| POST | `/api/purchases/{id}/payments` | Registrar pago (COM-05, COM-06) |
| PUT | `/api/purchases/{id}/delivery` | Cambiar entrega (COM-07) |
| POST | `/api/purchases/{id}/void` | Anular pedido (COM-09) |
| POST | `/api/purchases/{id}/payments/{paymentId}/void` | Anular pago (COM-09) |
| GET | `/api/purchases/payment-accounts` | Cuentas activas para pagar |
| GET | `/api/purchases/price-suggestion?productId=&newCost=` | Precio sugerido (COM-03) |

## Tareas

- [x] Migración `V8__purchases.sql`: `purchases`, `purchase_items`, `purchase_payments`,
  `product_price_changes`, `financial_transactions.purchase_id`, `financial_categories.system_key` con Pedido y
  Transporte, secuencia `purchase_number_seq`.
- [x] Permiso `PURCHASES` (Operador y Administrador) y lecturas cruzadas en proveedores, productos y categorías.
- [x] `PurchaseService` con registro de pedido, envío, actualización de costo y precio, pagos y estado de pago.
- [x] Finanzas y POS rechazan editar o anular movimientos de pedidos; Finanzas no elimina categorías de sistema.
- [x] Pruebas: `PurchaseServiceTest`, `PurchaseRepositoryTest` (contra PostgreSQL), `EndpointAuthorizationTest`.
- [x] Frontend: `core/purchase`, `pages/purchases` (listado, formulario con alerta de costo y producto nuevo,
  detalle con pagos), menú Compras → Pedidos.
- [x] Migración `V9__purchase_delivery.sql`: tipo, estado y fecha de entrega (COM-07).
- [x] `PurchaseService`: cambio de entrega, vista por pagar, anulación de pago y de pedido (COM-07 a COM-09).
- [x] Pruebas: `PurchaseServiceTest`, `PurchaseRepositoryTest`, `EndpointAuthorizationTest`.
- [x] Frontend: entrega en el formulario y el detalle, filtro de entrega y de anulados, pestaña Por pagar,
  anulación de pedido y de pago con motivo.
- [ ] Pago por persona (DEU-02), con el módulo Deudas.
