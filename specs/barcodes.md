# Códigos de barras

Detalle de CAT-10 y POS-15 (`specs/requirements.md`). Códigos de fábrica y lector USB en la pantalla de venta. Generar
códigos, imprimir etiquetas, productos por kilo, presentaciones, cámara y ticket quedan fuera.

## POS-15 · Escanear en la venta

Vale para la pantalla de venta (`/pos/sale`) con la caja abierta.

- **Cuando** se escanea el código de un producto activo por unidad **entonces** entra al carrito con cantidad 1, igual que
  al elegirlo en la búsqueda: el mismo precio, el descuento de siempre y el precio al costo si la venta es al costo.
- **Cuando** se vuelve a escanear **entonces** la línea suma 1. Cada lectura cuenta: 3 escaneos son cantidad 3.
- **Cuando** el producto tiene varios códigos **entonces** cualquiera de ellos agrega la misma línea.
- **Cuando** el cursor está en el buscador, en una cantidad, en un descuento o en ningún campo **entonces** el producto se
  agrega y ningún campo cambia, salvo la cantidad de la línea que suma. Con «nue» escrito en el buscador y otra línea en 2,
  después del escaneo siguen «nue» y 2, y el cursor sigue donde estaba.
- **Cuando** un lector envía el código de 12 dígitos (UPC-A) y está guardado con 13 (un 0 delante), o al revés, **entonces**
  es el mismo código.
- **Cuando** el código se cargó después de abrir la pantalla, incluso en otra PC **entonces** se encuentra igual, sin
  recargar la página.

### Lo que no se agrega

- **Cuando** el código no está registrado o no es válido **entonces** no se agrega nada ni cambia ningún campo, suena un
  aviso y se muestra: «El código 7791234567890 no está registrado. Búscalo por nombre o cárgalo en Productos.» No se abre
  ninguna ventana y no se asigna desde la caja.
- **Cuando** el código es de un producto inactivo **entonces** suena el aviso y se muestra: «<producto> está inactivo y no
  se vende.»
- **Cuando** hay una ventana abierta (cobro, línea libre, presentaciones, grupos rápidos, una confirmación) **entonces** no
  se agrega nada, ningún campo cambia, el formulario no se envía, suena el aviso y se muestra: «Cierra esta ventana para
  escanear.»
- **Cuando** la caja está cerrada **entonces** el escaneo no hace nada. En `/pos`, adonde lleva la venta sin caja, un escaneo
  se descarta en silencio: no escribe en el monto de apertura ni envía ese formulario.

### Escaneo o teclado

- **Cuando** una persona escribe dígitos a mano en cualquier campo **entonces** se escriben como hoy: solo cuenta como
  escaneo lo que llega a la velocidad de un lector y termina en Enter.
- **Cuando** una etiqueta no se puede leer y se escribe su código completo en el buscador **entonces** el producto aparece
  y Enter lo agrega (POS-01). Un código de barras nunca coincide por parecido ni por parte del número.
- **Cuando** se confirma la venta **entonces** cada línea guarda si al menos una lectura fue por escaneo, solo para medir el
  uso. No cambia montos, costos, reportes ni el detalle de la venta (POS-05).

## CAT-10 · Registrar códigos

Con el permiso `PRODUCTS`, en el formulario de un producto y en la carga de códigos.

- **Cuando** se escanea o se escribe un código **entonces** queda en la lista del producto sin guardarse. Se guarda solo con
  el botón, viendo el nombre y el precio del producto: el Enter del lector no guarda ni cierra nada.
- **Cuando** el código no tiene 8, 12 o 13 dígitos, o su dígito de control no coincide **entonces** «El código de barras
  no es válido.»
- **Cuando** el código ya está en otro producto activo o inactivo **entonces** «El código ya es de <producto>
  (<código>).» y no se guarda.
- **Cuando** se quiere guardar un sexto código **entonces** «Un producto admite hasta 5 códigos de barras.»
- **Cuando** el producto se vende por kilo o es especial **entonces** el formulario no ofrece códigos y el servidor
  rechaza: «Este producto no admite código de barras.» Un escaneo en ese formulario muestra ese aviso, sin escribir en ningún campo ni guardar.
- **Cuando** un producto con códigos pasa a venderse por kilo **entonces** «Quita sus códigos de barras para venderlo por
  kilo.»
- **Cuando** se quita un código **entonces** se desactiva, no se borra (RG-01), con quién y cuándo. Otro producto puede
  usarlo desde ese momento.
- **Cuando** se elimina un producto **entonces** sus códigos se desactivan y no los recupera.
- **Cuando** se busca en Productos un código completo **entonces** aparece su producto, para revisar o corregir uno mal
  cargado.

## CAT-10 · Carga de códigos

- **Cuando** se abre «Cargar códigos» en Productos **entonces** se ven los productos activos, por unidad y no especiales que
  no tienen código ni están marcados «No trae», de más a menos líneas vendidas en los últimos 30 días, sin contar las ventas
  anuladas. A igual cantidad, por nombre. Se muestra «Faltan N». No se muestran montos ni cantidades vendidas.
- **Cuando** se elige un producto **entonces** se ven su nombre, código actual, proveedor y precio, y se puede escanear.
- **Cuando** se elige «Guardar y seguir» **entonces** se guarda el código y se pasa al siguiente de la lista.
- **Cuando** se elige «No trae» **entonces** queda marcado, con quién y cuándo, y sale de la lista.
- **Cuando** se elige «Saltar» **entonces** se pasa al siguiente y el producto sigue en la lista.
- **Cuando** un producto marcado «No trae» recibe un código **entonces** se quita la marca. También se puede quitar a mano
  en su formulario, y entonces no queda historia de esa marca.

## Notas de implementación

- **Reconocer el escaneo:** `shared/utils/barcode-scan.ts` retiene los dígitos en la fase de captura de `window`, antes que
  NF-07 y los campos. Hasta 60 ms entre teclas, al menos 6 dígitos y Enter son un escaneo. Lo demás se repone en el cursor,
  con hasta 60 ms de retraso. Un campo de fecha no se puede reponer: sus dígitos pasan, y un escaneo le devuelve el valor que tenía. Cada Enter detenido detiene también su `keyup`, porque hay campos que actúan al soltarlo.
  Los 60 ms son un valor inicial, por confirmar con el lector real.
- **Lector:** tiene que funcionar como teclado USB y terminar cada lectura con Enter. Un código con letras (Code 128) no se
  retiene; esas simbologías se apagan con el manual del lector.
- **Ventana abierta:** se detecta con `[aria-modal="true"]`, que tienen todas las ventanas de la app.
- **Catálogo cargado:** un código movido o un producto inactivado en otra PC sigue resolviendo como antes hasta recargar el catálogo, y eso pasa al volver a entrar a la venta.
- **Fuera de la venta y de Productos:** en Compras y Consumos el lector escribe como un teclado.
- **Datos:** migración `V27__product_barcodes.sql`. UPC-A se guarda con 13 dígitos. La validación y la forma canónica viven
  en el servidor (`BarcodeFormat`).
- **Lista de carga:** `GET /api/products/barcode-pending` ordena por ventas sin devolver ninguna cifra de ventas.
- **Presupuesto:** Productos pasa a cargarse bajo demanda para que la carga inicial siga bajo 1 MB.

## Prueba con el equipo real

Las pruebas automáticas simulan el lector. Falta probar con el lector real: un producto escaneado con el cursor en el
buscador, en una cantidad y sin foco; tres escaneos seguidos; un código desconocido; un escaneo con el cobro abierto; y
digitar números a mano en una cantidad sin que cuente como escaneo.
