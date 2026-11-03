# Catálogo web

Detalle de CAT-13 (sección 3.2 de `specs/requirements.md`). El sitio vive en el repo `JoaquinVM/akhana-web`, con sus
reglas WEB-03 a WEB-09. Aquí está lo que Admin le entrega y cómo llega, sin abrir el VPS a internet (P-57).

## CAT-13 · Marcar

- **Cuando** se edita un producto con el permiso `PRODUCTS` **entonces** el formulario muestra «Publicar en la web», que
  se guarda solo, como la foto. Al crear un producto no aparece.
- **Cuando** se marca o se desmarca **entonces** queda una fila con quién y cuándo, generados por el servidor (RG-02).
  Repetir el mismo estado no agrega nada.
- **Cuando** se abre la auditoría del producto **entonces** la sección «Catálogo web» lista cada cambio con quién y cuándo,
  del más reciente al más antiguo, o «Nunca se marcó para la web.».
- **Cuando** el producto es especial (POS-11, POS-13) o está eliminado **entonces** «Un producto especial no se publica en
  la web.» o «Un producto eliminado no se publica en la web.». Un especial no muestra la casilla.
- **Cuando** el producto está marcado sin foto o inactivo **entonces** la casilla dice por qué todavía no sale.
- **Cuando** se activa el filtro «En la web sin foto» **entonces** quedan los marcados que esperan su foto.
- **Cuando** se llama sin sesión **entonces** 401; con sesión y sin `PRODUCTS`, 403.

## WEB-01, WEB-02 · Qué sale

- **Cuando** corre la exportación **entonces** sale solo lo activo, marcado, no especial y con su foto. Si falta el
  archivo de la foto, el producto no sale y se registra en el log.
- **Cuando** se escribe `catalog.json` **entonces** lleva solo código, nombre, categoría, descripción, unidad de venta,
  precio y foto de cada producto, y nombre y precio de sus presentaciones activas. `WebCatalog` es un record aparte de
  `ProductResponse`, para que un campo nuevo del catálogo interno nunca salga solo.
- **Cuando** un producto deja de salir **entonces** su foto se borra de la copia y del bucket. La foto de Admin no cambia.

## Cómo llega

```
API (cada 5 min) → volumen web_catalog → publish-web-catalog.sh (cada 10 min) → Cloudflare R2 → akhana-web en Pages
```

- **Cuando** un precio cambia **entonces** se ve en la web en menos de 15 minutos, más un minuto de caché del JSON.
- **Cuando** la exportación falla **entonces** queda la copia anterior. Si la copia pasa de 30 minutos sin renovarse, el
  script falla y avisa por `HEALTHCHECK_WEB_CATALOG_URL`, sin subir nada.
- **Cuando** falta el catálogo o no es válido **entonces** no se sube ni se borra nada: un volumen vacío no vacía la web.
- **Cuando** `WEB_CATALOG_REMOTE` está vacío **entonces** el script termina sin subir nada.
- **Cuando** se revisa `docker-compose.prod.yml` **entonces** los puertos publicados son los mismos: la subida solo sale.

## Notas de implementación

- El volumen se puede regenerar: no entra en respaldos. Las fotos se copian una vez, porque nunca cambian bajo su id.
- El JSON se reescribe solo si cambió; si no, se renueva su hora, que es lo que vigila el script.
- La exportación lee en una transacción de solo lectura y escribe fuera de ella, para no retener una conexión del POS.
- Encendida solo en producción (`WEB_CATALOG_ENABLED`).

## Para publicar (lo hace Joaquín)

1. En Cloudflare, con verificación en dos pasos: crear el bucket de R2, darle acceso público y CORS para el dominio de la
   web, y crear un token que solo pueda escribir en ese bucket.
2. En el VPS: agregar el remoto a la configuración de rclone con `no_check_bucket = true`, definir `WEB_CATALOG_REMOTE` y
   `HEALTHCHECK_WEB_CATALOG_URL` en `/opt/akhana/.env`.
3. Instalar el timer, que un despliegue no instala solo: copiar `deploy/systemd/akhana-web-catalog.*` a
   `/etc/systemd/system/`, `systemctl daemon-reload` y `systemctl enable --now akhana-web-catalog.timer`.

## Validación

- `WebCatalogExportIntegrationTest`, contra PostgreSQL y archivos reales.
- `deploy/tests/publish-web-catalog.test.sh`, con rclone real contra carpetas locales. No se probó contra R2.

## Tareas

- [x] Migración, marca, auditoría y exportación, con pruebas.
- [x] Casilla y filtro en Productos, con pruebas Vitest.
- [x] Script, timer y volumen.
- [ ] Crear el bucket y publicar.
