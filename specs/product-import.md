# Importación de productos

Detalle de CAT-06 (sección 3.2 de `specs/requirements.md`), con la venta por peso de CAT-08. Carga el catálogo
desde la hoja Productos de *Akhana Recaudaciones* antes de la salida (compuerta de la fase 1: «productos con
unidad de venta y precio por kilo revisados»). Se usa pocas veces: una carga inicial y, si hace falta, cargas
de las filas corregidas.

Moneda Bs, costo y precio con escala 2 `HALF_UP`. La importación solo crea: nunca cambia ni borra un producto,
categoría, proveedor o etiqueta que ya exista.

## Lo que muestra la planilla (validación del 26-10-2026)

Hoja Productos: 879 filas de datos (no ~1.200 como decía P-02), columnas Grupo, Proveedor, Etiqueta, Nombre,
Codigo, Costo, Precio Venta, Utilidad, Utilidad %. Hay 3 grupos reales (Nutricion 667, Estetica 132, Suplemento
78), 96 proveedores y 19 etiquetas (una por fila como máximo; 432 filas sin etiqueta).

| Caso real | Filas | Qué hará la importación |
| --- | --- | --- |
| Sin código (Oleo Primula Capsulas, Konjac 138 cap) | 15 | Rechazada: «Falta el código.» |
| Sin precio de venta (Crema Facial Rosa Mosqueta, Toronjil) | 24 más (34 en total con las sin código) | Rechazada: «Falta el precio de venta.» |
| Costo 0 (Lip care frutos rojos, jabon regenerante, Arcilla 8g) | 3 | Rechazada: «El costo debe ser mayor a 0.» (como en el formulario, CAT-01) |
| Frutos Secos base con precio por gramo (Arandano: costo 0,095, precio 0,14) y granolas Miranda Fruit (0,075 / 0,1) | 16 | Rechazada mientras no se marque «Kilo» y se lleve a Bs por kilo: «Precio menor a Bs 1: si se vende por peso, marcar Kilo y poner el precio por kilo.» Con Kilo, Arandano queda costo 95,00 y precio 140,00 por kilo. |
| Granel en 100 g (Psylium 100gr a Bs 25, Espirulina 100gr) | 10 | Se importan por unidad salvo que la planilla los marque Kilo con precio por kilo (P-31: lo hace el dueño antes de migrar). |
| Presentaciones de frutos secos (Arandano 1/4, 1/2 y 1 libra, códigos `-CLI`, `-MLI`, `-1LI`) | 42 | Se importan como productos por unidad con su precio fijo (Pistacho 1/2 libra a Bs 63); hoy se venden así y las ventas históricas (FIN-11) usan esos códigos. Si el dueño pasa a vender solo por kilo, se desactivan después. |
| Código repetido (`COR-QUI-HOC-70GR` en Quintos Hojuela y Quintos Rulito; `VIL-SHA-ROM` dos veces igual) | 2 | Se importa la primera; la siguiente se rechaza: «El código ya está en la fila 462.» |
| Mismo nombre, otro proveedor (Kefir Natural 2L de Sativa y de Belen; Cardo Mariano de 3 proveedores) | 16 | Se importa como «Kefir Natural 2L (Belen)» y se avisa (CAT-01 no admite nombres repetidos). |
| Mismo nombre y proveedor (Moringa y Guanabana de Desconocida 4, hoja y polvo) | 2 | Rechazada: «El nombre ya está en la fila 619.» |
| Precio menor al costo (Barra Chia Caja x12: costo 25,50, precio 4) | 4 | Se importa con aviso «Se vende a pérdida.» (CAT-02); el precio es por barra y el costo por caja: corregir antes. |
| Grupo «Sativa» (2 filas) | 2 | Crea la categoría Sativa; probablemente debía ser Nutricion. |
| Proveedores que solo cambian en mayúsculas (AlfaVitamins y Alfavitamins) | 18 | Un solo proveedor (se compara sin distinguir mayúsculas y sin espacios al borde). |
| Columnas a la derecha (cálculos de libras, «Cambiar 100gr», fechas) y Utilidad / Utilidad % | — | Se ignoran: la utilidad la calcula el sistema (CAT-02). |
| Espacios al borde («Moringa ») | 2 | Se recortan. |

Con las reglas de abajo y la planilla tal como está hoy: 817 filas importadas (16 renombradas, 4 a pérdida) y 62
rechazadas. Los valores de la hoja leída por la API vienen redondeados por el formato de la celda (0,095 se ve
0,10), por eso se importa el `.xlsx` con los valores reales y no lo que muestra la pantalla.

## Permiso y pantalla

- Botón **Importar** en la pantalla Productos, solo para Administrador: permiso nuevo `PRODUCT_IMPORT`
  (tabla 2.2). **Cuando** un operador llama a `/api/products/import/**` **entonces** recibe 403 y no ve el botón.
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
- **Cuando** Costo o Precio Venta es 0 o negativo **entonces** «<columna> debe ser mayor a 0.»
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

- **Cuando** la columna «Se vende por» dice Kilo (o kg) **entonces** el producto queda `KILOGRAM` y Costo y
  Precio Venta se toman como Bs por kilo. Vacía, «Unidad» o «u» es `UNIT`; otro valor se rechaza con «Se vende
  por debe ser Unidad o Kilo.»
- **Cuando** una fila por unidad tiene Precio Venta menor a Bs 1 **entonces** se rechaza con «Precio menor a
  Bs 1: si se vende por peso, marcar Kilo y poner el precio por kilo.» (protege de importar el precio por gramo
  de Frutos Secos).
- **Cuando** una fila Kilo tiene Precio Venta menor a Bs 10 **entonces** se rechaza con «Precio por kilo menor a
  Bs 10: ¿es el precio por gramo o por 100 g?»
- **Cuando** se importa Arandano con Kilo, costo 95 y precio 140 **entonces** en el POS 100 g cuesta Bs 14,00 y
  1/2 libra (0,227 kg) Bs 31,78.

## CAT-06 · Resultado

- **Cuando** se revisa o importa **entonces** se muestra: filas leídas, a importar (o importadas), rechazadas y
  con aviso; categorías, proveedores y etiquetas nuevos; y la tabla de rechazadas y avisos con fila, código,
  nombre y motivo, filtrable por tipo.
- **Cuando** se importa **entonces** todas las filas válidas se guardan en una sola transacción: si algo falla al
  guardar, no queda nada a medias. Las rechazadas no se guardan.
- **Cuando** el resultado tiene rechazadas **entonces** se puede descargar como `.xlsx` (fila, código, nombre,
  motivo) para corregir la planilla.
- **Cuando** se corrige la planilla y se vuelve a importar el archivo completo **entonces** las filas ya
  importadas salen rechazadas por «Ya existe un producto con el código …» y las corregidas se importan: la
  importación se puede repetir sin duplicar.

## API

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/products/import/preview` (multipart `file`) | Valida y devuelve el resultado sin guardar. |
| `POST /api/products/import` (multipart `file`) | Valida de nuevo y guarda las filas válidas. |

Respuesta: `{ totalRows, importedCount, rejectedCount, warningCount, newCategories[], newSuppliers[], newTags[],
rows: [{ rowNumber, code, name, status: IMPORTED | REJECTED | WARNING, messages[] }] }`. Solo se devuelven en
`rows` las rechazadas y con aviso. La descarga de rechazadas se arma en el frontend desde esa respuesta.

## Tareas

- [ ] Permiso `PRODUCT_IMPORT` (solo Administrador) en backend y `navigation.config.ts`.
- [ ] `ProductImportService`: lectura del `.xlsx` con Apache POI (columnas por nombre), validación por fila con
  los mensajes de arriba, alta de categorías, proveedores y etiquetas, guardado en una transacción.
- [ ] `ProductImportController` con preview e import; prueba en `EndpointAuthorizationTest`.
- [ ] Pruebas: `ProductImportServiceTest` con un `.xlsx` armado en la prueba (filas reales de la tabla de arriba:
  Arandano Kilo, Psylium por unidad, sin código, sin precio, costo 0, código y nombre repetidos, a pérdida,
  proveedor en otra mayúscula, repetir la importación).
- [ ] Frontend: botón Importar en `pages/products`, modal con Revisar / Importar, resumen y tabla de rechazadas,
  descarga `.xlsx`; pruebas Vitest del servicio y del modal.
- [ ] Validar con el `.xlsx` real de la planilla: 817 importadas y 62 rechazadas sin marcar Kilo.
- [ ] Actualizar `specs/requirements.md` (CAT-06, P-02, tablero).
