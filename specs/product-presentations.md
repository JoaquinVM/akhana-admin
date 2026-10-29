# Presentaciones de productos por peso

Detalle de CAT-11 (sección 3.2 de `specs/requirements.md`), que amplía la venta por peso de CAT-08 y POS-10.
Decidido con el dueño el 26-10-2026 al revisar la importación de productos (CAT-06), que queda en pausa hasta
tener esto: la importación creará las presentaciones de los frutos secos directamente.

Moneda Bs, montos con escala 2 `HALF_UP`; pesos en kilos con hasta 3 decimales (al gramo).

Hecho el 26-10-2026 en la rama `feat/product-presentations`: migración `V15__product_presentations.sql`,
`ProductPresentationService`, ventas, consumos y reportes con presentación, selector compartido
`shared/components/presentation-picker` (POS en un modal, Consumos en línea) y sección Presentaciones en el
formulario de Productos. Probado en la app el 28-10-2026 con la Nuez pelada real de la planilla (ver Tareas).

## Por qué

En la planilla, cada fruto seco tiene un precio por gramo y además tres productos por presentación con precio
redondeado: Nuez pelada a 0,21 Bs/g y Nuez pelada 1/4, 1/2 y 1 libra a Bs 23, 45 y 90. Con un solo precio por
kilo (210) la 1/2 libra daría 0,227 × 210 = Bs 47,67, no los 45 que se cobran hoy. Tener las presentaciones como
productos por unidad aparte obliga a mantener a mano 42 costos y precios y, en la V2, no descontaría kilos del
stock. Con presentaciones dentro del producto por kilo, el costo se calcula solo y el stock queda en kilos.

## CAT-11 · Presentaciones en el catálogo

Una presentación pertenece a un producto que se vende por kilo y tiene nombre, peso fijo y precio fijo opcional.

| Nuez pelada (costo 130,00 / kg, precio 210,00 / kg) | Peso | Precio fijo | Precio en el POS | Costo calculado |
| --- | --- | --- | --- | --- |
| 100 g | 0,100 | — | 21,00 | 13,00 |
| 1/4 libra | 0,113 | 23,00 | 23,00 | 14,69 |
| 1/2 libra | 0,227 | 45,00 | 45,00 | 29,51 |
| 1 libra | 0,454 | 90,00 | 90,00 | 59,02 |

- **Cuando** el producto se vende por Kilo **entonces** el formulario de Productos muestra la sección
  «Presentaciones», editable con el permiso `PRODUCTS`; con Unidad no se muestra.
- **Cuando** se marca un producto como «Se vende por: Kilo» al crearlo o editarlo **entonces** el formulario
  propone 100 g (0,100) y 1/2 libra (0,227) sin precio fijo; se pueden editar o quitar antes de guardar.
- **Cuando** una presentación no tiene precio fijo **entonces** su precio es peso × precio por kilo, redondeado
  a 2 decimales (100 g de Nuez: 0,100 × 210 = 21,00); si lo tiene, se usa el fijo (1/2 libra: 45,00).
- **Cuando** se muestra o se usa el costo de una presentación **entonces** es siempre peso × costo por kilo del
  producto; no se guarda en la presentación, así que una compra que cambia el costo (COM-03) lo actualiza.
- **Cuando** falta el nombre, pasa de 50 caracteres o ya existe otra presentación activa con ese nombre en el
  producto (sin distinguir mayúsculas) **entonces** «El nombre de la presentación es obligatorio.», «… no debe
  superar los 50 caracteres.» o «Ya existe la presentación '<nombre>' en este producto.»
- **Cuando** el peso falta, es 0 o negativo, tiene más de 3 decimales o se repite en otra presentación activa
  del producto **entonces** «El peso debe ser mayor a 0 kg.», «El peso admite hasta 3 decimales (gramos).» o «Ya
  existe una presentación de <peso> kg en este producto.»
- **Cuando** el precio fijo es 0, negativo o tiene más de 2 decimales **entonces** «El precio fijo debe ser mayor
  a 0.» o «El precio fijo admite hasta 2 decimales.»
- **Cuando** el precio (fijo o calculado) es menor al costo calculado **entonces** se guarda con el aviso «La
  presentación '<nombre>' se vende a pérdida.» (CAT-02).
- **Cuando** se quiere guardar una séptima presentación activa **entonces** «Un producto admite hasta 6
  presentaciones activas.»
- **Cuando** se quita una presentación **entonces** se desactiva, no se borra: las ventas y consumos que la usaron
  la siguen mostrando, y no aparece más en el POS ni en Consumos. Una inactiva se puede reactivar si no choca con
  el nombre o peso de otra activa.
- **Cuando** un producto pasa de Kilo a Unidad **entonces** sus presentaciones activas se desactivan; si vuelve a
  Kilo, el formulario las muestra inactivas para reactivarlas.
- **Cuando** se cambia el precio por kilo de un producto con presentaciones de precio fijo **entonces** se guarda
  y se avisa: «Estas presentaciones tienen precio fijo y no cambian: 1/4 libra (Bs 23,00), 1/2 libra (Bs 45,00),
  1 libra (Bs 90,00).»
- **Cuando** se crea, cambia, desactiva o reactiva una presentación **entonces** queda quién y cuándo (auditoría
  del servidor).
- **Cuando** cambia el precio fijo de una presentación (de un valor a otro, o se pone o se quita) **entonces**
  queda una fila en `product_price_changes` con origen `CATALOG`, la presentación y el precio fijo anterior y
  nuevo (vacío = sin precio fijo); el costo y precio del producto se copian sin cambios.

## CAT-11 · Elegir la presentación en el POS (amplía POS-10)

- **Cuando** se elige un producto por kilo en la búsqueda o en los productos rápidos **entonces** se abre un
  selector con sus presentaciones activas de menor a mayor peso, cada una con nombre y precio, y al final «Otro
  peso»: `[100 g · 21,00] [1/4 libra · 23,00] [1/2 libra · 45,00] [1 libra · 90,00] [Otro peso…]`.
- **Cuando** se elige «1/2 libra» **entonces** entra al carrito la línea «Nuez pelada · 1/2 libra», cantidad 1,
  Bs 45,00. La cantidad de una línea con presentación es entera (bolsitas) y +/− suman o restan una.
- **Cuando** se elige otra vez la misma presentación del mismo producto **entonces** suma 1 a la línea existente.
- **Cuando** se quiere otra presentación **entonces** se quita la línea y se elige de nuevo; la presentación no se
  cambia dentro de la línea.
- **Cuando** se elige «Otro peso» **entonces** la línea funciona como hoy: cantidad en kilos con hasta 3
  decimales, precio por kilo y +/− de 100 g.
- **Cuando** el producto por kilo no tiene presentaciones activas **entonces** no se abre el selector y entra
  directo como «Otro peso».
- **Cuando** se vende **entonces** el servidor vuelve a calcular precio y costo desde el catálogo; si la
  presentación ya no está activa, «La presentación '<nombre>' de '<producto>' ya no está disponible.»
- Los botones fijos «100 g» y «1/2 libra» de la línea (POS-10) desaparecen: sus pesos pasan a ser presentaciones
  del producto.

## CAT-11 · Qué guarda la línea de venta

Ejemplo: 2 × Nuez pelada 1/2 libra, costo 130,00 / kg.

| Campo | Valor |
| --- | --- |
| Producto (copia de nombre y código) | Nuez pelada, FSE-NUE |
| Presentación (id y copia de nombre y peso) | 1/2 libra, 0,227 kg |
| Cantidad | 2 |
| Kilos (`weight_kg`) | 0,454 |
| Precio unitario | 45,00 |
| Subtotal | 90,00 |
| Costo unitario (`unit_cost`, por kilo) | 130,00 |
| Costo de la línea (kilos × costo por kilo) | 59,02 |

- **Cuando** la línea tiene presentación **entonces** kilos = cantidad × peso de la presentación, y el costo de la
  línea es kilos × `unit_cost`, redondeado a 2 decimales.
- **Cuando** la línea es «Otro peso» o de un producto por unidad **entonces** no tiene presentación ni kilos, y
  todo sigue como hoy (cantidad × `unit_cost`).
- **Cuando** después se cambia el nombre, peso o precio de la presentación **entonces** las ventas anteriores no
  cambian (copia al vender, como POS-05).
- **Cuando** la línea tiene descuento por unidad (POS) **entonces** se aplica a la bolsita como en cualquier
  línea: 2 × (45,00 − 5,00) = 80,00.
- **Cuando** se anula la venta **entonces** no hay nada distinto: la línea queda como estaba.

## CAT-11 · Consumos, venta al costo y compras

- **Cuando** se registra un consumo de un producto por kilo **entonces** se usa el mismo selector; una bolsita de
  1/2 libra de Nuez queda con kilos 0,227 y subtotal 0,227 × 130 = 29,51. El detalle muestra «Nuez pelada · 1/2
  libra, 1 (0,227 kg)».
- **Cuando** la venta es al costo (CON-02) **entonces** el precio de cada bolsita es peso × costo por kilo
  (29,51), no el precio fijo: se ignora el precio fijo de la presentación.
- **Cuando** se registra una compra **entonces** no hay presentaciones: la línea sigue en kilos y costo por kilo,
  que es lo que actualiza el costo del producto (COM-03).

## CAT-11 · Reportes y detalle

- **Cuando** un reporte suma cantidad (ventas por día, por proveedor, consumos al costo, totales y su Excel)
  **entonces** una línea con presentación suma sus kilos (0,454), no las bolsitas; las demás suman su cantidad
  como hoy. Total, costo y utilidad no cambian de significado.
- **Cuando** se ve el detalle de una venta o consumo **entonces** la línea con presentación muestra «Nuez pelada ·
  1/2 libra» y «2 × Bs 45,00 (0,454 kg)»; la de «Otro peso», «Nuez pelada» y «0,300 kg × Bs 210,00».
- Ventas por presentación («¿cuántas medias libras se vendieron?») no entra en la V1 (V3, reportes avanzados).

## Datos existentes

- **Cuando** se aplica la migración **entonces** cada producto que ya se vende por kilo recibe 100 g (0,100) y
  1/2 libra (0,227) sin precio fijo, que reemplazan a los botones fijos. No hay datos de producción (T-01).

## Modelo de datos (migración `V15__product_presentations.sql`)

- `product_presentations`: id, `product_id`, `name VARCHAR(50)`, `weight_kg NUMERIC(12,3) CHECK > 0`,
  `fixed_price NUMERIC(12,2) NULL CHECK > 0`, `active`, auditoría (creado y modificado por y cuándo). Índices
  únicos parciales por producto en `lower(name)` y en `weight_kg` donde `active`.
- `sale_items` y `consumption_items`: `presentation_id NULL`, `presentation_name VARCHAR(50) NULL`,
  `presentation_weight_kg NUMERIC(12,3) NULL`, `weight_kg NUMERIC(12,3) NULL`, con un `CHECK` de que los cuatro
  van juntos.
- `product_price_changes`: `presentation_id NULL`, `previous_fixed_price` y `new_fixed_price NUMERIC(12,2) NULL`.

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/products`, `PUT /api/products/{id}` | `presentations` opcional en el cuerpo: la lista completa del formulario (sin `id` = nueva, `active: false` = desactivar, lo que falta se desactiva); sin el campo se mantienen. Se guarda en la misma transacción que el producto. La respuesta trae `warnings` (venta a pérdida, precios fijos que no cambian). |
| `GET /api/products` y `/{id}` | Traen `presentations` (activas e inactivas, de menor a mayor peso) con `price` y `cost` calculados; el POS y Consumos usan solo las activas. |
| `POST /api/sales`, `POST /api/consumptions` | Cada línea acepta `presentationId` opcional; con él, `quantity` es entera. Las líneas de la respuesta traen `presentationId`, `presentationName`, `presentationWeightKg` y `weightKg`. |

Las presentaciones van dentro del producto y no en rutas aparte: el formulario guarda producto y presentaciones
juntos, y el aviso de precios fijos depende del precio por kilo que se guarda en el mismo paso.

## Tareas

- [x] Migración `V15__product_presentations.sql` (tabla, columnas en líneas y en `product_price_changes`,
  presentaciones 100 g y 1/2 libra para los productos por kilo existentes).
- [x] `ProductPresentationService`: reglas del catálogo, desactivar al pasar a Unidad, aviso de precio fijo al
  cambiar el precio por kilo, historial en `product_price_changes`.
- [x] `SaleServiceImpl` y `ConsumptionServiceImpl`: líneas con presentación (precio, kilos, costo, venta al costo,
  presentación inactiva).
- [x] Reportes: sumar `COALESCE(weight_kg, quantity)` en cantidad y costo (`SaleRepository` y consultas de
  consumos).
- [x] Pruebas: `ProductPresentationServiceTest`, `SaleServiceTest`, `ConsumptionServiceTest` y
  `SaleRepositoryTest` (kilos y costo contra PostgreSQL) con la Nuez del ejemplo. No hay rutas nuevas, así que
  `EndpointAuthorizationTest` no cambia.
- [x] Frontend: sección Presentaciones en el formulario de Productos; selector en el POS (`register-sale`, sin
  tocar `pos.component.html`) y en el formulario de Consumos; detalle de venta y consumo; botones fijos quitados;
  pruebas Vitest en `register-sale`, `consumption-form-modal` y `products`.
- [x] Probar en la app con un producto real (28-10-2026, rama `feat/app-checks`): Nuez pelada `FSE-NUE` con los
  valores de la planilla (0,13 y 0,21 Bs/g = 130 y 210 por kilo) y 1/4, 1/2 y 1 libra a Bs 23, 45 y 90. El
  formulario propuso 100 g y 1/2 libra y calculó precio y costo como la tabla de arriba; el selector del POS las
  ordenó por peso; 2 × 1/2 libra entró como una línea de Bs 90,00 (0,454 kg) y «Otro peso» 0,300 kg a Bs 63,00
  (VTA-000137); la venta al costo de 1/2 libra cobró Bs 29,51 (VTA-000138); el consumo guardó 1 u (0,227 kg) a
  Bs 29,51 (CON-000050); ventas por día sumó 0,454 + 0,300 kg y el costo 59,02 + 39,00. Hallazgos corregidos:
  - El detalle de venta mostraba la línea de «Otro peso» como «0.3» y «Bs 210.00», sin kilos: la línea de la
    respuesta trae ahora `saleUnit` del producto (como Consumos) y el detalle muestra «0.300 kg» y «Bs 210.00/kg».
    Pruebas en `SaleServiceTest` y `sale-detail-modal`.
  - El listado de Productos mostraba el precio por kilo sin unidad: ahora «Bs 210.00/kg» y «Bs 130.00/kg».
- [x] Actualizar `specs/sales.md` (POS-10) y `specs/requirements.md` (CAT-08, CAT-11, POS-10, tablero).
- [ ] Retomar `specs/product-import.md` (rama `feat/product-import`): las filas de libras de frutos secos pasan a
  ser presentaciones de su producto base.


## Notas de implementación

Detalle de cómo se construyó cada requerimiento, movido desde `requirements.md` el 29-10-2026 sin cambios.

- **CAT-07** · V1 — El operador puede crear productos y cambiar el costo (precio de compra) de un producto existente. También puede cambiar el precio de venta, desde el catálogo o al aceptar el precio sugerido en una compra (COM-03). Todo cambio de costo o precio queda auditado. Desde el 25-10-2026 los cambios desde una compra se registran en `product_price_changes` (origen `PURCHASE`). Desde el 27-10-2026 también los del catálogo (origen `CATALOG`): `ProductServiceImpl` guarda una fila al crear un producto (costo y precio iniciales, con anterior vacío; migración `V16__catalog_price_changes.sql` quita el `NOT NULL` de `previous_buy_price` y `previous_sell_price`) y otra al editarlo solo si cambia el costo o el precio de venta, en la misma transacción, con quién y cuándo. Los precios fijos de presentaciones siguen con su propia fila (CAT-11). La importación (CAT-06) no registra filas: el producto importado guarda quién y cuándo lo creó. Todavía no hay pantalla que muestre este historial. Pruebas en `ProductServiceTest`.
- **CAT-08** · V1 — Productos vendidos por peso: precio por kilo, para frutos secos y productos del proveedor Granel (P-31), y presentaciones rápidas (100 g = 0,100 kg; 1/2 libra = 0,227 kg); cantidades con hasta 3 decimales en ventas, compras y consumos. Cada producto indica cómo se vende en `products.sale_unit` (`UNIT` o `KILOGRAM`, por defecto `UNIT`; migración `V7__product_sale_unit.sql`), editable en el formulario de Productos («Se vende por»). Si es `KILOGRAM`, el precio de venta es Bs por kilo y la cantidad son kilos; un producto por unidad solo acepta cantidades enteras (el servidor rechaza los decimales con «La cantidad de '<producto>' debe ser entera: se vende por unidad.»). Las presentaciones rápidas fijas pasan a ser presentaciones configurables por producto (CAT-11).
