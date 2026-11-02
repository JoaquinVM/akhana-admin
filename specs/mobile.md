# App instalable

Detalle de MOV-01 a MOV-04 (sección 3.12 de `specs/requirements.md`), con los mismos IDs. Es Admin instalado en la
pantalla de inicio del teléfono: mismas pantallas, datos, usuarios y permisos. No es un sistema aparte ni se publica en
tiendas de apps. Sigue sin haber modo sin internet (P-14) y cada teléfono necesita la app de Tailscale encendida
(`deploy.md`).

## MOV-01 · Instalar

| Teléfono | Cómo se instala |
| --- | --- |
| Android | Chrome → menú ⋮ → Instalar app o Agregar a la pantalla principal |
| iPhone | Safari → Compartir → Agregar a inicio |

- **Cuando** se instala **entonces** queda como «Akhana», con el ícono de la tienda, y abre sin la barra del navegador.
- **Cuando** se pide `/manifest.webmanifest` **entonces** responde con nombre, nombre corto, `display: standalone`,
  `start_url` y `scope` en la raíz, e íconos de 192 y 512 px.
- En iPhone no se usa `viewport-fit=cover`: el sistema deja el contenido dentro de la zona segura, sin que quede debajo de
  la barra de estado ni de la zona inferior.
- Los íconos (`public/icons/`) salen de `akhana-logo.png`, con fondo blanco y el anillo dentro de la zona segura. Akhana
  está en rebranding: cambiarlos es reemplazar esos archivos, pero en iPhone el ícono solo cambia al reinstalar y en
  Android puede tardar días.

## MOV-02 · Qué se ve primero

La app se reconoce instalada con `display-mode: standalone` y, en iPhone, `navigator.standalone`.

- **Cuando** un administrador abre la app instalada **entonces** llega al Dashboard en **Hoy**, y «Ahora mismo» (caja
  actual, saldos y deudas, DSH-04) va antes de los filtros y los indicadores, y no se repite más abajo.
- **Cuando** abre Admin en el navegador, en la PC o en el celular **entonces** sigue en **Mes** y con el orden de siempre
  (`dashboard.md`).
- **Cuando** elige **Semana** o **Mes** en la app **entonces** ve los mismos números que en el navegador (DSH-01).
- **Cuando** la app instalada vuelve de segundo plano **entonces** recarga el período elegido, salvo en **Rango**: el
  teléfono retoma la app sin abrirla de nuevo y no debe seguir mostrando un «Ahora mismo» viejo. Si el token venció,
  esa recarga lleva al login.

## MOV-03 · Sin conexión

Un service worker escrito a mano (`public/sw.js`), sin dependencias. Lo registra solo la app instalada, así que la PC de
caja no cambia. En un Android, el Chrome normal comparte el worker una vez registrado y también ve esta página.

- **Cuando** se abre la app sin internet o con Tailscale apagado **entonces** muestra «No se pudo conectar. Revisa tu
  internet y que Tailscale esté encendido.» con un botón **Reintentar**.
- **Cuando** el servidor responde 502, 503 o 504, o no responde en 10 s **entonces** muestra la misma página.
- **Cuando** se toca **Reintentar** con la conexión de vuelta **entonces** abre la pantalla que se estaba pidiendo.
- **Cuando** se pide cualquier otra cosa (`/api`, bundles, otros orígenes) **entonces** el worker no interviene.
- **Cuando** se publica una versión nueva **entonces** la app la usa al abrirse: el worker guarda solo `offline.html` y
  su ícono, nunca pantallas ni datos del negocio. Al cambiar esos dos archivos hay que subir `CACHE_NAME` en `sw.js`.
- nginx no cachea `sw.js`, `manifest.webmanifest` ni `icons/`, igual que `index.html`.

Límites conocidos:

- La página propia aparece desde la segunda apertura: la primera, ya instalada y con conexión, registra el worker.
- Con la app ya abierta y sin conexión no se muestra esta página: cada pantalla muestra su error, como en el navegador.
- En iPhone el sistema puede borrar la caché de una app que no se usa; entonces vuelve el error del navegador hasta la
  siguiente apertura con conexión.

## MOV-04 · Permisos y sesión

Instalar no cambia nada de esto: los permisos son los de cada rol (USR-05, P-33) y la sesión sigue durando 12 h (T-19).

- **Cuando** un operador abre la app instalada **entonces** llega al POS, sin Dashboard en el menú, y `GET /api/dashboard`
  responde 403.
- **Cuando** vence el token **entonces** vuelve al login con el mensaje de sesión terminada, como en el navegador.
- En iPhone la app instalada no comparte sesión con Safari: pide su propio inicio de sesión.

## Validación

Probado en Chrome de escritorio a 375 × 812 contra una base local desechable y la configuración real de nginx,
simulando la app instalada con `navigator.standalone`. La instalación real, la zona segura del iPhone y Tailscale apagado
solo se validan en teléfonos reales, porque no hay ambiente de staging.
