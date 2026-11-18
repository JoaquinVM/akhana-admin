---
name: ver-en-local
description: Levanta Akhana Admin de esta rama en local, con una base de prueba propia, y se lo deja abierto a Joaquín en el navegador de la app. Úsala cuando diga «mostrame en local», «muéstramelo», «quiero verlo antes de aprobar» o pida ver un cambio con datos de prueba.
---

Todo lo pesado lo hace el script. No investigues cómo arranca la app: córrelo.

## 1. Levantar

Desde la carpeta del trabajo (la principal o su worktree):

    python3 -I .claude/skills/ver-en-local/ver_en_local.py

La primera vez tarda de 1 a 3 minutos porque compila e instala. Después reutiliza lo que ya está corriendo. Imprime un
JSON con `url`, `usuario`, `clave`, `api`, `psql` y `logs`. Cada rama tiene su base `akhana_local_<rama>`, sus puertos
(8090+ y 4290+) y su administrador `prueba` con segundo factor activo. No toca la base de Joaquín, los servidores de
8080 y 4200 ni el ensayo de otra sesión.

Si falla, el script muestra las últimas líneas del log. Arregla eso, no lo rodees.

## 2. Datos de prueba

La base empieza vacía, salvo lo que siembran las migraciones. Carga solo lo que la pantalla del trabajo necesita para
verse con casos reales: dos o tres ejemplos de cada caso, no un catálogo entero.

- **Por la API primero**, así se respetan las reglas del sistema:
  `T=$(python3 -I .claude/skills/ver-en-local/ver_en_local.py --token)` y `curl -H "Authorization: Bearer $T" <api>/...`.
- **SQL solo** para lo que la API no permite (fechas pasadas, cajas históricas), con el comando `psql` del JSON.
- Nunca copies datos de producción ni de la base de Joaquín.

## 3. Abrir

1. Abre la `url` en el navegador de la app (`mcp__Claude_Browser__navigate`).
2. Entra con `usuario` y `clave`. Para el código: `python3 -I .claude/skills/ver-en-local/ver_en_local.py --codigo`
   (cada código sirve una sola vez; el script espera al siguiente si hace falta).
3. Ve a la pantalla del trabajo y déjala en el estado que muestra el cambio. Si es para el teléfono, usa
   `resize_window` con `mobile`.
4. Comprueba tú que se ve lo que se pidió antes de pasárselo.

## 4. Pasárselo a Joaquín

En dos o tres líneas: qué pantalla dejaste abierta, qué datos de prueba cargaste y qué puede tocar. Dale la `url`, el
`usuario` y la `clave` solo si va a entrar desde su propio navegador. Es una base de prueba: puede vender, anular y
borrar sin miedo.

## Al terminar

- Al cerrar el trabajo: `--parar` (para backend y frontend, la base queda) o `--borrar` (también borra la base).
- No dejes servidores corriendo de un trabajo ya mergeado.
