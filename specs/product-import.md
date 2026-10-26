# Importación de productos

Detalle de CAT-06 (sección 3.2 de `specs/requirements.md`), con la venta por peso de CAT-08 y las presentaciones
de CAT-11 (`specs/product-presentations.md`). Carga el catálogo
desde la hoja Productos de *Akhana Recaudaciones* antes de la salida (compuerta de la fase 1: «productos con
unidad de venta y precio por kilo revisados»). Se usa pocas veces: una carga inicial y, si hace falta, cargas
de las filas corregidas.

Moneda Bs, costo y precio con escala 2 `HALF_UP`. La importación solo crea: nunca cambia ni borra un producto,
categoría, proveedor o etiqueta que ya exista.

## Lo que muestra la planilla (validación del 26-10-2026)

Hoja Productos: 879 filas de datos (no ~1.200 como decía P-02), columnas Grupo, Proveedor, Etiqueta, Nombre,
Codigo, Costo, Precio Venta, Utilidad, Utilidad %. Hay 3 grupos reales (Nutricion 667, Estetica 132, Suplemento
78), 95 proveedores y 19 etiquetas (una por fila como máximo; 432 filas sin etiqueta).

| Caso real | Filas | Qué hará la importación |
| --- | --- | --- |
| Sin código (Oleo Primula Capsulas, Konjac 138 cap) | 15 | Rechazada: «Falta el código.» |
| Sin precio de venta (Crema Facial Rosa Mosqueta, Toronjil) | 24 más (34 en total con las sin código) | Rechazada: «Falta el precio de venta.» |
| Costo 0 (Lip care frutos rojos, jabon regenerante, Arcilla 8g) | 3 | Se importa con el aviso «Costo 0: toda la venta cuenta como utilidad.» Son bonificaciones sin costo conocido; cuando se compren, COM-03 propone el costo real (decidido el 26-10-2026; CAT-01 pasa a aceptar costo 0). |
| Frutos Secos base con precio por gramo (Arandano: costo 0,095, precio 0,14) y granolas Miranda Fruit (0,075 / 0,1) | 16 | Rechazada mientras «Se vende por» no diga Gramo: «Precio menor a Bs 1: si se vende por peso, marcar en Se vende por Gramo, 100 g o Kilo.» Con Gramo, la importación convierte a Bs por kilo sin tocar la planilla: Arandano queda costo 95,00 y precio 140,00 por kilo. |
| Granel en 100 g (Psylium 100gr a Bs 25, Espirulina 100gr) | 10 | Se importan por unidad salvo que «Se vende por» diga 100 g (precio × 10 = Bs por kilo: Psylium a 250,00) (P-31: lo marca el dueño antes de migrar). |
| Presentaciones de frutos secos (Arandano 1/4, 1/2 y 1 libra, códigos `-CLI`, `-MLI`, `-1LI`; filas 812 a 853) | 42 | No son productos: cada fila es una presentación de su producto base (`FSE-ARA-MLI` → 1/2 libra de `FSE-ARA`) con su Precio Venta como precio fijo (Pistacho 1/2 libra a Bs 63). Los 14 productos base tienen las tres. Mientras su base no esté marcada Kilo, se rechazan junto con ella. |
| Código repetido (`COR-QUI-HOC-70GR` en Quintos Hojuela y Quintos Rulito; `VIL-SHA-ROM` dos veces igual) | 2 | Se importa la primera; la siguiente se rechaza: «El código ya está en la fila 462.» |
| Mismo nombre, otro proveedor (Kefir Natural 2L de Sativa y de Belen; Cardo Mariano de 3 proveedores) | 14 | Se importa como «Kefir Natural 2L (Belen)» y se avisa (CAT-01 no admite nombres repetidos). |
| Mismo nombre y proveedor (Moringa y Guanabana de Desconocida 4, hoja y polvo) | 0 | Ya renombradas en la planilla (Moringa Hoja / Moringa Polvo, Guanabana Hoja / Guanabana Polvo, verificado el 26-10-2026): no chocan con la Moringa ni la Guanabana de Autentica. Si vuelve a pasar: «El nombre ya está en la fila <n>.» |
| Precio menor al costo | 0 | Se importa con aviso «Se vende a pérdida.» (CAT-02). Los 4 casos que había eran las cajas de Natuser (filas 274 a 277) con el precio de la barra suelta; el dueño puso el precio de la caja el 26-10-2026 (Barra Chia, Papaya y Mani Caja x12 a 44, Roquin Caja x24 a 88). Las cajas quedan como productos aparte; en la V2 se vinculan con la barra para «Abrir caja» (COM-10). |
| Grupo «Sativa» (Kefir Griego 800 gr, filas 31 y 32) | 2 | Corregido en la planilla el 26-10-2026: ahora son Nutricion. |
| Proveedores que solo cambiaban en mayúsculas (Alfavitamins y AlfaVitamins) | 18 | Corregido en la planilla el 26-10-2026: las 18 filas dicen Alfavitamins (95 proveedores distintos). Se sigue comparando sin distinguir mayúsculas ni espacios al borde, contra la planilla y contra los que ya existen en el sistema. |
| Columnas a la derecha (cálculos de libras, «Cambiar 100gr», fechas) y Utilidad / Utilidad % | — | Se ignoran: la utilidad la calcula el sistema (CAT-02). |
| Espacios al borde («Moringa ») | 2 | Se recortan. |

Con las reglas de abajo y la planilla tal como está hoy (sin la columna «Se vende por»): 780 productos importados (14
renombrados, 3 con costo 0) y 99 filas rechazadas (las 57
de la tabla y las 42 presentaciones, porque
sus 14 bases se rechazan por precio por gramo). Con los 14 frutos secos marcados Gramo: 794
productos y 42
presentaciones. Los valores de la hoja leída por la API vienen redondeados por el formato de la celda (0,095 se ve
0,10), por eso se importa el `.xlsx` con los valores reales y no lo que muestra la pantalla.

## Permiso y pantalla

- Botón **Importar** en la pantalla Productos, con el permiso de la pantalla (`PRODUCTS`): lo usan el operador y
  el administrador, porque los permisos son por pantalla (P-33). No hay permiso nuevo. **Cuando** un usuario sin
  `PRODUCTS` llama a `/api/products/import/**` **entonces** recibe 403.
- La importación tiene dos pasos en un modal: **Revisar** (sube el archivo y muestra el resultado sin guardar) e
  **Importar** (vuelve a validar el mismo archivo y guarda).

## CAT-06 · Archivo

- **Cuando** se sube un `.xlsx` (Archivo → Descargar → Microsoft Excel de la planilla) **entonces** se lee la
  hoja llamada Productos; si no existe, la primera hoja.
- **Cuando** el archivo no es `.xlsx`, pesa más de 5 MB o tiene más de 5.000 filas **entonces** «El archivo debe
  ser un .xlsx de hasta 5 MB y 5.000 filas.» y no se importa nada.
- **Cuando** la fila 1 no tiene las columnas Grupo, Proveedor, Nombre, Codigo, Costo y Precio Venta **entonces**
  «Faltan las columnas: <lista>.» Las columnas se buscan por nombre (sin distinguir mayúsculas ni tildes), no por
  posición; Etiqueta y Se vende por son opcionales; las demás se ignoran.
- **Cuando** una fila no tiene Grupo, Proveedor, Nombre ni Codigo **entonces** se salta sin contarla (filas
  vacías al final de la hoja).

## CAT-06 · Reglas por fila

Cada fila se valida sola y contra las anteriores del archivo y los productos existentes. Una fila rechazada no
frena a las demás.

- **Cuando** falta Grupo, Proveedor, Nombre, Codigo, Costo o Precio Venta **entonces** se rechaza con «Falta
  <columna>.»
- **Cuando** Costo o Precio Venta no es un número **entonces** «<columna> no es un número: '<valor>'.» Se aceptan
  números de la celda y texto con punto o coma decimal.
- **Cuando** Costo o Precio Venta tiene más de 2 decimales en su valor real (no el que muestra la celda)
  **entonces** «<columna> admite hasta 2 decimales: '<valor>'.» No se redondea nada (decidido el 26-10-2026; hoy
  solo pasa en las filas de costo por gramo, que no se rechazan si «Se vende por» dice Gramo: ahí se valida el
  valor convertido, ver CAT-08).
- **Cuando** Precio Venta es 0 o negativo **entonces** «Precio Venta debe ser mayor a 0.»; cuando Costo es
  negativo, «Costo no puede ser negativo.»
- **Cuando** Costo es 0 **entonces** se importa con el aviso «Costo 0: toda la venta cuenta como utilidad.» y
  utilidad porcentual 0 % (como el formulario desde este cambio de CAT-01).
- **Cuando** Nombre pasa de 150 caracteres o Codigo de 50 **entonces** el mismo mensaje que el formulario.
- **Cuando** el código ya existe en el catálogo (sin distinguir mayúsculas, incluidos inactivos y eliminados)
  **entonces** «Ya existe un producto con el código <código>.»; si se repite en una fila anterior del archivo,
  «El código ya está en la fila <n>.»
- **Cuando** el nombre ya existe (catálogo o fila anterior) con otro proveedor **entonces** se importa como
  «<nombre> (<proveedor>)» con el aviso «Nombre repetido: se guardó como …»; si ese nombre también existe, o el
  repetido es del mismo proveedor, se rechaza con «Ya existe un producto con el nombre <nombre>.» o «El nombre
  ya está en la fila <n>.»
- **Cuando** Precio Venta es menor que Costo **entonces** se importa con el aviso «Se vende a pérdida.» (CAT-02).
- **Cuando** Grupo no coincide con una categoría activa (sin distinguir mayúsculas ni espacios al borde)
  **entonces** se crea la categoría con color gris por defecto; lo mismo con Proveedor y Etiqueta. Una categoría,
  proveedor o etiqueta dada de baja no se reactiva: la fila se rechaza con «La categoría <nombre> está dada de
  baja.»
- **Cuando** Etiqueta está vacía **entonces** el producto queda sin etiquetas.
- **Cuando** la fila se importa **entonces** el producto queda Activo, sin descripción, con auditoría del
  administrador que importó (RG de auditoría).

## CAT-08 · Venta por peso

La planilla no se modifica: se sigue usando en paralelo en la fase 2 y sus columnas de libras calculan desde el
precio por gramo (decidido el 26-10-2026). El dueño solo agrega la columna «Se vende por», que dice cómo se vende
el producto y en qué unidad están Costo y Precio Venta; la importación convierte a Bs por kilo.

| Se vende por | Producto | Costo y Precio Venta de la planilla | Arandano (0,095 / 0,14) se importa como |
| --- | --- | --- | --- |
| vacía, Unidad o u | `UNIT` | por unidad | — |
| Gramo o g | `KILOGRAM` | por gramo: × 1.000 | 95,00 / 140,00 por kilo |
| 100 g | `KILOGRAM` | por 100 g: × 10 | — (Psylium 100gr a 25 → 250,00 por kilo) |
| Kilo o kg | `KILOGRAM` | por kilo, sin convertir | — |

- **Cuando** «Se vende por» tiene otro valor **entonces** «Se vende por debe ser Unidad, Gramo, 100 g o Kilo.»
  (sin distinguir mayúsculas, tildes ni espacios: «100g» y «100 gr» valen).
- **Cuando** se convierte **entonces** las reglas de número (mayor a 0, hasta 2 decimales) se aplican al valor ya
  convertido: 0,095 × 1.000 = 95,00 pasa; un precio por gramo con más de 5 decimales se rechaza con «<columna>
  convertido a Bs por kilo tiene más de 2 decimales: '<valor>'.»
- **Cuando** una fila por unidad tiene Precio Venta menor a Bs 1 **entonces** se rechaza con «Precio menor a
  Bs 1: si se vende por peso, marcar en Se vende por Gramo, 100 g o Kilo.» (protege de importar el precio por
  gramo de Frutos Secos como precio por unidad).
- **Cuando** una fila por peso queda, ya convertida, con Precio Venta menor a Bs 10 por kilo **entonces** se
  rechaza con «Precio por kilo menor a Bs 10: revisar Se vende por (¿Gramo o 100 g?).»
- **Cuando** se importa Arandano con Gramo (costo 95, precio 140 por kilo) **entonces** en el POS 100 g cuesta Bs 14,00 y
  1/2 libra (0,227 kg) Bs 31,78.

## CAT-11 · Presentaciones de frutos secos

Se reconocen por el final del código, sin distinguir mayúsculas. Nombre y peso son fijos, los mismos de CAT-11; el
Nombre de la fila solo se muestra en el resultado.

| Final del código | Presentación | Peso |
| --- | --- | --- |
| `-CLI` | 1/4 libra | 0,113 kg |
| `-MLI` | 1/2 libra | 0,227 kg |
| `-1LI` | 1 libra | 0,454 kg |

- **Cuando** el código termina en `-CLI`, `-MLI` o `-1LI` **entonces** la fila no crea un producto: crea esa
  presentación en el producto cuyo código es el mismo sin el final (`FSE-NUE-MLI` → `FSE-NUE`), con Precio Venta
  como precio fijo (Nuez pelada 1/2 libra a Bs 45). Se exigen Codigo y Precio Venta con las reglas de número de
  arriba (mayor a 0, hasta 2 decimales); Grupo, Proveedor, Etiqueta, Costo y Se vende por se ignoran.
- **Cuando** se calcula el costo de la presentación **entonces** es peso × costo por kilo del producto base
  (CAT-11), no el Costo de la fila: 1/2 libra de Nuez queda con costo 0,227 × 130 = 29,51 (la planilla dice 29,48
  porque usa 226,796 g).
- **Cuando** el producto base está en el archivo **entonces** no importa el orden: las presentaciones se resuelven
  después de leer todas las filas de productos.
- **Cuando** el producto base no está en el archivo **entonces** se rechaza con «No está el producto base
  <código> en el archivo.»; si ya existe en el catálogo, «El producto <código> ya existe: agregar la presentación
  desde Productos.» (la importación no cambia productos existentes, así que repetirla no duplica presentaciones).
- **Cuando** el producto base se rechaza **entonces** sus presentaciones se rechazan con «Se rechazó el producto
  base <código> (fila <n>).»
- **Cuando** el producto base se vende por unidad **entonces** «El producto base <código> se vende por unidad:
  marcar Kilo.»
- **Cuando** la misma presentación se repite para un producto **entonces** se importa la primera y la siguiente se
  rechaza: «La presentación 1/2 libra de <código> ya está en la fila <n>.»
- **Cuando** el precio fijo es menor al costo calculado **entonces** se importa con el aviso «La presentación
  '<nombre>' se vende a pérdida.» (CAT-11).
- **Cuando** se importa un producto Kilo **entonces** recibe además 100 g (0,100 kg) sin precio fijo, como
  propone el formulario (CAT-11), y 1/2 libra sin precio fijo si el archivo no trae su `-MLI`. Nuez pelada queda
  con 100 g (21,00), 1/4 libra (23,00), 1/2 libra (45,00) y 1 libra (90,00).
- **Cuando** después se importen las ventas históricas (FIN-11) **entonces** una línea con código `FSE-NUE-MLI` se
  carga como Nuez pelada con la presentación 1/2 libra (se anota aquí para FIN-11, no se hace en CAT-06).

## CAT-06 · Resultado

- **Cuando** se revisa o importa **entonces** se muestra: filas leídas, productos y presentaciones a importar (o
  importados), ya existentes, rechazadas y con aviso; categorías, proveedores y etiquetas nuevos; y la tabla de
  rechazadas y avisos con fila, código, nombre y motivo, filtrable por tipo.
- **Cuando** una fila se rechaza porque su código ya existe en el catálogo (o es una presentación de un producto
  que ya existe) **entonces** cuenta como «ya existente», no como rechazada: el resumen dice «794 ya estaban en el
  sistema» y la tabla no las muestra salvo que se elija el filtro «Ya existentes». No van en la descarga de
  rechazadas.
- **Cuando** se importa **entonces** todas las filas válidas se guardan en una sola transacción: si algo falla al
  guardar, no queda nada a medias. Las rechazadas no se guardan.
- **Cuando** el resultado tiene rechazadas **entonces** se puede descargar como `.xlsx` (fila, código, nombre,
  motivo) para corregir la planilla.
- **Cuando** se corrige la planilla y se vuelve a importar el archivo completo **entonces** las filas ya
  importadas salen como ya existentes y las corregidas se importan: la importación se puede repetir sin duplicar.
  Solo crea (decidido el 26-10-2026): un cambio de precio o costo de un producto ya importado se hace en Productos,
  no re-importando.

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/products/import/preview` (multipart `file`) | Valida y devuelve el resultado sin guardar. |
| `POST /api/products/import` (multipart `file`) | Valida de nuevo y guarda las filas válidas. |

Respuesta: `{ totalRows, importedCount, presentationCount, existingCount, rejectedCount, warningCount,
newCategories[], newSuppliers[], newTags[], rows: [{ rowNumber, code, name, status: IMPORTED | EXISTING | REJECTED
| WARNING, messages[] }] }`. Solo se devuelven en `rows` las ya existentes, rechazadas y con aviso. La descarga de rechazadas se arma en el frontend desde esa respuesta.

## Tareas

- [ ] CAT-01 acepta costo 0: `ProductRequest` (`buyPrice` mayor o igual a 0, mensaje «El precio de compra no
  puede ser negativo.»), aviso «Costo 0: toda la venta cuenta como utilidad.» en la respuesta y en el formulario de
  Productos; pruebas en `ProductServiceTest` y en el spec de `products`.
- [ ] `ProductImportService`: lectura del `.xlsx` con Apache POI (columnas por nombre), validación por fila con
  los mensajes de arriba, alta de categorías, proveedores y etiquetas, presentaciones con las reglas de
  `ProductPresentationService`, guardado en una transacción.
- [ ] `ProductImportController` con preview e import, con `@PreAuthorize` de `PRODUCTS`; prueba en `EndpointAuthorizationTest`.
- [ ] Pruebas: `ProductImportServiceTest` con un `.xlsx` armado en la prueba (filas reales de la tabla de arriba:
  Arandano Kilo con sus 1/4, 1/2 y 1 libra, presentación sin base o con base rechazada o por unidad, Psylium por
  unidad, sin código, sin precio, costo 0, código y nombre repetidos, a pérdida,
  proveedor en otra mayúscula, repetir la importación).
- [ ] Frontend: botón Importar en `pages/products`, modal con Revisar / Importar, resumen y tabla de rechazadas,
  descarga `.xlsx`; pruebas Vitest del servicio y del modal.
- [ ] Validar con el `.xlsx` real de la planilla: 780 productos y 99 rechazadas sin la columna «Se vende por», y 794 productos y 42 presentaciones con los 14 frutos secos en Gramo.
- [ ] Actualizar `specs/requirements.md` (CAT-06, P-02, tablero).
