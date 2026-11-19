# Integraciones

Detalle de INT-01 (sección 3.13 de `specs/requirements.md`).

## INT-01 · Catálogo para el bot de WhatsApp

El bot de WhatsApp de la tienda (proyecto aparte, `whatsapp-bot-platform`) responde precios y toma pedidos con los
productos de Admin. Lo lee de Admin para que haya un solo catálogo: lo que se cambia en Productos llega al bot en
la siguiente sincronización (cada 15 minutos, o al pedirla desde el panel del bot).

### Escenarios

- **Cuando** el bot pide `GET /api/integrations/catalog` con el encabezado `X-Api-Key` igual a `CATALOG_API_KEY`
  **entonces** recibe la moneda (`Bs`) y los productos activos, ordenados por nombre, con código, nombre, categoría,
  descripción, unidad de venta (`UNIT` o `KILOGRAM`), precio de venta y fecha del último cambio.
- **Cuando** un producto se vende por kilo **entonces** su precio es por kilo y trae sus presentaciones activas con
  nombre, peso y precio: el fijo, o peso × precio por kilo redondeado a 2 decimales (CAT-11). Las presentaciones
  inactivas no van.
- **Cuando** un producto está inactivo o eliminado, o es especial del POS (sin identificar o de terceros, POS-11 y
  POS-13) **entonces** no va.
- **Cuando** se arma la respuesta **entonces** nunca lleva costo, utilidades, proveedor, etiquetas, códigos de barras,
  fotos ni usuarios de auditoría.
- **Cuando** falta la clave, es otra o `CATALOG_API_KEY` está vacía **entonces** responde 401.
- **Cuando** un usuario con sesión pide el catálogo del bot **entonces** responde 403: la clave da un permiso propio
  (`INTEGRATION_CATALOG`) que ningún rol tiene, y la clave del bot no sirve para ninguna otra ruta.
- **Cuando** `CATALOG_API_KEY` tiene menos de 32 caracteres **entonces** Admin no arranca y lo dice.

### Notas de implementación

- `IntegrationApiKeyFilter` (en `SecurityConfig`) actúa solo en `/api/integrations/`; compara el SHA-256 de la clave
  en tiempo constante. `IntegrationCatalogController` exige `INTEGRATION_CATALOG`.
- `IntegrationCatalogServiceImpl` usa `findByStatusOrderByNameAsc(ACTIVO)` y el precio de las presentaciones de
  `ProductPresentationServiceImpl.priceOf`. Sin migración: solo lee.
- Red: nginx responde 404 a `/api/integrations/`, así que el endpoint no sale a internet ni por el túnel. El worker
  del bot se une a la red `akhana_default` y llama a `http://akhana-api-1:8080/api/integrations/catalog`.
- Sin stock hasta la V2: el bot solo sabe si un producto está activo.

### Pruebas

- `IntegrationApiKeyFilterTest`: clave correcta, distinta, ausente, otra ruta e integración apagada.
- `RequiredEnvironmentValidatorTest`: `CATALOG_API_KEY` opcional, con al menos 32 caracteres.
- `IntegrationCatalogIntegrationTest` (Postgres): solo activos y normales, precio por kilo y de presentaciones, sin
  datos internos en la respuesta, 401 sin clave y 403 con sesión de usuario.

### Tareas

- [x] Endpoint, filtro, validación de la clave, bloqueo en nginx y pruebas.
- [ ] Publicar y encender (ver «Próximos pasos» en `specs/requirements.md`).
