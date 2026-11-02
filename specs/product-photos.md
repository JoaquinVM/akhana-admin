# Foto de productos

Detalle de CAT-12 (sección 3.2 de `specs/requirements.md`); su respaldo, NF-07, está en `specs/deploy.md`. Cada producto
admite una foto, que después usará Akhana Web. Aquí no se abre nada a internet: cómo llega a la web se decide en un
trabajo de Akhana Web (P-57).

## CAT-12 · Subir una foto

- **Cuando** se edita un producto con el permiso `PRODUCTS` **entonces** el formulario muestra la sección «Foto», con la
  foto actual o el botón «Agregar foto». En el celular, ese botón permite usar la cámara.
- **Cuando** se está creando un producto **entonces** la sección dice «Guarda el producto para agregar la foto.»
- **Cuando** se elige un archivo válido **entonces** se ve una espera mientras sube y, al terminar, la foto en el
  formulario y en el listado, sin recargar la página.
- **Cuando** el archivo no es JPEG, PNG o WebP según su contenido real, cualquiera sea su extensión, o está dañado, o
  pesa más de 10 MB **entonces** «La foto debe ser JPEG, PNG o WebP de hasta 10 MB.» y el producto no cambia.
- **Cuando** la imagen tiene más de 50 megapíxeles **entonces** «La foto tiene demasiados píxeles.», sin intentar
  procesarla.
- **Cuando** el disco está lleno o falla el guardado **entonces** «No se pudo guardar la foto. Intenta de nuevo.», el
  producto conserva su foto anterior y no se ven detalles técnicos (RG-04).
- **Cuando** dos personas guardan una foto del mismo producto a la vez **entonces** una se guarda y la otra recibe un
  conflicto (RG-04): nunca quedan dos fotos activas.

## CAT-12 · Qué se guarda

- **Cuando** se guarda una foto **entonces** queda una versión de hasta 1600 px en su lado mayor, sin agrandar las más
  chicas, y una miniatura de hasta 300 px. El archivo original no se conserva.
- **Cuando** la foto del celular trae orientación en sus metadatos **entonces** se aplica antes de quitarlos: la foto no
  queda girada.
- **Cuando** la foto trae ubicación GPS u otros metadatos **entonces** ni la versión guardada ni la miniatura los
  conservan. Se prueba con una foto que tenga GPS real.
- **Cuando** se reduce una foto de 12 megapíxeles **entonces** la versión guardada pesa 500 KB o menos.
- **Cuando** la imagen tiene transparencia **entonces** se guarda con fondo blanco.
- **Cuando** se guarda un archivo **entonces** su nombre lo genera el servidor, sin usar el nombre recibido, y nunca
  reemplaza a otro archivo.

## CAT-12 · Cambiar o quitar

- **Cuando** se elige «Cambiar foto» y la nueva se guarda **entonces** pasa a ser la foto del producto; la anterior queda
  inactiva, con su archivo y su auditoría.
- **Cuando** se elige «Quitar foto» y se confirma **entonces** el producto queda sin foto y el archivo se conserva. No
  pide motivo, porque no es una anulación de dinero.
- **Cuando** se sube, cambia o quita una foto **entonces** queda quién y cuándo, generado por el servidor (RG-02).
- **Cuando** un producto se inactiva o se elimina **entonces** su foto no cambia.

## CAT-12 · Listado

- **Cuando** se abre Productos **entonces** cada fila muestra la miniatura o una marca «Sin foto», y las miniaturas se
  piden solo para las filas visibles.
- **Cuando** se activa el filtro «Sin foto» **entonces** quedan solo esos productos, combinado con el filtro de estado y
  la búsqueda, y se ve cuántos son.
- **Cuando** se pide el catálogo (`GET /api/products`) **entonces** la respuesta solo dice si hay foto y su referencia,
  sin datos de imagen, y su tamaño sigue dentro de lo esperado para NF-04.
- **Cuando** el POS, Consumos o Compras usan el catálogo **entonces** no muestran fotos ni piden imágenes.
- **Cuando** el registro existe pero falta el archivo **entonces** se ve la marca «Sin foto» y la pantalla sigue
  funcionando.

## CAT-12 · Acceso

- **Cuando** se llama sin sesión a una ruta de fotos **entonces** responde 401; con sesión y sin `PRODUCTS`, 403.
  `EndpointAuthorizationTest` cubre todas las rutas nuevas.
- **Cuando** la foto pasa por el nginx de producción **entonces** llega al navegador: hoy la regla de estáticos atrapa las
  rutas terminadas en `.jpg`, `.png` o `.webp` antes que `/api/`, así que se prueba contra ese nginx y no solo contra el
  backend.
- **Cuando** se sirve una foto **entonces** el tipo de contenido lo fija el servidor y se mantiene `nosniff`.
- **Cuando** se revisa `docker-compose.prod.yml` **entonces** los puertos publicados son los mismos de hoy.

## Modelo de datos (migración `V26__product_photos.sql`)

- `product_photos`: `id`, `product_id`, `file_name`, `thumbnail_file_name` (únicos), `active`, auditoría (`created_by`,
  `created_at`, `deactivated_by`, `deactivated_at`). Un índice único parcial en `product_id` donde `active` garantiza una
  sola foto activa.
- `Product.activePhotoId` es una fórmula de solo lectura: el catálogo trae la referencia en su misma consulta. Subir o
  quitar una foto no toca la fila de `products`.

## API

Todas con el permiso `PRODUCTS`. Ninguna ruta termina en una extensión de imagen.

| Método y ruta | Qué hace |
| --- | --- |
| `POST /api/products/{id}/photo` | Multipart con `file` y `expectedPhotoId` (la foto que mostraba la pantalla; sin él, ninguna). 201 con `{photoId}`; 400 si el archivo no sirve o el producto está eliminado; 409 si otra persona la cambió; 500 si no se pudo guardar |
| `DELETE /api/products/{id}/photo?expectedPhotoId=` | Quita la foto. 204, o 409 si ya no es la actual |
| `GET /api/products/{id}/photo/{photoId}/image` y `/thumbnail` | JPEG con caché privada e inmutable; 404 si no está activa o falta el archivo |

`GET /api/products` y `/{id}` traen `photoId`, sin datos de imagen.

## Notas de implementación

- **Lectura:** el formato se decide por los primeros bytes, y ancho y alto se leen del encabezado antes de decodificar.
  JPEG y PNG se decodifican submuestreados hasta unos 3200 px; WebP puede decodificarse entero, y orientar la imagen la duplica (se
  midieron picos de unos 400 MB con 41 megapíxeles), por eso se procesa una imagen a la vez, con 30 s de espera.
- **Límites antes de decodificar (`ProductPhotoInspector`):** un archivo de 10 MB puede pedir gigabytes u horas. Un WebP debe tener
  un solo frame, que se mide aparte porque el decodificador reserva memoria por lo que declara el frame y no el encabezado; una
  animación se rechaza. Un JPEG admite hasta 100 scans. De los metadatos solo se lee la etiqueta de orientación del primer
  bloque EXIF: un lector general infla los textos comprimidos de un PNG y crea un arreglo por etiqueta.
- **Bibliotecas:** TwelveMonkeys lee WebP y JPEG en CMYK. `metadata-extractor` queda solo en las pruebas, para comprobar que lo
  guardado no trae metadatos; leer la orientación con ella agotaba la memoria con un EXIF manipulado. `ImageIO.scanForPlugins()`
  corre al arrancar porque dentro del jar de Spring Boot los lectores pueden no registrarse solos.
- **Limpieza:** dibujar sobre una imagen RGB nueva y escribir el JPEG sin metadatos quita todo el EXIF.
- **Archivos:** se llaman `<id>.jpg` y `<id>-thumb.jpg`. Se escriben en `.tmp/` y se publican con un enlace duro, que falla
  si el nombre existe: un archivo guardado nunca se reemplaza. Solo se borran los de un guardado fallido, que ninguna fila
  registra.
- **Orden:** el procesamiento va fuera de la transacción, que solo registra archivos ya escritos. Se desactiva la anterior
  y se inserta la nueva con `saveAndFlush`, porque Hibernate ejecuta los inserts primero y chocaría con el índice.
- **Pantalla:** `<img src>` no envía el token, así que las imágenes se piden con `HttpClient` como blob. La miniatura se
  pide cuando la fila entra en pantalla (`IntersectionObserver`). La foto se guarda sola, sin pasar por «Guardar».
- **Filtro:** se guía por `photoId`. Un registro cuyo archivo se perdió se ve «Sin foto», pero no entra en el filtro.
- **nginx:** `location ^~ /api/` evita que la regla de estáticos atrape una ruta de la API terminada en `.jpg`.

## Validación

- **Catálogo (NF-04):** con 822 productos activos de una copia de la base local, el JSON pesa 601.722 bytes (60.480 con gzip) y
  responde en 40 a 60 ms. Con una foto en cada producto, 629.670 bytes (81.327 con gzip), en el mismo tiempo.
- El resto de la validación, con el stack de producción, está en `specs/deploy.md`.

## Fuera de CAT-12

Publicar fotos, carga por lote, fotos en la importación (CAT-06), varias fotos, fotos de presentaciones o variantes, fotos
en POS, Consumos, Compras o tickets, recortar o editar, recuperar una foto anterior desde la pantalla, ver su historial y
aceptar HEIC.

## Tareas

- [x] Migración, modelo, procesador, almacenamiento, servicio y rutas, con pruebas contra PostgreSQL.
- [x] Sección «Foto», miniaturas y filtro «Sin foto» en Productos, con pruebas Vitest.
- [x] Respaldo, restauración, nginx y compose (`specs/deploy.md`, NF-07).
- [ ] Probar una foto real del celular con GPS y dejarlo anotado aquí.
- [ ] Antes de publicar: revisar que el bucket de B2 no tenga una regla de ciclo de vida que expire archivos.
