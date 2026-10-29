# Spec Delta: user-management

## Purpose

Permite administrar integralmente los usuarios del sistema, asignarles un rol (Operador o Administrador) que agrupa los permisos por pantalla de la tabla 2.2 de `requirements.md` (USR-05, P-33), proteger las rutas y endpoints del frontend y backend, auditar las operaciones de creación, edición y baja lógica mediante seis campos inmutables generados por el servidor, y garantizar la compatibilidad con los usuarios existentes.

## ADDED Requirements

### Requirement: Listado y consulta de usuarios
El sistema SHALL presentar una tabla de usuarios en Seguridad → Usuarios (`/users`) con paginación, búsqueda por nombre completo y nombre de usuario, visualizando nombre completo, nombre de usuario, fecha/usuario de creación, fecha/usuario de modificación (vacíos si no hubo cambios), y las acciones exclusivas: "Ver auditoría", "Editar" y "Eliminar".

#### Scenario: Consulta del listado paginado y búsqueda
- **WHEN** el usuario autorizado accede a Seguridad → Usuarios e introduce un término en el buscador
- **THEN** el sistema filtra los usuarios activos o modificados cuyo nombre completo o nombre de usuario coincida con el término y los muestra paginados en la tabla con sus columnas de auditoría

#### Scenario: Visualización de campos de modificación en usuarios sin cambios
- **WHEN** un usuario registrado no ha sido modificado desde su creación
- **THEN** la tabla muestra vacías las columnas de fecha de modificación y usuario de modificación

---

### Requirement: Consulta independiente de auditoría de usuario
El sistema SHALL proveer la acción «Ver auditoría» en cada fila de usuario para consultar de forma independiente sus seis campos de auditoría (`createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`) reutilizando el componente estándar de auditoría del sistema (`AuditModalComponent`), los estilos, iconos y patrones de interacción de la pantalla de productos, sin implementar vistas ni modales de detalle adicionales.

#### Scenario: Consulta de auditoría de usuario
- **WHEN** el usuario pulsa la acción "Ver auditoría" (botón `.btn-audit` con icono de reloj) sobre un registro de la tabla
- **THEN** el sistema abre el modal estándar de auditoría exhibiendo el título "Auditoría de Usuario", el subtítulo con el nombre y usuario del registro, y el desglose de fechas y responsables de creación, modificación y eliminación (si aplica)

---

### Requirement: Creación de nuevos usuarios con rol
El sistema SHALL permitir el registro de nuevos usuarios requiriendo obligatoriamente nombre completo, nombre de usuario (único e inmutable), contraseña y confirmación de contraseña coincidente, y un rol (Operador por defecto, o Administrador), almacenando la contraseña mediante hash seguro (BCrypt) y registrando automáticamente la auditoría de creación en el servidor.

#### Scenario: Creación exitosa de nuevo usuario
- **WHEN** se envía el formulario con datos válidos, contraseñas coincidentes y un rol elegido
- **THEN** el sistema persiste el usuario en estado activo con ese rol, hashea la contraseña, registra `created_at` con la fecha del servidor y `created_by` con el usuario autenticado, dejando vacíos los campos de modificación y eliminación

#### Scenario: Rechazo por nombre de usuario duplicado
- **WHEN** se intenta crear un usuario con un nombre de usuario que ya existe en la base de datos
- **THEN** el sistema rechaza la solicitud e informa que el nombre de usuario ya se encuentra registrado

#### Scenario: Nombre de usuario que solo cambia en mayúsculas y contraseña al crear (segunda revisión, 29-10-2026)
- **WHEN** existe `operador_qa` (activo o dado de baja) y se crea `Operador_QA`, o la contraseña nueva tiene menos de 8 caracteres o es igual al nombre de usuario
- **THEN** el servidor rechaza el alta: el nombre de usuario es único sin distinguir mayúsculas («El nombre de usuario 'Operador_QA' ya está registrado.», 409) y la contraseña sigue la misma regla que el cambio de contraseña (8 a 100 caracteres, distinta del nombre de usuario). El formulario pide 8 caracteres. Pruebas en `UserServiceTest`.

#### Scenario: Rechazo por rol ausente
- **WHEN** se intenta crear o editar un usuario sin indicar el rol
- **THEN** el backend responde 400 y no guarda cambios

#### Scenario: Rechazo por discrepancia en confirmación de contraseña
- **WHEN** la confirmación de contraseña no coincide exactamente con la contraseña ingresada
- **THEN** el sistema bloquea el envío y muestra el mensaje de error de validación correspondiente

---

### Requirement: Inmutabilidad estricta del nombre de usuario
El sistema SHALL garantizar que el nombre de usuario establecido durante la creación sea completamente inmutable en el frontend (mostrándolo como solo lectura) y en el backend (rechazando o ignorando cualquier intento de actualización de dicho campo en los endpoints de edición), con el fin de preservar la integridad referencial de auditoría de todas las entidades del negocio.

#### Scenario: Visualización de solo lectura en edición
- **WHEN** se abre el formulario de edición de un usuario
- **THEN** el campo nombre de usuario se presenta bloqueado, deshabilitado y como solo lectura

#### Scenario: Protección en endpoint de actualización
- **WHEN** se envía una solicitud de actualización intentando modificar el nombre de usuario
- **THEN** el backend no altera el nombre de usuario existente y procesa únicamente los campos permitidos o rechaza la operación si viola la inmutabilidad

---

### Requirement: Edición de usuario y detección ergonómica de cambios
El sistema SHALL permitir editar el nombre completo y el rol de un usuario existente sin requerir ni modificar su contraseña, implementando el patrón visual y funcional de detección de cambios de la pantalla de productos: botón Guardar deshabilitado inicialmente, habilitado solo ante cambios efectivos y válidos, identificación visual de campos modificados (`.is-modified` y badge de modificado), reversión si se restauran los valores originales, y omisión de auditoría si no hubo modificaciones reales.

#### Scenario: Estado inicial del formulario de edición
- **WHEN** se abre el formulario de edición de un usuario
- **THEN** los campos cargan los valores actuales, ningún campo se resalta como modificado y el botón Guardar permanece deshabilitado

#### Scenario: Identificación de cambios efectivos y habilitación de Guardar
- **WHEN** el usuario modifica el nombre completo o cambia el rol respecto a los valores iniciales
- **THEN** el control modificado se resalta con el estilo visual correspondiente, se muestra el badge de modificado y el botón Guardar se habilita si el formulario es válido

#### Scenario: Reversión total a los valores originales
- **WHEN** el usuario regresa todos los campos modificados exactamente a su valor original de apertura
- **THEN** los resaltes visuales se eliminan y el botón Guardar vuelve a deshabilitarse automáticamente

#### Scenario: Rechazo al quitar el rol al último administrador
- **WHEN** se cambia a Operador al único administrador activo
- **THEN** el backend rechaza con 400 informando que no se puede quitar el rol de administrador al último administrador activo

#### Scenario: Actualización de auditoría únicamente con cambios reales
- **WHEN** se guardan cambios efectivos en un usuario
- **THEN** el sistema persiste las modificaciones, actualiza `updated_at` con la hora del servidor y `updated_by` con el usuario autenticado, manteniendo intactos los datos de creación

---

### Requirement: Eliminación lógica y preservación histórica
El sistema SHALL realizar la baja de usuarios mediante eliminación lógica (`deleted_at` y `deleted_by`), solicitando confirmación explícita con el nombre del usuario y advirtiendo las consecuencias, revocando inmediatamente el acceso y sesión del usuario eliminado, prohibiendo que un usuario se elimine a sí mismo y prohibiendo eliminar al último administrador activo.

#### Scenario: Baja lógica exitosa de usuario
- **WHEN** se confirma la eliminación de un usuario activo que no es el usuario en sesión ni el último administrador
- **THEN** el sistema registra `deleted_at` con la fecha del servidor, `deleted_by` con el usuario autenticado, marca al usuario como inactivo, preserva todas sus ventas y registros históricos, e impide cualquier inicio de sesión futuro

#### Scenario: Rechazo de auto-eliminación
- **WHEN** un usuario autenticado intenta eliminar su propia cuenta de usuario
- **THEN** el sistema bloquea la acción informando que no está permitido eliminarse a sí mismo

#### Scenario: Rechazo de eliminación del último administrador
- **WHEN** se intenta eliminar al único administrador activo
- **THEN** el sistema bloquea la operación advirtiendo que el sistema no puede quedar sin capacidad de administración. Desde el 29-10-2026 la cuenta de administradores activos bloquea sus filas (`findActiveByRoleForUpdate`, en orden de id): si dos administradores se dan de baja o se pasan a Operador a la vez, el segundo espera al primero y vuelve a contar, así nunca quedan los dos fuera. Prueba concurrente en `SerializationLockIntegrationTest`.

#### Scenario: Sesión que terminó
- **WHEN** el servidor rechaza el token de una sesión abierta (venció o se cambió la contraseña en otro equipo)
- **THEN** el frontend vuelve al login y muestra «Su sesión terminó: venció o se cambió la contraseña en otro equipo. Vuelva a ingresar.», una sola vez. Al crear un usuario, la respuesta ya trae la fecha de creación. Pruebas en `auth.interceptor.spec.ts`, `login.component.spec.ts` y `UserServiceTest`.

---

### Requirement: Roles con permisos por pantalla (USR-05, T-08, P-33)
El sistema SHALL tener un único modelo de autorización: cada usuario tiene un rol, Operador o Administrador, y el rol agrupa permisos por pantalla. Quien entra a una pantalla puede hacer todo en ella, incluidas sus anulaciones. Los permisos no se eligen por usuario. Las claves son las mismas en el backend (`Permission`, `Role`) y en el frontend (`PERMISSIONS` en `navigation.config.ts`) y coinciden con las entradas del menú.

| Permiso | Pantalla y lo que permite | Operador | Administrador |
| --- | --- | --- | --- |
| `POS` | POS: abrir y cerrar caja, registrar y anular ventas, registrar y anular gastos de caja, productos rápidos | Sí | Sí |
| `SALES_HISTORY` | Historial de ventas | Sí | Sí |
| `CASH_HISTORY` | Historial de cajas y su detalle | Sí | Sí |
| `PRODUCTS` | Productos, con costo y precio de venta | Sí | Sí |
| `CATEGORIES` | Categorías de productos | Sí | Sí |
| `TAGS` | Etiquetas | Sí | Sí |
| `SUPPLIERS` | Proveedores | Sí | Sí |
| `FINANCES_SUMMARY` | Resumen de finanzas | No | Sí |
| `FINANCES_TRANSACTIONS` | Ingresos, gastos y transferencias manuales, con su anulación | No | Sí |
| `FINANCES_ACCOUNTS` | Cuentas | No | Sí |
| `FINANCES_CATEGORIES` | Categorías financieras | No | Sí |
| `USERS` | Usuarios y sus roles | No | Sí |

Cada endpoint exige el permiso de la pantalla dueña; si otra pantalla solo lo lee, también acepta esa pantalla (por ejemplo, Productos lee proveedores, categorías y etiquetas; el POS lee productos y categorías de gasto). Las pantallas nuevas (compras, consumos, reportes) agregan su permiso a este cuadro.

#### Scenario: Selección de rol en el formulario
- **WHEN** se abre el formulario de creación o edición de usuario
- **THEN** se elige un único rol entre Operador y Administrador, cada uno con la descripción de lo que permite

#### Scenario: Permisos derivados del rol
- **WHEN** un usuario inicia sesión o consulta `/api/auth/me`
- **THEN** la respuesta trae su rol y la lista de permisos por pantalla que ese rol otorga

#### Scenario: Consulta de roles
- **WHEN** un administrador consulta `GET /api/users/roles`
- **THEN** recibe cada rol con sus permisos

---

### Requirement: Control de navegación y filtrado en frontend
El sistema SHALL filtrar reactivamente la navegación del usuario autenticado en el Navbar mostrando únicamente los submenús autorizados y ocultando los menús principales que carezcan de submenús permitidos. Ante intentos de acceso por URL directa no autorizada, el Angular Guard SHALL bloquear la vista y redirigir a la pantalla por defecto permitida, o a una vista de acceso no disponible si no posee permisos.

#### Scenario: Ocultamiento de menús no autorizados
- **WHEN** un operador inicia sesión
- **THEN** el Navbar muestra Ventas, Catálogo y Compras, y oculta Finanzas y Seguridad

#### Scenario: Bloqueo de acceso por URL directa no autorizada
- **WHEN** un usuario introduce directamente en el navegador una URL hacia una pantalla para la que no tiene permiso
- **THEN** el Guard intercepta la navegación y redirige automáticamente al usuario a su pantalla autorizada predeterminada sin generar ciclos de redirección

#### Scenario: Usuario sin permisos asignados
- **WHEN** un usuario autenticado no cuenta con ningún submenú autorizado en su perfil
- **THEN** el sistema presenta una pantalla informativa de acceso no disponible sin exponer contenido protegido

---

### Requirement: Protección de endpoints y seguridad en backend
El sistema SHALL validar la autorización en cada endpoint del backend de forma independiente del frontend: cada endpoint lleva `@PreAuthorize` con el permiso de su pantalla (T-13), rechazando solicitudes no autorizadas con código HTTP 403 Forbidden y el mensaje «No tiene permiso para realizar esta acción», e invalidando inmediatamente los tokens de usuarios que hayan sido dados de baja.

#### Scenario: Rechazo de solicitud sin permiso administrativo
- **WHEN** un operador envía una petición HTTP a `/api/users` o a un endpoint de finanzas que el POS no usa
- **THEN** el backend responde con código HTTP 403 Forbidden y mensaje de acceso denegado

#### Scenario: Anulación de gasto desde el POS
- **WHEN** desde el POS se anula un gasto con `POST /api/finances/transactions/cash-expense/{id}/void`
- **THEN** se anula si es un gasto de caja de la caja abierta; si es otro movimiento o la caja está cerrada, el backend responde 400

#### Scenario: Acceso del administrador
- **WHEN** un administrador llama a cualquier endpoint
- **THEN** el backend no lo rechaza por permisos

#### Scenario: Endpoint sin permiso declarado
- **WHEN** se agrega un endpoint sin `@PreAuthorize`
- **THEN** la prueba `EndpointAuthorizationTest` falla, porque exige el permiso y la fila de la matriz para cada endpoint

#### Scenario: Invalidación inmediata de usuario dado de baja
- **WHEN** un usuario cuya cuenta fue dada de baja intenta realizar una petición con un token JWT emitido previamente
- **THEN** el filtro de autenticación consulta el estado en base de datos, detecta la baja lógica y deniega la solicitud con código HTTP 401 Unauthorized

---

### Requirement: Migración a roles (V3)
El sistema SHALL pasar los usuarios existentes al modelo de roles con la migración `V3__operator_administrator_roles.sql`, sin tocar contraseñas, nombres de usuario ni auditoría.

#### Scenario: Ejecución de la migración V3
- **WHEN** se aplica `V3__operator_administrator_roles.sql`
- **THEN** los usuarios con rol `ADMIN` o con el permiso `SECURITY_USERS` pasan a Administrador, el resto a Operador, la columna `role` solo admite `OPERATOR` y `ADMINISTRATOR`, y se elimina la tabla `user_permissions`

#### Scenario: Sesión guardada con el modelo anterior
- **WHEN** el navegador tiene guardada una sesión con rol `ADMIN` o `SELLER`
- **THEN** el frontend la descarta y pide ingresar de nuevo

---

### Requirement: Primer administrador y cambio de contraseña (USR-06)
El sistema SHALL crear el primer administrador solo cuando no existe ningún usuario, con usuario y contraseña tomados de `ADMIN_USERNAME` y `ADMIN_PASSWORD`, marcado para cambiar la contraseña en su primer ingreso, y SHALL permitir que un usuario autenticado cambie su contraseña validando las reglas en el backend.

#### Scenario: Primer arranque sin usuarios
- **WHEN** el backend arranca con la tabla de usuarios vacía y `ADMIN_USERNAME`/`ADMIN_PASSWORD` definidas
- **THEN** crea un único usuario activo con rol Administrador y `must_change_password = true`; no crea ningún operador

#### Scenario: Arranque sin usuarios y sin variables del admin
- **WHEN** la tabla de usuarios está vacía y falta `ADMIN_USERNAME` o `ADMIN_PASSWORD`, o la contraseña tiene menos de 8 caracteres
- **THEN** el arranque falla con un mensaje que nombra las variables

#### Scenario: Arranque con usuarios existentes
- **WHEN** ya existe al menos un usuario
- **THEN** no se crea ni modifica ningún usuario, aunque las variables del admin no estén definidas

#### Scenario: Primer ingreso con cambio obligatorio
- **WHEN** un usuario con `must_change_password` inicia sesión
- **THEN** el login responde con `mustChangePassword: true`, el frontend muestra la pantalla de cambio de contraseña y el backend responde 403 `PasswordChangeRequired` a toda llamada distinta de `/api/auth/me` y `/api/auth/change-password`

#### Scenario: Cambio de contraseña válido
- **WHEN** el usuario envía su contraseña actual correcta y una nueva de 8 a 100 caracteres, distinta de la actual y del nombre de usuario
- **THEN** se guarda el nuevo hash, se quita la marca de cambio obligatorio, se registra `updated_by` y el usuario puede usar el sistema

#### Scenario: Cambio de contraseña rechazado
- **WHEN** la contraseña actual no coincide, o la nueva no cumple las reglas
- **THEN** el backend responde 400 con el motivo y la contraseña no cambia

### Requirement: Límite de intentos de login (USR-07, T-19)
El sistema SHALL contar los intentos fallidos de login por nombre de usuario (sin distinguir mayúsculas ni espacios) y por IP de la conexión, y SHALL rechazar nuevos intentos durante un bloqueo cuando se supera el límite dentro de la ventana. Valores por defecto: 5 fallos por usuario o 20 por IP en 15 minutos, bloqueo de 15 minutos, configurables en `application.properties` (`security.login.*`). El contador vive en memoria (un solo servidor).

#### Scenario: Bloqueo tras N fallos
- **WHEN** un usuario acumula 5 fallos dentro de 15 minutos
- **THEN** el quinto fallo y todo intento siguiente, incluso con la contraseña correcta, responden 429 `Too Many Requests` con "Demasiados intentos fallidos. Espere 15 minutos e intente nuevamente." y no se valida la contraseña ni se genera token

#### Scenario: Desbloqueo al pasar el tiempo
- **WHEN** termina el bloqueo, o pasan 15 minutos desde el primer fallo sin llegar al límite
- **THEN** el contador se olvida y el usuario puede ingresar con normalidad

#### Scenario: Login exitoso reinicia el contador
- **WHEN** un usuario con fallos previos (sin bloqueo) ingresa con la contraseña correcta
- **THEN** su contador vuelve a cero; el contador de la IP no se reinicia

#### Scenario: Bloqueo por IP con usuarios distintos
- **WHEN** desde una misma IP fallan 20 intentos dentro de 15 minutos, aunque sean con usuarios distintos
- **THEN** esa IP queda bloqueada para cualquier usuario; desde otra IP el usuario puede ingresar

#### Scenario: Usuario inexistente
- **WHEN** se intenta ingresar con un usuario que no existe, inactivo o dado de baja
- **THEN** el fallo cuenta igual y las respuestas (401 genérico y luego 429) son las mismas que para un usuario existente

#### Scenario: IP detrás de un proxy
- **WHEN** el pedido trae `X-Forwarded-For` y `FORWARD_HEADERS_STRATEGY` no está en `native`
- **THEN** se usa la IP de la conexión y el encabezado se ignora; con `native` (solo detrás del proxy del VPS) Tomcat lo acepta únicamente desde direcciones internas

#### Scenario: Mensaje en la pantalla de login
- **WHEN** el login responde 429
- **THEN** la pantalla de login muestra el mensaje del backend y deja el formulario habilitado

#### Scenario: Duración del token
- **WHEN** un usuario inicia sesión
- **THEN** el token dura 12 h por defecto (`JWT_EXPIRATION`); al vencer, el siguiente pedido responde 401 y el frontend vuelve al login

### Tareas
- [x] USR-07 / T-19: `LoginAttemptService` en memoria (usuario e IP), 429 `LoginBlockedException`, `security.login.*`, IP sin confiar en `X-Forwarded-For`, token de 12 h configurable, mensaje del backend en `/login` (25-10-2026).
- [x] USR-06: migración `V2__user_must_change_password.sql`, `DataInitializer`, `POST /api/auth/change-password`, bloqueo en `JwtAuthenticationFilter`, pantalla `/change-password` (T-03, 25-10-2026).
- [x] USR-05 / T-08 / T-13: roles Operador y Administrador con permisos por pantalla (`Permission`, `Role`), migración `V3__operator_administrator_roles.sql`, `@PreAuthorize` por pantalla en todos los controllers, 403 con mensaje en `GlobalExceptionHandler`, `EndpointAuthorizationTest` (matriz de todos los endpoints), selector de rol en `/users`, `PERMISSIONS` en `navigation.config.ts` (25-10-2026).
- [x] P-33: permisos por pantalla; el POS anula gastos con `POST /api/finances/transactions/cash-expense/{id}/void` (25-10-2026).


## Notas de implementación

Detalle de cómo se construyó cada requerimiento, movido desde `requirements.md` el 29-10-2026 sin cambios.

- **USR-05** Dos roles iniciales, Operador y Administrador, con los permisos de la tabla 2.2. Hecha el 25-10-2026: el rol se elige al crear o editar el usuario (ya no hay matriz por submenú) y agrupa permisos por pantalla definidos en código (`Permission`, `Role`; detalle en `specs/users.md`). Quien entra a una pantalla puede hacer todo en ella (P-33). El operador entra a POS, historial de ventas, historial de cajas, productos, categorías, etiquetas, proveedores y pedidos (`PURCHASES`, desde el 25-10-2026); el administrador, además, a finanzas y usuarios. No se puede quitar el rol ni dar de baja al último administrador activo.
- **T-03** · V1 — Hallazgo: Secreto JWT por defecto y contraseña de base escritos en `application.properties`. El README ya no documenta contraseñas, pero `DataInitializer` crea en cada arranque `admin`/`12345admin` y `seller`/`12345seller` si no existen, y el vendedor recibe todos los permisos. Requerimiento: Secretos solo por variables de entorno; el primer admin se crea con contraseña obligatoria de cambiar. Hecha el 25-10-2026: `DB_PASSWORD` y `JWT_SECRET` (mín. 32 caracteres) solo vienen del entorno; si faltan, el arranque falla antes de crear beans con un mensaje que nombra cada variable (`RequiredEnvironmentValidator`). Para desarrollo, `.env.example` en la raíz: copiado a `.env` (ignorado por git) lo leen `docker compose` y el backend (`spring.config.import`); las variables reales del entorno tienen prioridad. `DataInitializer` ya no crea `admin` ni `seller`: solo si no hay ningún usuario crea el primer admin con `ADMIN_USERNAME`/`ADMIN_PASSWORD` (mín. 8 caracteres; si faltan, el arranque falla) y queda con `must_change_password` (migración `V2__user_must_change_password.sql`). Ver USR-06. Las bases de desarrollo que ya tenían `admin`/`seller` los conservan: cambiar o dar de baja esas cuentas a mano.
- **T-08** · V1 — Hallazgo: Rol `ADMIN/SELLER` convive con la matriz de permisos; `UserController` acepta `hasRole('ADMIN')` como atajo. Requerimiento: Un solo modelo: roles Operador y Administrador que agrupan permisos. Hecha el 25-10-2026: `Role` (`OPERATOR`, `ADMINISTRATOR`) agrupa el enum `Permission`, con un permiso por pantalla (P-33); las autoridades de Spring son los permisos del rol y se quitó `hasRole('ADMIN')`. Migración `V3__operator_administrator_roles.sql`: `ADMIN` o quien tenía `SECURITY_USERS` pasa a Administrador, el resto a Operador, y se elimina `user_permissions`. Las bases de desarrollo con el `seller` heredado (tenía todos los permisos) lo dejan como Administrador: pasarlo a Operador o darlo de baja a mano.
- **T-13** · V1 — Hallazgo: Solo `UserController` tiene `@PreAuthorize`. Cualquier usuario autenticado puede llamar por API a finanzas, anulación de ventas, catálogo y caja, aunque el menú se lo oculte. Requerimiento: Cada endpoint exige el permiso de la tabla 2.2 (USR-02, USR-05), con pruebas de acceso denegado para el operador. Hecha el 25-10-2026: `@PreAuthorize` en todos los controllers con el permiso de la pantalla dueña del endpoint; los endpoints que otra pantalla solo lee también aceptan esa pantalla (Productos lee proveedores, categorías y etiquetas; el POS lee productos y categorías de gasto). Un 403 responde «No tiene permiso para realizar esta acción». El POS anula gastos con `POST /api/finances/transactions/cash-expense/{id}/void`, que solo acepta gastos de caja de la caja abierta; la anulación general de movimientos sigue siendo de Finanzas. `EndpointAuthorizationTest` recorre todos los endpoints: 2xx para el administrador, 403 para el operador en finanzas y usuarios, y falla si un endpoint no tiene `@PreAuthorize` o falta en la matriz. Frontend con las mismas claves (`PERMISSIONS` en `navigation.config.ts`, rutas y `permission.guard.ts`).
- **T-19** · V1 — Hallazgo: El login no limita intentos fallidos y el token dura 24 h. La baja de usuario sí corta el acceso al instante (el filtro consulta el usuario en cada pedido). Requerimiento: Límite de intentos de login por usuario e IP antes de exponer el VPS (NF-02, NF-05). Hecha el 25-10-2026: ver USR-07. Contador en memoria (`LoginAttemptServiceImpl`): hay un solo servidor en el VPS y perder los contadores al reiniciar solo acorta un bloqueo, así que no hace falta tabla. Por defecto 5 fallos por usuario o 20 por IP en 15 minutos bloquean 15 minutos (`security.login.*`, variables `LOGIN_MAX_ATTEMPTS_PER_USER`, `LOGIN_MAX_ATTEMPTS_PER_IP`, `LOGIN_WINDOW`, `LOGIN_LOCKOUT`); el límite por IP es mayor porque los equipos de la tienda comparten una IP pública. La IP es la de la conexión (`getRemoteAddr`); `X-Forwarded-For` se ignora salvo con `FORWARD_HEADERS_STRATEGY=native`, que se activa solo detrás del proxy inverso del VPS (Tomcat confía en el encabezado solo desde direcciones internas). El token pasa de 24 h a 12 h por defecto (`JWT_EXPIRATION`, acepta `12h` o milisegundos): cubre un turno completo sin pedir otro ingreso y vence durante la noche; al vencer, el frontend vuelve al login. Desde el 29-10-2026 cambiar la contraseña cierra las otras sesiones del usuario: `app_users.password_changed_at` (migración `V19__password_changed_at.sql`) guarda el instante del cambio, el token lleva `iatMs` (el emitido, con milisegundos) y el filtro rechaza los tokens emitidos antes del cambio (401, el frontend vuelve al login). `POST /api/auth/change-password` responde 200 con `{ token }`, el nuevo con el que sigue la sesión que cambió la contraseña, y el frontend lo guarda. Pruebas en `AuthControllerTest`, `JwtAuthenticationIntegrationTest` y `auth.service.spec.ts`.
