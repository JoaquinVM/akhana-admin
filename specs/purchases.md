# Compras (pedidos)

Detalle del módulo Compras de la V1 (sección 3.3 de `specs/requirements.md`), con los mismos IDs.
Alcance: COM-01 a COM-09, COM-11 a COM-14, COM-19. El pago hecho por una persona (origen «persona» de COM-05, que nace con DEU-01/DEU-02)
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
- **Cuando** el costo unitario tiene más de 2 decimales (30,555) **entonces** se rechaza con «El costo unitario de
  '<producto>' admite hasta 2 decimales.», en vez de redondearse sin aviso (segunda revisión, 29-10-2026). Desde la
  tercera revisión la pantalla envía el costo tal como se escribió (antes lo redondeaba y la regla nunca llegaba al
  servidor).
- **Cuando** se crea un producto nuevo desde una línea (COM-03) con costo 0 **entonces** se crea (CAT-01, con el aviso
  del servidor); antes la pantalla lo bloqueaba pidiendo un costo. Pruebas en `purchase-form-modal.component.spec.ts`.
- **Cuando** se lista Pedidos (más de 500 con el historial) **entonces** se dibujan de a 100 con «Mostrar 100 más»,
  y al cambiar un filtro las filas actuales quedan (atenuadas) hasta que llega la respuesta, en vez de borrarse y
  volver a dibujarse todas: cambiar de filtro tardaba 1 a 2 s. Las fechas del pedido y de entrega se muestran como
  día de la tienda (pipe `storeDay`), y «hoy» del formulario es el de Bolivia aunque el equipo tenga otra zona
  horaria. Pruebas en `purchases.component.spec.ts`, `store-day.pipe.spec.ts` y `store-date.spec.ts`.
- **Cuando** se presiona Enter en un campo del formulario de pedido **entonces** el pedido no se envía (antes Enter en
  el buscador, la cantidad o el costo registraba el pedido, con su pago desde Caja); solo el botón «Registrar
  pedido» lo registra. Enter en el buscador agrega el producto del código escrito o el único resultado, como en el
  POS. Lo mismo en el formulario de consumo. Pruebas en `purchase-form-modal.component.spec.ts`,
  `consumption-form-modal.component.spec.ts` y `product-search.spec.ts` (cuarta revisión, 29-10-2026).
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

### Aviso de efectivo al registrar o editar un pedido (CAJ-03)

El aviso va en su propia fila, antes de los totales, y nunca bloquea. Con efectivo esperado Bs 300,00:

- **Cuando** el pago inicial o el envío salen de Caja y superan el esperado **entonces** aparece «El monto supera el
  efectivo esperado en caja (Bs 300,00). Verifique que haya ese dinero en la gaveta antes de registrarlo.»; con Bs 300,00
  exactos no hay aviso.
- **Cuando** el pago es Bs 290,00 y el envío Bs 25,00, ambos desde Caja **entonces** hay un solo aviso: «El pago y el envío
  suman Bs 315,00 y superan el efectivo esperado en caja (Bs 300,00). …»
- **Cuando** una parte sale de otra cuenta, de una persona o el envío es 0 **entonces** esa parte no cuenta.
- **Cuando** se elige Caja **entonces** el esperado se consulta en ese momento; sin caja abierta o si falla la consulta, no
  hay aviso.
- **Cuando** se edita un pedido sin cambiar el envío **entonces** no hay aviso. Si pasa de otra cuenta a Caja se compara el
  envío completo; si ya era de Caja, solo la diferencia, porque COM-12 anula el anterior.
- La opción dice «Caja», sin «(caja abierta)», en las listas del formulario y del detalle. **Cuando** se elige y el servidor
  responde que no hay caja abierta **entonces** debajo aparece «La caja está cerrada. Ábrala y vuelva a elegir Caja.» y se
  desactiva el botón de guardar, hasta elegir otra cuenta o volver a elegir Caja con la caja ya abierta; al editar, solo si el
  envío cambia. Con pago y envío en Caja, la última respuesta vale para las dos listas. Un fallo de la consulta no muestra la
  nota ni bloquea. Si aun así llega al servidor con la caja cerrada, el
  rechazo es el de COM-06 y no se pierde lo escrito.
- Pruebas en `purchase-form-modal.component.spec.ts`, `purchase-detail-modal.component.spec.ts`,
  `cash-limit-warning.component.spec.ts` y `cash-closed-note.component.spec.ts`.

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

Un pago registrado no se edita: se corrige anulándolo con motivo (RG-03); un pedido se anula o se edita (COM-11). Lo hace quien entra a
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

## COM-11 · Edición de pedidos

Caso típico: llega algo que no se pidió, falta un producto o llega otra cantidad o costo (P-51). Se edita desde el
detalle con «Editar», que abre el mismo formulario del alta con el pedido cargado (sin el pago inicial: los pagos
se registran y anulan aparte, COM-05 y COM-09). `PUT /api/purchases/{id}`; lo hace quien entra a Compras (P-33).

- **Cuando** el pedido está anulado **entonces** se rechaza: «El pedido está anulado.»; si es histórico, «El pedido
  es histórico (migrado de la planilla): solo se le pueden registrar pagos.»
- **Cuando** el pedido está pendiente o entregado, por pagar, a cuenta o pagado **entonces** se puede editar.
- **Cuando** el motivo falta o está en blanco **entonces** se rechaza: «El motivo del cambio es obligatorio.»
  (siempre, P-51).
- **Cuando** se envía el pedido sin ningún cambio **entonces** se rechaza: «No hay cambios para guardar.»
- **Cuando** llega un producto que no se pidió **entonces** se agrega como línea nueva, con las mismas reglas de
  COM-02 (cantidad, costo, producto eliminado) y su costo registrado de ese momento (`previous_unit_cost`).
- **Cuando** llega otra cantidad o a otro costo **entonces** se cambia en la línea y se recalcula su subtotal; la
  línea conserva el costo registrado de cuando se agregó.
- **Cuando** falta un producto **entonces** su línea se quita: baja lógica (`removed_at`, `removed_by`), sale del
  detalle y del total, y queda en el historial de cambios. El pedido debe conservar al menos una línea.
- **Cuando** una línea existente llega con otro producto **entonces** se rechaza: «No se cambia el producto de una
  línea: quítela y agregue otra.»
- **Cuando** el producto no existe en el catálogo **entonces** se crea desde la línea, como en el alta (COM-03).
- **Cuando** el costo de una línea nueva o cambiada difiere del costo del producto **entonces** se alerta y se
  propone el precio de venta igual que en el alta (COM-03, P-29), con la fila en `product_price_changes` vinculada
  al pedido.
- **Cuando** se cambian proveedor, fecha, consignación, comentario o entrega **entonces** se validan como en el alta
  (proveedor activo si cambia, fecha no futura, COM-07).
- **Cuando** cambia el total de las líneas **entonces** se recalculan `items_total`, `total_amount`, saldo y estado
  de pago: un pedido Pagado de Bs 300,00 al que se agrega una línea de Bs 20,00 queda A cuenta con saldo Bs 20,00.

## COM-12 · Efectos de la edición en el dinero y auditoría

- **Cuando** el nuevo total de las líneas es menor a lo pagado (pagos de cuentas y de personas, DEU-02)
  **entonces** se rechaza: «El total del pedido (Bs 250,00) no puede ser menor a lo pagado (Bs 300,00); anule
  primero el pago.» Igual a lo pagado se acepta (queda Pagado). Las deudas con personas no cambian.
- **Cuando** cambia el envío (monto o cuenta) **entonces** la pantalla alerta «Se anulará el gasto de envío de
  Bs X en <cuenta> y se registrará uno nuevo» y, al guardar, el gasto de Transporte anterior queda `ANULADO` con el
  motivo, la cuenta recupera el monto y se registra el nuevo como en COM-02 (envío 0: sin gasto nuevo).
- **Cuando** el gasto de envío anterior fue en una caja cerrada **entonces** se rechaza: «El envío se pagó en una
  caja cerrada; no se puede cambiar.»; si fue de Caja con la caja abierta, el efectivo esperado se recalcula.
- **Cuando** el envío no cambia **entonces** su gasto no se toca.
- Bloqueo en orden: pedido, caja, cuentas del envío anterior y nuevo juntas por id (T-14). Todo en una transacción.
- **Cuando** se guarda una edición **entonces** queda una fila en `purchase_revisions` con quién, cuándo, motivo y el
  resumen de lo que cambió, armado en el servidor (ej. «Cantidad de Maní: 3 → 2 · Línea agregada: Barra Chia × 2 a
  Bs 10,00 · Línea quitada: Pasas · Total: Bs 300,00 → Bs 280,00»), y el pedido guarda `updated_by` y
  `updated_at`. El detalle muestra el historial de cambios, del más reciente al más antiguo.

## COM-13 · Precio de venta como referencia

En el formulario de pedido, al registrar y al editar. Es el precio actual del catálogo, de solo lectura: no se envía al
servidor ni se guarda en el pedido, y no cambia el costo, el total ni el precio del producto. El precio se sigue cambiando
solo con las opciones «Sugerido», «Mantener» y «Otro» de la alerta de costo (COM-03). El detalle y el listado de pedidos
no lo muestran.

- **Cuando** se busca un producto por unidad (costo Bs 10,00, precio Bs 15,00) **entonces** el resultado sigue mostrando
  a la izquierda lo que ya tenía y, aparte a la derecha y resaltado, «Venta» con «Bs 15,00»; por kilo, «costo Bs 40,00/kg»
  a la izquierda y «Bs 60,00/kg» a la derecha. A 720 px o menos, el precio va debajo del texto.
- **Cuando** se agrega una línea **entonces** la columna «Precio de venta», después de «Subtotal», separada con una línea
  y resaltada, muestra el precio de una unidad o un kilo sin multiplicar por la cantidad: «Bs 15,00» o «Bs 60,00/kg». Un
  producto creado desde la línea muestra el precio con que se creó. La unidad va al lado de la cantidad, con ancho
  fijo para que las casillas de esa columna queden alineadas entre filas (COM-14).
- **Cuando** se cambia el costo de la línea o se elige otro precio en la alerta **entonces** la columna sigue en el precio
  actual hasta guardar; el otro pedido que se abra después ya muestra el nuevo.
- **Cuando** se edita un pedido **entonces** cada línea muestra el precio actual del catálogo, que puede ser distinto del
  que tenía al hacer el pedido; mientras el catálogo no cargó o si el producto fue eliminado, «—», nunca «Bs 0,00».
- **Cuando** se vuelve a abrir el formulario **entonces** no se usa el catálogo de la apertura anterior: hasta que llega
  el nuevo, las líneas muestran «—» y el buscador no da resultados.
- **Cuando** el producto del catálogo tiene precio 0 **entonces** muestra «Bs 0,00», sin aviso.

Pruebas en `purchase-form-modal.component.spec.ts`.

## COM-14 · Botones de cantidad

Cada línea del formulario de pedido, al registrar y al editar, usa `shared/components/quantity-stepper`, el mismo control
del POS. Producto por unidad: Té verde. Producto por kilo: Nueces.

- **Cuando** Té verde tiene cantidad 2 y se aprieta + **entonces** queda en 3; con − queda en 1. El subtotal y los totales
  se recalculan al momento.
- **Cuando** Nueces tiene 1,500 kg y se aprieta + **entonces** queda en 1,600 kg; con − queda en 1,400 kg, sin decimales
  de más.
- **Cuando** se agrega un producto **entonces** entra con 1 unidad o 1 kg, como antes (en el POS, por peso entra con 0,1).
- **Cuando** Té verde tiene cantidad 1 **entonces** el − está deshabilitado. **Cuando** Nueces tiene 0,100 kg o menos
  **entonces** también: se deshabilita si restar dejaría 0 o menos. La línea se quita solo con su X. En el POS, en cambio,
  el − al llegar a 0 quita la línea.
- **Cuando** se vacía el campo o se escribe 0 **entonces** la línea no se quita, el − queda deshabilitado y el + sigue
  desde 0; registrar con cantidad 0 lo rechaza el servidor (COM-02).
- **Cuando** una línea registrada cambia de cantidad con los botones **entonces** conserva su `itemId` (COM-11).
- Cantidad, costo unitario, costo de envío y monto pagado no muestran las flechitas del navegador; el costo y el precio
  de «Producto nuevo» y el precio «Otro» de la alerta de costo las conservan. La unidad (u o kg) va al lado del control,
  en la misma línea. Pruebas en
  `purchase-form-modal.component.spec.ts` y `quantity-stepper.component.spec.ts`.

## COM-19 · Monto del pago inicial

Solo en el alta (editar no tiene pago inicial, COM-11, y el detalle ya propone el saldo). Pedido de ejemplo: líneas por
Bs 300,00 y envío de Bs 25,00, que se sigue pagando aparte (P-24).

- **Cuando** se elige una cuenta o una persona **entonces** el monto viene lleno con `300.00`.
- **Cuando** se registra sin tocar el monto **entonces** se envían Bs 300,00 y el pedido queda Pagado.
- **Cuando** el monto no se tocó y se agrega una línea de Bs 20,00 **entonces** pasa a `320.00`. Lo mismo al cambiar una
  cantidad o un costo y al quitar una línea. Si cambia el envío, no cambia.
- **Cuando** se elige el origen antes de agregar líneas, o las líneas suman 0 **entonces** el monto queda vacío, nunca
  `0.00`, y registrar sin monto da «Ingrese el monto del pago inicial.»
- **Cuando** se entra al campo **entonces** su contenido queda seleccionado: escribir `100` lo reemplaza.
- **Cuando** se escribió un monto **entonces** ya no cambia solo, aunque cambien las líneas; la nota de debajo sigue con el
  total al día. Si supera el total, el servidor lo rechaza (COM-05) y no se guarda nada.
- **Cuando** se vacía el campo **entonces** queda vacío y registrar pide el monto.
- **Cuando** se cambia de origen **entonces** el monto no cambia. **Cuando** se vuelve a «Sin pago por ahora» y se elige otra
  vez un origen **entonces** viene lleno de nuevo con la suma.
- **Cuando** una línea tiene 0,015 kg a Bs 137,00 **entonces** el subtotal, el total y el monto dicen Bs 2,06, como el
  servidor (`BigDecimal` `HALF_UP`): la pantalla calculaba con decimales de JavaScript y podía quedar un centavo abajo.

Pruebas en `purchase-form-modal.component.spec.ts`.

## Día de los pagos (CAJ-11)

- **Cuando** un pago o un envío se paga desde Caja pasada la medianoche en la caja del 31/10 **entonces** su gasto y
  el pago cuentan para el 31/10 y el detalle del pedido muestra «Día 31/10 · cargada el 01/11 00:30» en lugar de la
  fecha. Desde otra cuenta o hecho por una persona, el día es el de su registro. La fecha del pedido no cambia.
  Pruebas en `PurchaseServiceTest` y `purchase-detail-modal.component.spec.ts`; detalle en `cash.md`.

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
| PUT | `/api/purchases/{id}` | Editar pedido con motivo (COM-11, COM-12) |
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
- [x] Migración `V20__purchase_edit.sql`: `purchase_items.removed_at/removed_by` y `purchase_revisions` (COM-11, COM-12).
- [x] `PurchaseService.updatePurchase` con líneas, COM-03, envío rehecho y revisión; pruebas en `PurchaseServiceTest`.
- [x] Frontend: «Editar» en el detalle, formulario en modo edición con motivo y alerta de envío, historial de cambios.
  Pruebas en `purchase-form-modal.component.spec.ts` y `purchase-detail-modal.component.spec.ts`; verificado en el
  navegador con la base local (edición con línea nueva, cantidad, envío rehecho y rechazo por total menor a lo pagado).
- [x] COM-13: precio de venta en el buscador y en la tabla de `purchase-form-modal`, solo frontend.
- [x] COM-20 a COM-25: lectura desde foto o texto. Migración `V28__purchase_readings.sql`, `PurchaseReadingService`,
  `OpenAiPurchaseReader`, `ProductTextMatcher`, `PurchasePhotoService`; pantalla en `purchase-reading-modal`,
  `reading-lines-panel`, `reading-notice` y `purchase-photo-panel`. Falta la medición previa.

## COM-20 a COM-25 · Pedido desde foto o texto

Flujo normal: al pedir se pega el WhatsApp y queda un pedido pendiente con costos del sistema; al llegar, se registran los
precios reales con foto o a mano. Una compra sin texto entra por foto como pedido entregado. Decisiones en P-60 a P-62.

| ID | Cuando… | Entonces… |
| --- | --- | --- |
| CA-01 | Se pega un texto con «2 jamaica 130ml» | El formulario abre con la línea, cantidad 2 y el producto; no se creó ningún pedido |
| CA-02 | La línea coincide con un producto | Queda elegido con su costo del sistema, y el pedido sale con «costo por confirmar» |
| CA-03 | Hay varias coincidencias o ninguna | Muestra sugerencias, buscador, «Crear producto» y «Quitar línea»; no se registra hasta resolverla |
| CA-04 | El texto dice «4 a 5» o «Blanco» sin número | Propone 4 y 1, resaltados con el texto original |
| CA-05 | El texto trae título de grupo: «Cepillos: 2 Amarillo» | La línea lleva el grupo: «Cepillos Amarillo» (medición previa) |
| CA-06 | El texto parece traer varios proveedores | Avisa «Pega un proveedor por vez.» y no llena nada |
| CA-07 | El texto no dice el proveedor | Queda vacío y hay que elegirlo; si lo dice, se empareja con los activos |
| CA-08 | Se registra un pedido desde texto | Entrega pendiente; en Pedidos y Por pagar se ve «costo por confirmar» y el monto como estimado |
| CA-09 | En un pedido por confirmar se pulsa «Llegó la mercadería, con foto» | La foto llena cantidades y costos sobre ese pedido; lo que no está en la factura queda señalado, no se quita solo. Al confirmar se edita (COM-11) con motivo «Llegó la mercadería», queda entregado y sin la marca |
| CA-10 | Se pulsa «Llegó la mercadería, a mano» | Se editan a mano, con el mismo resultado. Una edición normal o cambiar solo la entrega no quitan la marca |
| CA-11 | Un costo real difiere del registrado | Aparece la alerta y el precio sugerido de COM-03; los pagos respetan COM-12 |
| CA-12 | Se usa Desde foto y hay un pedido pendiente de ese proveedor | Primero ofrece completarlo; si no, sigue como pedido nuevo |
| CA-13 | Foto sin pedido pendiente | Pedido nuevo con entrega entregado y los costos de la factura |
| CA-14 | La foto no trae costo por unidad, pero sí el importe | Costo = importe ÷ cantidad, HALF_UP, con un mensaje del cálculo |
| CA-15 | No se puede saber el costo | Queda en blanco con el mensaje del cálculo; no se registra hasta llenarlo |
| CA-16 | Hay una marca de repetido («〃») | La línea usa el texto de la línea de arriba (medición previa) |
| CA-17 | Hay líneas o totales tachados | Se usa lo vigente (medición previa) |
| CA-18 | La suma de líneas y envío no da el total leído | Avisa la diferencia, sin bloquear; solo con todas las líneas y costos llenos |
| CA-19 | La nota dice «Cancelado» | Pide elegir el origen del pago inicial, con el monto de COM-19; «No se pagó» quita la propuesta. Al completar un pedido existente, solo avisa: el pago va desde el detalle |
| CA-20 | La factura trae envío | Propone el monto, con cuenta vacía (P-24) |
| CA-21 | Se registra un pedido con foto | La foto se sube después de registrar, queda reducida, sin metadatos, visible en el detalle con el mismo permiso y en la copia fuera del VPS. Si falla la subida, el pedido sigue registrado y el detalle permite agregarla |
| CA-22 | La foto queda sin registrar | Solo pasó por memoria: no queda guardada |
| CA-23 | La foto no es JPEG, PNG ni WebP, o pesa de más | Mensaje de CAT-12, sin llamar a OpenAI. El navegador convierte a JPEG lo que puede abrir, como HEIC en iPhone |
| CA-24 | OpenAI falla, tarda o responde algo inválido | «No se pudo leer. Puedes intentar de nuevo o llenarlo a mano.»; conserva el texto, no crea nada, sin reintentos |
| CA-25 | El gasto del mes llega a US$10 | Botones apagados con «Se llegó al tope de este mes»; el administrador ve el gasto |
| CA-26 | Un usuario sin `PURCHASES` llama a estas rutas | 403 |
| CA-27 | Se revisa el código y los registros | La clave no está en el repo, el navegador ni los registros; las llamadas van con `store: false` |
| CA-28 | Se abre en un teléfono de 375 × 812 | Tomar foto, pegar texto, revisar y confirmar sin desplazamiento horizontal |

Notas de implementación:

- `POST /api/purchases/readings/text` y `/photo` responden un borrador; `GET /readings/status` dice si está encendido y, al
  administrador, el gasto. La foto y su lectura se enlazan con `readingId` al registrar o al llegar; una lectura va con un solo pedido.
- A OpenAI va la foto ya reducida de CAT-12, sin metadatos. Las instrucciones están en `resources/purchase-reading/`; cambiarlas
  obliga a repetir la medición.
- El gasto sale del uso que informa cada respuesta, a los precios de `application.properties`, redondeado hacia arriba. Una
  llamada que no informa uso cuenta US$0,01. Al cambiar de modelo hay que actualizar esos precios.
- Los archivos de la foto de factura viven en el volumen de las fotos de productos, con nombres únicos del servidor, y usan su misma copia.

### Medición previa a producción

Con la instrucción congelada y una copia de la tabla de datos correctos guardada antes de medir; Joaquín corrige esos datos
antes de correrla. Si algo no cumple, se le presenta antes de publicar.

| Qué se mide | Con qué | Umbral |
| --- | --- | --- |
| Cantidades con número claro | Los 5 textos reales de Joaquín y otros que sume | ≥ 95 % de líneas |
| Producto correcto entre las sugerencias | Esos textos y las 22 fotos, contra el catálogo real | ≥ 80 % de líneas |
| Cantidades, costos y totales en fotos | Las 17 fotos medidas de la prueba 012 | Al menos 67 de 69 líneas y 17 de 17 totales |
| Reglas nuevas | Marcas de repetido, importe visible | Se informa cada caso |
| Tiempo: borrador corregido contra tipeado | 3 pedidos reales, incluida la nota de pan | Más rápido en al menos 2 de 3 |
