# Inventario

Detalle de la sección 3.10 de `specs/requirements.md` (INV) y de CAT-05, CAT-14, CAT-15, CON-04, COM-10, COM-28,
COM-29 y T-04. Moneda Bs, montos con escala 2 `HALF_UP`; cantidades en la unidad base del producto, con hasta 3
decimales en los productos por kilo.

La Fase 3 se hace en cuatro trabajos, uno detrás de otro (decisión de Joaquín, 21-11-2026):

1. **Empaques** (CAT-15, COM-10, COM-29), sin stock.
2. **Núcleo**: kardex valorado, promedio ponderado, costo con envío, existencias, «Quedan N», aviso en la caja, conteo
   en cajas y arranque por categorías (CAT-05, CAT-14, CON-04, T-04, INV-01 a INV-03, INV-05 a INV-12, INV-14,
   INV-15 e INV-18).
3. **Bonificaciones y devoluciones** (COM-28, INV-22, INV-13 e INV-16).
4. **Reposición y análisis** (INV-04, INV-17, INV-19, INV-20 e INV-21).

Los empaques van antes del arranque para que el conteo inicial se haga en cajas y las cajas que hoy son productos se
cuenten dentro de su producto.

## CAT-15 · Empaques en el catálogo

Un empaque pertenece a un producto y dice cuántas unidades base trae: Caja ×12 (12 unidades), Display ×24, Saco 25 kg
(25 kg), Bolsa 250 g (0,250 kg). Las presentaciones de CAT-11 son empaques que se venden (`product-presentations.md`).
En el código, un empaque sigue siendo `ProductPresentation`.

| Barra Chia (costo 2,13, precio 4,00) | Equivalencia | Se vende | Precio propio | Precio en el POS | Costo |
| --- | --- | --- | --- | --- | --- |
| Caja ×12 | 12 | Sí | 44,00 | 44,00 | 25,56 |
| Display ×24 | 24 | No | — | — | 51,12 |

- **Cuando** se abre el formulario de un producto que no es especial **entonces** muestra la sección «Empaques», por
  unidad y por kilo, editable con el permiso `PRODUCTS`. Cada empaque tiene nombre, equivalencia, «Se vende», precio
  propio y código de barras.
- **Cuando** el producto se vende por unidad **entonces** la equivalencia es un número entero desde 2: «La equivalencia
  de '<nombre>' debe ser un número entero mayor a 1.». **Cuando** se vende por kilo **entonces** son kilos mayores a 0
  con hasta 3 decimales, como el peso de CAT-11: «El peso debe ser mayor a 0 kg.», «El peso admite hasta 3 decimales
  (gramos).».
- **Cuando** dos empaques activos del producto tienen el mismo nombre (sin distinguir mayúsculas) o la misma
  equivalencia **entonces** «Ya existe el empaque '<nombre>' en este producto.» o «Ya existe un empaque de 12 u en este
  producto.» («de 0,227 kg» en los productos por kilo).
- **Cuando** se quiere guardar un séptimo empaque activo **entonces** «Un producto admite hasta 6 empaques activos.»
- **Cuando** un empaque se vende **entonces** su precio es el propio o, sin él, equivalencia × precio del producto,
  redondeado a 2 decimales (Caja ×12 de Barra Chia sin precio propio: 12 × 4,00 = 48,00). Un empaque que no se vende
  no tiene precio, y su precio propio se descarta al guardar.
- **Cuando** se muestra o se usa el costo de un empaque **entonces** es siempre equivalencia × costo del producto
  (12 × 2,13 = 25,56), sin guardarse: una compra que cambia el costo (COM-03) lo actualiza. Desde el núcleo es
  equivalencia × lo que cuesta una línea ahora: el promedio si el producto controla stock (INV-06).
- **Cuando** el precio de un empaque que se vende es menor a su costo **entonces** se guarda con el aviso «El empaque
  '<nombre>' se vende a pérdida.» (CAT-02).
- **Cuando** se cambia el precio de un producto con empaques de precio propio **entonces** se guarda y se avisa: «Estos
  empaques tienen precio propio y no cambian: Caja ×12 (Bs 44,00).»
- **Cuando** se quita un empaque **entonces** se desactiva, no se borra (RG-01): las ventas, consumos y compras que lo
  usaron lo siguen mostrando. Uno inactivo se reactiva si no choca con otro activo.
- **Cuando** un producto cambia entre unidad y kilo **entonces** sus empaques activos se desactivan, porque su
  equivalencia cambia de significado; al pasar a kilo, el formulario propone 100 g y 1/2 libra como en CAT-11.
- **Cuando** se crea, cambia, desactiva o reactiva un empaque, o cambia su precio propio **entonces** queda quién y
  cuándo, y el precio propio deja su fila en `product_price_changes` como el precio fijo de CAT-11.
- Los productos especiales (POS-11, POS-13) no tienen empaques.

### Código de barras del empaque

- **Cuando** se escribe o se escanea el código de un empaque **entonces** vale lo mismo que en CAT-10: 8, 12 o 13
  dígitos con su dígito de control («El código de barras no es válido.»), un solo código por empaque.
- **Cuando** el código ya es de un producto o de otro empaque activo **entonces** «El código ya es de Barra Chia
  (NSE-BAR-CHI).» o «El código ya es de Barra Chia · Caja ×12.», y no se guarda.
- **Cuando** el producto se vende por kilo **entonces** sus empaques admiten código (una Bolsa 250 g de fábrica), aunque
  el producto sigue sin código propio (CAT-10).
- **Cuando** se quita el empaque o su código **entonces** el código se desactiva con quién y cuándo y queda libre.
- **Cuando** se busca en Productos el código de un empaque **entonces** aparece su producto.

### Datos existentes

- **Cuando** se aplica la migración **entonces** cada presentación pasa a ser un empaque que se vende, con su peso como
  equivalencia y su precio fijo como precio propio. Las ventas y consumos con presentación guardan igual sus kilos, que
  ahora se llaman cantidad base.

## CAT-15 · Empaques en el POS y en Consumos

- **Cuando** se busca en el POS un producto por unidad con empaques que se venden **entonces** cada uno sale como una
  fila más: «Barra Chia · Caja ×12 · Bs 44,00». Elegir «Barra Chia» agrega 1 unidad, como siempre; elegir la caja
  agrega la línea «Barra Chia · Caja ×12», cantidad 1, Bs 44,00. Los productos rápidos siguen agregando el producto.
- **Cuando** se elige un producto por kilo **entonces** el selector de CAT-11 ofrece solo sus empaques activos que se
  venden, de menor a mayor, y «Otro peso». Un Saco 25 kg que solo se compra no aparece.
- **Cuando** se escanea el código de un empaque que se vende **entonces** entra su línea con cantidad 1, o suma 1, sin
  abrir el selector, igual que un producto (POS-15).
- **Cuando** se escanea el código de un empaque que no se vende **entonces** no se agrega nada y suena el aviso: «El
  empaque Display ×24 de Barra Chia no se vende entero.»
- **Cuando** se elige otra vez el mismo empaque **entonces** suma 1 a su línea. La cantidad de una línea con empaque es
  entera y no se cambia de empaque dentro de la línea, como en CAT-11.
- **Cuando** la venta es al costo (CON-02) **entonces** el empaque cuesta equivalencia × costo y se ignora su precio
  propio (Caja ×12: 25,56).
- **Cuando** se vende **entonces** el servidor recalcula precio y costo desde el catálogo; si el empaque ya no está
  activo o ya no se vende, «El empaque 'Caja ×12' de 'Barra Chia' ya no está disponible.»
- **Cuando** se registra un consumo **entonces** el selector de los productos por kilo es el mismo; los productos por
  unidad se consumen por unidad.

### Qué guarda la línea

Ejemplo: 2 × Barra Chia Caja ×12, costo 2,13.

| Campo | Valor |
| --- | --- |
| Empaque (id y copia de nombre y equivalencia) | Caja ×12, 12 |
| Cantidad | 2 |
| Cantidad base (`base_quantity`) | 24 |
| Precio unitario | 44,00 |
| Subtotal | 88,00 |
| Costo unitario (`unit_cost`, por unidad base) | 2,13 |
| Costo de la línea (cantidad base × costo unitario) | 51,12 |

- **Cuando** la línea tiene empaque **entonces** cantidad base = cantidad × equivalencia y el costo de la línea es
  cantidad base × `unit_cost`. Los reportes suman la cantidad base (24 unidades; en kilos, como CAT-11). Desde el
  núcleo, la cantidad base es lo que sale del stock.
- **Cuando** se ve el detalle de la venta o el consumo **entonces** la línea dice «Barra Chia · Caja ×12» y «2 × Bs
  44,00 (24 u)»; en los productos por kilo, «(0,454 kg)» como hasta ahora.
- **Cuando** después cambia el empaque **entonces** la línea no cambia (copia al vender, POS-05).

## COM-29 · Compras por empaque

Ejemplo: 2 Caja ×12 de Barra Chia a Bs 25,50 la caja.

| Campo | Valor |
| --- | --- |
| Empaque (id y copia de nombre y equivalencia) | Caja ×12, 12 |
| Cantidad (cajas, enteras) | 2 |
| Costo unitario (por caja) | 25,50 |
| Subtotal | 51,00 |
| Cantidad base | 24 |
| Costo por unidad base | 2,13 (25,50 / 12 = 2,125, `HALF_UP`) |

- **Cuando** se agrega una línea al registrar o editar un pedido **entonces** se elige «Unidad» (o «Kilo») o uno de los
  empaques activos del producto, se vendan o no. La línea muestra la cantidad base y el costo por unidad base: «24 u ·
  Bs 2,13 c/u».
- **Cuando** la línea tiene empaque **entonces** la cantidad son empaques enteros («La cantidad de 'Barra Chia · Caja
  ×12' debe ser entera.») y el costo es por empaque. El subtotal sigue siendo cantidad × costo unitario: el total del
  pedido, los pagos y lo que se debe no cambian de fórmula.
- **Cuando** el costo por unidad base difiere del costo del producto **entonces** sale la alerta de COM-03 con ese
  costo, y al confirmarla el producto toma 2,13 y el precio sugerido se calcula con 2,13.
- **Cuando** se edita un pedido (COM-11) **entonces** se puede cambiar el empaque de una línea («Empaque de Barra Chia:
  Unidad → Caja ×12» en los cambios). Una línea cuyo empaque se desactivó después lo conserva mientras no se cambie.
- **Cuando** el empaque elegido ya no está activo **entonces** «El empaque 'Caja ×12' de 'Barra Chia' ya no está
  disponible.»; uno de otro producto no se encuentra.
- **Cuando** se ve el detalle del pedido **entonces** la línea dice «Barra Chia · Caja ×12», «2 × Bs 25,50 (24 u)».
- La lectura por foto o texto (COM-20) sigue proponiendo las líneas en unidad base; el empaque se elige al revisarla.

## COM-10 · Cajas-producto a empaques

En la V1, algunas cajas son productos aparte (Barra Chia Caja x12, NSE-BAR-CHI-CAJ, junto a Barra Chia, NSE-BAR-CHI).

- **Cuando** se abre «Cajas a empaques» en Productos **entonces** se ven los productos activos, por unidad y no
  especiales, que parecen una caja de otro producto activo del mismo proveedor y que nadie revisó: su nombre dice «caja»,
  «cja», «display», «pack», «paquete» o «fardo» y una cantidad («x12», «x 24», «×6»), sin gramos ni mililitros detrás;
  y su producto base es el de código igual sin el final (`-CAJ`, `-CJA`, `-DIS`, `-PAC`) o, si no, el de nombre igual
  sin esas palabras. Cada fila muestra la caja, su producto base, la equivalencia y el precio propuestos. Se muestra
  «Faltan N».
- **Cuando** se confirma **entonces** se puede corregir el nombre (propuesto: «Caja ×12»), la equivalencia y el precio
  propio (propuesto: el precio de la caja), y en la misma transacción: el producto base recibe el empaque que se vende,
  con el código de barras de la caja si tenía; la caja se elimina (baja lógica, CAT-03), con quién y cuándo, sus
  códigos se desactivan y deja de verse en los productos rápidos. Sus ventas y compras pasadas no cambian.
- **Cuando** el empaque no se puede crear (nombre o equivalencia repetidos, siete empaques, código de otro producto)
  **entonces** se muestra el mensaje de CAT-15 y no cambia nada.
- **Cuando** se elige «Descartar» **entonces** la caja sale de la lista con quién y cuándo, y sigue siendo un producto.
- **Cuando** una caja ya fue confirmada o descartada **entonces** no vuelve a proponerse.
- Las cajas que la lista no encuentra se pasan a mano: se crea el empaque en el producto base y se elimina la caja.

## Núcleo · Stock y kardex (trabajo 2)

### CAT-05, CAT-14, INV-18 · Qué productos controlan stock

| Producto | Estado | Mueve stock | La caja avisa | Costo de la línea |
| --- | --- | --- | --- | --- |
| Creado en la V2 (formulario o importación) | Controla stock, desde 0 | Sí | Sí | Promedio |
| De la V1, antes de su primer conteo | Por contar | No | No | Último costo, como en la V1 |
| Especial (POS-11, POS-13) o marcado «No controla stock» | No controla stock | No | No | Último costo |

- **Cuando** se aplica la migración **entonces** todo producto que existía queda «Por contar» y los especiales no
  controlan stock.
- **Cuando** se crea un producto, desde el formulario o la importación (CAT-06) **entonces** controla stock desde ese
  momento, con 0, salvo que se desmarque «Controla stock».
- **Cuando** se desmarca «Controla stock» **entonces** deja de mover stock y sale de los conteos; su kardex queda.
  **Cuando** se vuelve a marcar **entonces** queda «Por contar» y su próximo conteo fija el stock.
- **Cuando** se cambia la unidad (unidad o kilo) de un producto que ya tiene movimientos de stock **entonces** «La
  unidad de '<producto>' no se puede cambiar: ya tiene movimientos de stock, que van en esa unidad.» (CAT-14).
- Solo mueve stock un producto que lo controla en ese momento: lo que pasó antes no se recalcula.

### INV-01, INV-03, T-04 · Kardex valorado

Cada fila lleva cantidad y valor con signo y, después de ella, el saldo en cantidad, valor y costo promedio. El saldo
es la suma de las filas; `product_stocks` es un atajo que se escribe en la misma transacción.

| Fila | Cantidad | Costo unit. | Valor | Saldo | Promedio | Valor del saldo |
| --- | --- | --- | --- | --- | --- | --- |
| Pedido entregado PED-1 | +10 | 10,00 | 100,00 | 10 | 10,00 | 100,00 |
| Pedido entregado PED-2 | +10 | 12,00 | 120,00 | 20 | 11,00 | 220,00 |
| Venta VTA-1 | −5 | 11,00 | −55,00 | 15 | 11,00 | 165,00 |

- **Cuando** se abre el Kardex de un producto **entonces** se elige un rango de días de la tienda (por defecto, el mes
  en curso); abre con el saldo de antes del rango y muestra cada fila con quién y cuándo.
- **Cuando** algo mueve stock **entonces** la fecha es la del servidor (INV-08): ninguna fila entra con fecha pasada y
  toda corrección es otra fila por la diferencia.
- **Cuando** el producto tiene empaques **entonces** Existencias y Kardex leen el stock en empaques («40 = 3 Caja ×12 +
  4», CAT-15).
- No hay pantalla ni endpoint que escriba el stock a mano: se mueve con documentos, conteos y ajustes.

### INV-06 · Costo promedio ponderado

- **Cuando** entra mercadería **entonces** su valor se suma al del stock y el promedio es valor ÷ cantidad (10 a Bs 10
  + 10 a Bs 12 = 20 a Bs 11).
- **Cuando** sale (venta, venta al costo, consumo, ajuste) **entonces** sale al promedio del momento redondeado al
  centavo, que es el costo de la línea (POS-05, T-07). En una venta al costo, ese es el precio (CON-02), también el de
  un empaque: equivalencia × promedio.
- **Cuando** el stock está en cero o negativo y entra mercadería **entonces** la entrada fija el promedio: antes, una
  fila «Ajuste del promedio» lleva lo que había al costo de la entrada (−2 u a Bs 8 y entran 12 a Bs 10: ajuste −4,00;
  saldo 10 u a Bs 10).
- **Cuando** un producto controla stock y todavía no tiene promedio **entonces** sus salidas van al último costo.
- **Cuando** se cambia el costo a mano (CAT-07) o se acepta la alerta de una compra (COM-03) **entonces** cambia el
  último costo, no el promedio. El formulario lo llama «Último costo» y muestra el promedio aparte.

### INV-07 · Costo puesto en tienda

| PED con envío Bs 15,00 | Subtotal | Parte del envío | Valor que entra |
| --- | --- | --- | --- |
| A: 10 × Bs 10 | 100,00 | 10,00 | 110,00 |
| B: 10 × Bs 5 | 50,00 | 5,00 | 55,00 |

- **Cuando** un pedido entra al stock **entonces** su envío, lo pague la tienda o una persona (DEU-02), se reparte entre
  sus líneas activas según su subtotal, al centavo; la línea más grande toma el resto del redondeo.
- **Cuando** cambia el envío o el costo de un pedido ya entregado **entonces** la diferencia de valor va a lo que queda
  del producto o, si no queda nada, al costo de ventas (la fila lo dice: «Al costo de ventas: Bs 5,00»).

### INV-14, INV-15 · Pedidos

- **Cuando** un pedido se registra entregado o pasa a Entregado **entonces** cada línea entra en unidad base (COM-29),
  con su valor y su parte del envío. La consignación entra igual (INV-15).
- **Cuando** un pedido entregado vuelve a Pendiente o se anula **entonces** sale exactamente lo que entró, aunque el
  stock quede negativo; la pantalla lo avisa antes de guardar.
- **Cuando** se edita un pedido entregado (COM-11) **entonces** solo se mueve la diferencia de cada línea: la cantidad al
  costo de la línea y el resto como cambio de valor; una línea quitada sale y una agregada entra.
- **Cuando** un pedido está pendiente **entonces** Existencias muestra lo que trae como «por llegar».
- **Cuando** se edita un pedido entregado antes de que su producto empezara a controlar stock **entonces** esas líneas
  no se mueven (ya estaban en el conteo); sí entra una línea agregada en la edición.

### CON-04, INV-02, INV-09, INV-12 · Ventas y consumos

- **Cuando** se registra una venta, una venta al costo o un consumo **entonces** cada línea sale en unidad base: un
  empaque, cantidad × equivalencia (2 Caja ×12 = 24 u); un producto por kilo, sus kilos con 3 decimales.
- **Cuando** se anula **entonces** vuelve al stock lo que salió, al costo con que salió.
- **Cuando** la línea es libre o de un producto de terceros **entonces** no mueve stock. **Cuando** se asigna una línea
  sin identificar (POS-12) **entonces** sale en ese momento, al promedio de ese momento.

### INV-10 · Más de lo que hay

- **Cuando** una venta, venta al costo o consumo lleva de un producto que controla stock más de lo que hay **entonces**
  la pantalla pregunta «No hay stock registrado de <producto> (quedan N). ¿Vender igual?». Confirmada, se registra
  marcada y el producto queda «Stock negativo, por revisar» mientras su stock sea negativo.
- **Cuando** llega al servidor sin confirmar **entonces** responde 409 con ese mensaje, y la pantalla hace la misma
  pregunta (el stock pudo cambiar desde que se cargó el catálogo).
- Un producto «Por contar» no pregunta.

### INV-11 · «Quedan N» en el POS

- **Cuando** se busca un producto que controla stock, o está en una línea de la venta, o en los productos rápidos
  **entonces** se ve «Quedan N» (kilos con 3 decimales), con el stock al abrir la venta.

### INV-02 · Ajustes a mano

| Motivo | Mueve |
| --- | --- |
| Merma, Rotura sin nadie que pague, Vencimiento | Sale al promedio |
| Sobrante | Entra al promedio |

- **Cuando** se registra un ajuste desde el Kardex **entonces** lleva motivo, cantidad en unidad base, comentario
  opcional y quién y cuándo. La rotura que alguien paga sigue siendo venta al costo (P-39).
- Un producto «Por contar» no se ajusta: se cuenta.

### INV-05, INV-18 · Conteo y arranque

Ejemplo: Barra Chia, 37 u en stock, Caja ×12.

| Paso | Qué pasa |
| --- | --- |
| Se escribe «3 Caja ×12 + 4» | Contado 40; esperado 37, tomado al guardar |
| Se venden 2 antes de confirmar | Stock 35; la diferencia sigue siendo +3 |
| Se confirma | Ajuste por conteo +3 al promedio: stock 38 |

- **Cuando** se empieza un conteo **entonces** es de una categoría (lista sus productos que controlan stock) o de
  productos sueltos; en ambos se agregan productos con el lector, la cámara o buscándolos.
- **Cuando** se cuenta **entonces** es a ciegas: no se ve el stock ni lo esperado. Cada producto se escribe en empaques
  enteros y sueltas; el lector o la cámara suman una caja con el código del empaque y una suelta con el del producto.
- **Cuando** se guarda la cantidad de un producto **entonces** queda lo esperado en ese momento, con quién y cuándo; se
  puede volver a escribir mientras el conteo esté abierto.
- **Cuando** un producto ya está en otro conteo abierto **entonces** «'<producto>' ya está en el Conteo #N, que sigue
  abierto.»
- **Cuando** se revisa **entonces** se ve por producto lo contado, lo esperado y la diferencia en unidades y Bs, y
  cuántos de la categoría quedaron sin contar (no cambian).
- **Cuando** se confirma **entonces** cada diferencia es un «Ajuste por conteo» al promedio, con quién y cuándo, y el
  conteo queda cerrado. «Descartar» lo cierra sin mover nada.
- **Cuando** el producto estaba «Por contar» **entonces** su línea es la carga inicial: lo contado al último costo de ese
  día, que pasa a ser su promedio, menos lo vendido o consumido entre que se escribió y la confirmación. Desde ahí
  controla stock (INV-18). El arranque es por categorías: se cuentan una a una.

### Permisos (P-33)

| Pantalla | Permiso | Operador | Administrador |
| --- | --- | --- | --- |
| Inventario · Existencias | `INVENTORY_STOCK` | Sí | Sí |
| Inventario · Kardex (y sus ajustes) | `INVENTORY_KARDEX` | Sí | Sí |
| Inventario · Conteo | `INVENTORY_COUNTS` | Sí | Sí |

«Quedan N» se lee con el permiso del POS o de Consumos. El orden de bloqueo de T-14 suma el stock de los productos al
final, todos juntos y por id.

## Modelo de datos (migración `V38__product_packaging.sql`)

- `product_presentations`: `weight_kg` pasa a `base_quantity`; nueva `sold BOOLEAN NOT NULL DEFAULT TRUE`.
- `sale_items` y `consumption_items`: `presentation_weight_kg` y `weight_kg` pasan a `presentation_base_quantity` y
  `base_quantity`.
- `purchase_items`: `presentation_id`, `presentation_name`, `presentation_base_quantity` y `base_quantity`, los cuatro
  juntos, como en las ventas.
- `product_barcodes`: `presentation_id NULL`: el código es de ese empaque. El índice único de los códigos activos
  vale para productos y empaques.
- `box_product_reviews`: la caja revisada (única), su producto base, el empaque creado, la decisión (`CONVERTED` o
  `DISCARDED`) y quién y cuándo.

### Núcleo (migración `V39__inventory_core.sql`)

- `products.stock_controlled`: «Controla stock» (falso en los especiales).
- `product_stocks`: por producto, cantidad, valor, promedio y `started_at` (nulo: por contar o no controla).
- `stock_movements`: el kardex; tipo, cantidad, costo unitario, valor, valor al costo de ventas, saldos después de la
  fila, referencia, motivo, comentario, la línea de venta, consumo, pedido o conteo que la generó, quién y cuándo.
- `stock_counts` y `stock_count_lines`: el conteo, y por producto lo contado, cómo se escribió, lo esperado al
  escribirlo, si es carga inicial y quién y cuándo.
- `sales.negative_stock_confirmed` y `consumptions.negative_stock_confirmed`: INV-10.

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/products`, `PUT /api/products/{id}` | `presentations` admite ahora `baseQuantity`, `sold` y `barcode`, por unidad y por kilo. |
| `GET /api/products` y `/{id}` | Cada empaque trae `baseQuantity`, `sold`, `barcode`, `price` (null si no se vende) y `cost`. |
| `POST /api/sales`, `POST /api/consumptions` | `presentationId` vale para un empaque que se vende; las líneas responden `presentationBaseQuantity` y `baseQuantity`. |
| `POST /api/purchases`, `PUT /api/purchases/{id}` | Cada línea acepta `presentationId`; responde el empaque, `baseQuantity` y `baseUnitCost`. |
| `GET /api/products/box-proposals` | COM-10: las cajas por revisar, con su producto base, nombre, equivalencia y precio propuestos. Permiso `PRODUCTS`. |
| `POST /api/products/{id}/box-conversion` | COM-10: confirma la caja `{id}` con nombre, equivalencia, precio y código. Permiso `PRODUCTS`. |
| `POST /api/products/{id}/box-discard` | COM-10: descarta la caja `{id}`. Permiso `PRODUCTS`. |
| `GET /api/inventory/stock?categoryId=` | INV-01, INV-14: Existencias, con lo que está por llegar. `INVENTORY_STOCK`. |
| `GET /api/inventory/levels` | INV-11: el stock de lo que controla stock. `POS`, `CONSUMPTIONS` o `INVENTORY_STOCK`. |
| `GET /api/inventory/products/{id}/kardex?from=&to=` | INV-03. `INVENTORY_KARDEX`. |
| `POST /api/inventory/products/{id}/adjustments` | INV-02: ajuste con motivo. `INVENTORY_KARDEX`. |
| `GET`, `POST /api/inventory/counts`; `GET /api/inventory/counts/{id}` | INV-05: conteos, a ciegas. `INVENTORY_COUNTS`. |
| `PUT /api/inventory/counts/{id}/lines/{productId}` | INV-05: lo contado de un producto, en empaques y sueltas. |
| `GET /api/inventory/counts/{id}/review`, `POST …/confirm`, `POST …/discard` | INV-05, INV-18: diferencias, ajuste o carga inicial, descarte. |
| `POST /api/sales`, `POST /api/consumptions` | INV-10: `allowNegativeStock`; sin él, 409 `Insufficient Stock` con el mensaje. |
| `GET /api/products` | Cada producto trae `stockControlled`, `stockStatus`, `stock`, `averageCost` y `cost` (lo que cuesta una línea ahora). |

## Tareas

### 1. Empaques

- [x] Migración `V38__product_packaging.sql`.
- [x] Empaques por unidad y por kilo, «se vende» y código (`ProductPresentationService`, `ProductBarcodeService`).
- [x] Ventas y consumos con empaques por unidad y cantidad base; reportes con `base_quantity`.
- [x] Compras por empaque y costo por unidad base (`PurchaseServiceImpl`).
- [x] Cajas a empaques (`BoxProductConversionService`, `BoxProductConversionController`, página `/products/boxes`).
- [x] Frontend: sección Empaques en Productos; filas de cajas, escaneo de empaques y selector en el POS; Consumos;
  detalle de venta, consumo y pedido; empaque por línea en Compras; «Cajas a empaques».
- [x] Pruebas de las reglas de `trabajos/102-inventario-empaques.md` de Foundry y contratos regenerados:
  `ProductPresentationServiceTest`, `SaleServiceTest`, `PurchaseServiceTest`, `SaleRepositoryTest`,
  `PackagingIntegrationTest`, `EndpointAuthorizationTest` y, en el frontend, `sale-catalog`, `register-sale`,
  `barcode-scanner`, `presentation-picker`, `products`, `purchase-form-modal` y `box-conversion`.
- [ ] Publicada la V2, revisar las cajas de producción en «Cajas a empaques» antes del conteo (Próximos pasos de
  `requirements.md`).

### 2. Núcleo

- [x] Migración `V39__inventory_core.sql`.
- [x] Kardex valorado y promedio (`InventoryLedger`), movimientos desde ventas, consumos y pedidos
  (`InventoryServiceImpl`), conteo y carga inicial (`StockCountServiceImpl`), Existencias, Kardex y ajustes
  (`InventoryController`).
- [x] Frontend: grupo Inventario (Existencias, Kardex, Conteo con lector y cámara), «Quedan N» y «¿Vender igual?» en el
  POS y en Consumos, «Controla stock» y «Último costo» en Productos, aviso de stock en la entrega y la anulación de
  pedidos.
- [x] Pruebas de las reglas de `trabajos/105-inventario-nucleo.md` de Foundry: `InventoryIntegrationTest`,
  `StockCountIntegrationTest`, `EndpointAuthorizationTest` y, en el frontend, `inventory.models`, `count-detail`,
  `sale-catalog`, `cart-line`, `register-sale` y `consumption-form-modal`.
- [ ] Publicada la V2, antes del conteo: revisar «Cajas a empaques» con el catálogo de producción.

### 3. Bonificaciones y devoluciones · 4. Reposición y análisis

Cada uno agrega aquí sus escenarios cuando empieza.
