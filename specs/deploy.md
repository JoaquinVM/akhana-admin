# Producción (NF-01, NF-02, NF-05)

Servidor de producción de la V1: un VPS con la app detrás de HTTPS, accesible solo para los equipos de la tienda, con
respaldo diario de la base guardado fuera del VPS y una restauración probada antes de la salida.

Todo lo que se puede versionar vive en `deploy/` (compose de producción, Dockerfiles, nginx, scripts y unidades de
systemd). Los secretos viven solo en el servidor (`/opt/akhana/.env`, `rclone.conf`) y en el gestor de contraseñas
del dueño; nunca en git.

## Decisiones (sesión «VPS recomendación para proyecto»)

| Tema | Decisión |
| --- | --- |
| Proveedor | Contabo, plan más chico, región US East (~100–130 ms desde Bolivia), con Auto Backup ($2/mes). |
| Sistema | Ubuntu 24.04 LTS con Docker y `docker compose`, administrado con los scripts del repo (ver D-1). |
| Acceso | Tailscale: solo los equipos agregados a la red de la tienda ven el sistema; sin dominio propio. |
| HTTPS | Certificado `*.ts.net` de Tailscale (`tailscale serve`), delante de nginx como proxy inverso. |
| Respaldo | `pg_dump` diario cifrado y subido a un almacenamiento S3 fuera de Contabo (ver D-2); el Auto Backup de Contabo es una capa extra, no lo reemplaza. |

### Confirmadas con el dueño (todas gratuitas)

- **D-1 · Imagen del VPS: Ubuntu 24.04 limpio**, no Dokploy: su panel (3000) y su Traefik (80/443) quedan abiertos a
  internet, lo contrario de lo decidido con Tailscale. Si ya se instaló Dokploy, reinstalar desde el panel de Contabo
  antes de cargar datos.
- **D-2 · Respaldo en Backblaze B2** (10 GB gratis), subido cifrado con `rclone crypt`: el proveedor no puede leer
  los datos.
- **D-3 · Avisos con healthchecks.io** (plan Hobbyist gratuito): un chequeo para el respaldo diario y otro para la
  prueba semanal de restauración; si un día no llega el aviso, manda un correo.
- **Tailscale:** el plan Personal es gratuito, pero Tailscale lo presenta como de uso no comercial. Si se exige un
  plan pago para la tienda, la arquitectura no cambia (solo el plan de la cuenta).

## Arquitectura

```
Equipo de la tienda (app Tailscale)
   │  https://akhana.<red>.ts.net  (TLS de Tailscale, solo dentro de la red)
   ▼
VPS · tailscale serve :443 ──► 127.0.0.1:8081
                                   │
                    docker compose (red interna)
                    ├─ web: nginx — Angular compilado + /api ──► api:8080
                    ├─ api: Spring Boot (Java 21)
                    └─ db: PostgreSQL 17 (volumen, sin puerto publicado)
```

Ningún contenedor publica puertos en `0.0.0.0` (Docker se salta `ufw`); solo `web` publica en `127.0.0.1:8081`.

## NF-05 · VPS

### Preparación del servidor

- **Cuando** se clona el repo en `/opt/akhana/app` con una clave de despliegue de solo lectura de GitHub y se corre
  `deploy/setup-server.sh` como root en un Ubuntu 24.04 recién instalado
- **Entonces** quedan instalados Docker, `rclone`, Tailscale y las actualizaciones de seguridad automáticas
  (`unattended-upgrades`); la zona horaria es `America/La_Paz`; se crean `/opt/akhana` (con `.env` copiado del
  ejemplo, permisos `600`) y `/var/backups/akhana`, y se activan los timers del respaldo y de la prueba de
  restauración. Si se corre otra vez, no rompe nada (idempotente).

- **Cuando** se inicia Tailscale en el VPS (`tailscale up --ssh`, paso manual con el enlace de inicio de sesión)
- **Entonces** el VPS aparece en la red de la tienda con el nombre `akhana` y se puede entrar por SSH a través de
  Tailscale.

### Firewall

- **Cuando** ya se entra por Tailscale y se cierra el acceso público (`setup-server.sh --lock`)
- **Entonces** `ufw` rechaza toda conexión entrante por la IP pública salvo el puerto UDP de Tailscale; SSH, la app y
  la base solo responden por la interfaz `tailscale0`. Si la sesión SSH no entra por Tailscale, el script se niega
  (se cortaría); `--force` solo con la consola VNC de Contabo a mano.

- **Cuando** alguien fuera de la red prueba la IP pública del VPS (`nmap`, `curl http://IP`, `curl http://IP:8081`,
  `psql -h IP`)
- **Entonces** no hay ningún puerto abierto que responda.

- **Cuando** un empleado deja la tienda
- **Entonces** se quita su equipo del panel de Tailscale y se desactiva su usuario en la app (USR); sin ambos pasos no
  se da por cerrado.

## NF-02 · HTTPS con proxy inverso

- **Cuando** se activan MagicDNS y HTTPS en el panel de Tailscale y se corre
  `tailscale serve --bg --https=443 http://127.0.0.1:8081`
- **Entonces** `https://akhana.<red>.ts.net` responde con un certificado válido (renovado por Tailscale) y muestra el
  login.

- **Cuando** el navegador pide una ruta de la app (`/pos`, `/caja`) o recarga la página en ella
- **Entonces** nginx devuelve `index.html` (rutas de Angular) y los archivos con hash se cachean; `index.html` no.

- **Cuando** el navegador llama a `/api/...`
- **Entonces** nginx lo pasa al backend en la misma URL (sin CORS), con `X-Forwarded-For` y `X-Forwarded-Proto`, y
  acepta archivos de hasta 21 MB (importación y migración, FIN-11).

- **Cuando** fallan 20 intentos de login desde un equipo (T-19)
- **Entonces** el bloqueo por IP cae sobre la IP de Tailscale de ese equipo y no sobre la de nginx: nginx toma como
  cliente la última dirección que agregó `tailscale serve` a `X-Forwarded-For` (lo que escriba el navegador antes se
  ignora) y el backend corre con `FORWARD_HEADERS_STRATEGY=native`. Se verifica en el VPS que otro equipo de la
  tienda sigue pudiendo entrar.

- **Cuando** se responde cualquier página
- **Entonces** lleva `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` y
  `Referrer-Policy: same-origin`.

- **Cuando** se cambia `JWT_EXPIRATION` en `/opt/akhana/.env` y se reinicia el backend
- **Entonces** los tokens nuevos duran lo configurado (expiración de sesión configurable; 12 h por defecto, USR-07).

## Variables de entorno

- **Cuando** se prepara el servidor
- **Entonces** se copia `deploy/.env.production.example` a `/opt/akhana/.env` (dueño root, permisos `600`) y se
  completan los valores con secretos nuevos generados con `openssl rand`; nunca los de desarrollo.

| Variable | Valor en producción |
| --- | --- |
| `DB_NAME`, `DB_USERNAME` | `akhana` |
| `DB_PASSWORD` | Nuevo, 32+ caracteres |
| `DB_URL` | `jdbc:postgresql://db:5432/akhana` (lo fija el compose) |
| `JWT_SECRET` | Nuevo, `openssl rand -hex 32` |
| `JWT_EXPIRATION` | `12h` |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Primer admin; cambia la contraseña al entrar (USR-06) |
| `FORWARD_HEADERS_STRATEGY` | `native` |
| `TZ` | `America/La_Paz` |
| `BACKUP_REMOTE` | Remoto cifrado de rclone, p. ej. `akhana-crypt:` |
| `BACKUP_KEEP_LOCAL_DAYS`, `BACKUP_KEEP_REMOTE_DAYS` | `7` y `60` |
| `HEALTHCHECK_BACKUP_URL`, `HEALTHCHECK_RESTORE_URL` | URLs de aviso de healthchecks.io (D-3) |
| `WEB_PORT` | `8081` (nginx en `127.0.0.1`) |

- **Cuando** falta una variable obligatoria
- **Entonces** el backend no arranca y el log dice cuál falta (T-03); `docker compose` tampoco levanta si falta
  `DB_PASSWORD`.

- **Cuando** se revisa el repo
- **Entonces** no hay ningún `.env` ni `rclone.conf` versionado; solo el ejemplo con valores vacíos.

- **Cuando** se crean los secretos
- **Entonces** el dueño guarda en su gestor de contraseñas `/opt/akhana/.env` y las dos claves de `rclone crypt`.
  Sin las claves de crypt, los respaldos fuera del VPS no se pueden abrir.

## Arranque del backend y del frontend

- **Cuando** se construyen las imágenes (`deploy/deploy.sh`)
- **Entonces** el backend se compila en una imagen multi-etapa (Gradle → JRE 21, usuario sin privilegios) y el
  frontend con `npm ci && npm run build` en una imagen de nginx. Las pruebas no corren en el VPS: se corren antes de
  subir a `main` (NF-03, flujo de `CLAUDE.md`).

- **Cuando** se despliega una versión (`deploy/deploy.sh` en el VPS)
- **Entonces** el script baja `main` de GitHub (solo avance rápido), hace un respaldo previo local
  (`backup.sh --tag pre-deploy --local-only`), reconstruye las imágenes, levanta con `docker compose up -d` y espera a que el
  backend responda sano; si no responde en 3 minutos, lo informa con las últimas líneas del log.

- **Cuando** el backend arranca con migraciones nuevas
- **Entonces** Flyway las aplica antes de aceptar peticiones (T-01); el respaldo previo permite volver atrás.

- **Cuando** se reinicia el VPS o se cae un contenedor
- **Entonces** los tres servicios vuelven solos (`restart: unless-stopped`), `api` espera a que `db` esté sana y
  `web` espera a `api`.

- **Cuando** se consulta el estado del backend
- **Entonces** hay un chequeo de salud interno (Actuator `/actuator/health`, solo dentro de la red de Docker; nginx no
  lo expone).

- **Cuando** los contenedores escriben logs
- **Entonces** Docker los rota (10 MB × 5 por servicio) para no llenar el disco.

- **Cuando** se quiere probar el compose de producción en la Mac
- **Entonces** `docker compose -f deploy/docker-compose.prod.yml --env-file .env up` levanta todo en
  `http://localhost:8081`, sin Tailscale.

## NF-01 · Respaldo diario fuera del VPS

- **Cuando** son las 03:00 (hora de La Paz)
- **Entonces** el timer de systemd corre `deploy/backup.sh`, que:
  1. saca `pg_dump --format=custom` de la base desde el contenedor `db` a
     `/var/backups/akhana/akhana-AAAAMMDD-HHMM.dump`;
  2. verifica el archivo con `pg_restore --list` (si falla, no sube nada y avisa);
  3. lo sube cifrado con `rclone copy` a `BACKUP_REMOTE` (fuera de Contabo, D-2);
  4. borra los locales de más de 7 días y los remotos de más de 60;
  5. avisa a `HEALTHCHECK_BACKUP_URL` el inicio y el éxito o el fallo, con el final del log (D-3).

- **Cuando** un paso falla (base caída, disco lleno, sin red, clave incorrecta)
- **Entonces** el script termina con error, el log queda en `journalctl -u akhana-backup` y healthchecks.io manda un
  correo; si el VPS entero está caído, el correo llega igual porque no llega el aviso diario.

- **Cuando** se mira el almacenamiento remoto
- **Entonces** solo hay archivos cifrados (nombres y contenido); el proveedor no puede leer los datos.

- **Cuando** se pierde el VPS y la cuenta de Contabo a la vez
- **Entonces** el último respaldo remoto (como mucho de 24 h) más las claves del gestor de contraseñas alcanzan para
  levantar el sistema en otro servidor.

## NF-01 · Restauración probada

- **Cuando** se corre `deploy/restore.sh --check [archivo|latest]`
- **Entonces** el script baja el respaldo del remoto, lo restaura en una base temporal `akhana_restore_check` dentro
  del mismo Postgres, compara con la base viva (versión de Flyway, usuarios, productos, ventas y sus líneas,
  movimientos, pedidos, cajas y la última venta), muestra el resultado y borra la base temporal. No toca la base
  real. Falla si la restauración da error o si el respaldo tiene más registros que la base viva (puede tener menos:
  es de la madrugada).

- **Cuando** es domingo a las 04:00
- **Entonces** un timer corre `restore.sh --check latest` y avisa a `HEALTHCHECK_RESTORE_URL`: así la restauración se prueba
  cada semana, no solo una vez.

- **Cuando** hay que recuperar la base de verdad (`deploy/restore.sh --replace archivo`)
- **Entonces** el script pide escribir `RESTAURAR`, detiene `api`, hace un respaldo local de lo que haya
  (`--tag pre-restore`), recrea la base `akhana` desde el archivo y vuelve a levantar `api`.

- **Cuando** se hace la prueba antes de salir (compuerta de la Fase 0)
- **Entonces** se cumple todo esto, y queda anotado aquí en «Validación»:
  1. Con datos cargados en el VPS, `backup.sh` sube un respaldo y `restore.sh --check latest` coincide en todo.
  2. En la Mac, sin acceso al VPS, se baja ese respaldo con `rclone` y las claves del gestor, se restaura en el
     Postgres local, el backend arranca contra él y los reportes del mes muestran los mismos totales que producción.

## Tareas

- [x] Confirmar D-1, D-2 y D-3.

En el repo (rama `feat/deploy`):

- [x] `backend/Dockerfile` (multi-etapa, usuario sin privilegios) y Actuator con solo `health` expuesto y permitido.
- [x] `frontend/Dockerfile` y `deploy/nginx.conf` (rutas de Angular, `/api`, 21 MB, caché, cabeceras de seguridad).
- [x] `deploy/docker-compose.prod.yml` (db, api, web; solo `127.0.0.1:8081`; healthchecks; rotación de logs; `TZ`).
- [x] `deploy/.env.production.example` y `.dockerignore` del backend y del frontend.
- [x] `deploy/setup-server.sh` (paquetes, Tailscale, zona horaria, `/opt/akhana`, timers; `--lock` para `ufw`).
- [x] `deploy/deploy.sh` (pull de `main`, respaldo previo, build, up, espera de salud).
- [x] `deploy/backup.sh`, `deploy/restore.sh`, `deploy/common.sh` y las unidades `akhana-backup` y
  `akhana-restore-check` (service + timer).
- [x] Probar en la Mac: compose de producción, respaldo, subida y restauración (ver «Validación»).
- [x] `shellcheck` sin advertencias en los scripts.

En el VPS (con el dueño), en este orden:

- [x] Instalar Ubuntu 24.04 (D-1). Por SSH: `apt-get install -y git`, `ssh-keygen -t ed25519`, agregar la clave
  pública como *deploy key* de solo lectura en GitHub y `git clone git@github.com:JoaquinVM/akhana-admin.git
  /opt/akhana/app`.
- [x] `/opt/akhana/app/deploy/setup-server.sh`, `tailscale up --ssh --hostname akhana`, activar MagicDNS y HTTPS en el
  panel de Tailscale.
- [x] Crear el bucket privado en B2 y una clave de aplicación solo para él; `rclone config` con un remoto `b2` y un
  remoto `crypt` llamado `akhana-crypt`; guardar las dos claves de crypt en el gestor de contraseñas.
- [x] Crear los dos chequeos en healthchecks.io (diario con 2 h de gracia; semanal con 1 día).
- [x] Completar `/opt/akhana/.env` (guardar copia en el gestor) y correr `deploy.sh`.
- [x] `tailscale serve --bg --https=443 http://127.0.0.1:8081`; entrar con HTTPS y cambiar la contraseña del admin.
- [ ] Instalar Tailscale en la PC de caja y el celular y entrar con HTTPS desde ellos.
- [x] Desde una sesión por Tailscale: `setup-server.sh --lock`; comprobar desde fuera de la red que la IP pública no
  responde en ningún puerto.
- [x] Prueba de restauración en el VPS y en la Mac (arriba); anotar en «Validación».
- [ ] El día de la salida, antes de migrar: `restore.sh --replace /opt/akhana/base-limpia-v1.dump` (la base recién
  desplegada, con el admin definitivo) para quitar la copia de prueba.

## Validación

En la Mac (rama `feat/deploy`), con una copia de la base local que tiene el historial importado:

- `deploy.sh --no-pull` construyó las imágenes y levantó db, api y web sanos; `web` publica solo en `127.0.0.1`.
- nginx: `/` y `/pos` devuelven la app con `no-cache` y las cuatro cabeceras de seguridad; los `.js` con caché de un
  año; `/actuator/health` no llega al backend (devuelve la app); `/api` sin token da 401; el login del primer admin
  funciona y pide cambiar la contraseña.
- `restore.sh --replace` cargó la copia (respaldo `pre-restore` previo) y el backend arrancó sano sobre ella.
- `backup.sh` sacó, verificó y subió el respaldo (remoto local en lugar de B2); `restore.sh --check latest` lo bajó y
  coincidió en todo: Flyway 18, 845 productos, 91 ventas, 6.116 líneas, 1.522 movimientos, 517 pedidos y 145 cajas.
- Un fallo simulado (archivo inexistente) termina con error y pasa por el aviso de fallo.

En el VPS (Contabo, Ubuntu 24.04.5, 8 GB, US East), con `v1.0.0` (`6fbdbf1`) y una copia de la base local:

- Pruebas de `6fbdbf1` antes de desplegar: suite backend completa y frontend 554/554.
- `deploy.sh` (primer despliegue) construyó y levantó db, api y web sanos en unos 4 minutos;
  `https://akhana.<red>.ts.net` responde con certificado válido, `/api` sin token da 401, y el login del admin pidió
  cambiar la contraseña.
- `--lock`: desde fuera de la red, la IP pública no responde en 22, 80, 443, 5432, 8080 ni 8081; por Tailscale sí.
- `rclone` contra B2: el archivo sube cifrado (en B2 el nombre es ilegible), baja con el mismo contenido y se borra.
- `restore.sh --replace` cargó la copia; la base recién desplegada quedó en `/opt/akhana/base-limpia-v1.dump`, fuera
  de la rotación.
- `backup.sh` subió a B2 el respaldo (680 KB) y avisó a healthchecks.io; `restore.sh --check latest` lo bajó de B2 y
  coincidió en todo: Flyway 19, 11 usuarios, 847 productos, 105 ventas, 6.140 líneas, 1.561 movimientos, 521 pedidos y
  150 cajas, con la misma última venta.

En la Mac, sin pasar por el VPS, con `rclone` configurado solo con las claves del gestor:

- El respaldo de B2 se listó con su nombre legible, se bajó y se restauró en una base aparte del Postgres local.
- El backend arrancó sano contra esa base; el reporte de totales de octubre dio 32 ventas normales por Bs 57.253,74,
  total Bs 58.182,74 y utilidad después de consumos Bs 13.881,98.
- Las ventas de octubre por estado y tipo (cantidad y monto) y los movimientos coinciden exactamente con producción.
