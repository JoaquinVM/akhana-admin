# Spec Delta: user-management

## Purpose

Permite administrar integralmente los usuarios del sistema, configurar sus permisos de acceso granulares por submenú, proteger las rutas y endpoints del frontend y backend, auditar las operaciones de creación, edición y baja lógica mediante seis campos inmutables generados por el servidor, y garantizar la compatibilidad con los usuarios existentes.

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

### Requirement: Creación de nuevos usuarios con permisos iniciales
El sistema SHALL permitir el registro de nuevos usuarios requiriendo obligatoriamente nombre completo, nombre de usuario (único e inmutable), contraseña y confirmación de contraseña coincidente, y selección de permisos de acceso por submenú, almacenando la contraseña mediante hash seguro (BCrypt) y registrando automáticamente la auditoría de creación en el servidor.

#### Scenario: Creación exitosa de nuevo usuario
- **WHEN** se envía el formulario con datos válidos, contraseñas coincidentes y permisos seleccionados
- **THEN** el sistema persiste el usuario en estado activo, hashea la contraseña, guarda exclusivamente los permisos elegidos, registra `created_at` con la fecha del servidor y `created_by` con el usuario autenticado, dejando vacíos los campos de modificación y eliminación

#### Scenario: Rechazo por nombre de usuario duplicado
- **WHEN** se intenta crear un usuario con un nombre de usuario que ya existe en la base de datos
- **THEN** el sistema rechaza la solicitud e informa que el nombre de usuario ya se encuentra registrado

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
El sistema SHALL permitir editar el nombre completo y los permisos de acceso de un usuario existente sin requerir ni modificar su contraseña, implementando el patrón visual y funcional de detección de cambios de la pantalla de productos: botón Guardar deshabilitado inicialmente, habilitado solo ante cambios efectivos y válidos, identificación visual de campos modificados (`.is-modified` y badge de modificado), reversión si se restauran los valores originales, y omisión de auditoría si no hubo modificaciones reales.

#### Scenario: Estado inicial del formulario de edición
- **WHEN** se abre el formulario de edición de un usuario
- **THEN** los campos cargan los valores actuales, ningún campo se resalta como modificado y el botón Guardar permanece deshabilitado

#### Scenario: Identificación de cambios efectivos y habilitación de Guardar
- **WHEN** el usuario modifica el nombre completo o cambia algún permiso respecto a los valores iniciales
- **THEN** el control modificado se resalta con el estilo visual correspondiente, se muestra el badge de modificado y el botón Guardar se habilita si el formulario es válido

#### Scenario: Reversión total a los valores originales
- **WHEN** el usuario regresa todos los campos modificados exactamente a su valor original de apertura
- **THEN** los resaltes visuales se eliminan y el botón Guardar vuelve a deshabilitarse automáticamente

#### Scenario: Actualización de auditoría únicamente con cambios reales
- **WHEN** se guardan cambios efectivos en un usuario
- **THEN** el sistema persiste las modificaciones, actualiza `updated_at` con la hora del servidor y `updated_by` con el usuario autenticado, manteniendo intactos los datos de creación

---

### Requirement: Eliminación lógica y preservación histórica
El sistema SHALL realizar la baja de usuarios mediante eliminación lógica (`deleted_at` y `deleted_by`), solicitando confirmación explícita con el nombre del usuario y advirtiendo las consecuencias, revocando inmediatamente el acceso y sesión del usuario eliminado, prohibiendo que un usuario se elimine a sí mismo y prohibiendo eliminar al último usuario con permisos de administración de usuarios.

#### Scenario: Baja lógica exitosa de usuario
- **WHEN** se confirma la eliminación de un usuario activo que no es el usuario en sesión ni el último administrador
- **THEN** el sistema registra `deleted_at` con la fecha del servidor, `deleted_by` con el usuario autenticado, marca al usuario como inactivo, preserva todas sus ventas y registros históricos, e impide cualquier inicio de sesión futuro

#### Scenario: Rechazo de auto-eliminación
- **WHEN** un usuario autenticado intenta eliminar su propia cuenta de usuario
- **THEN** el sistema bloquea la acción informando que no está permitido eliminarse a sí mismo

#### Scenario: Rechazo de eliminación del último administrador
- **WHEN** se intenta eliminar al único usuario activo que posee el permiso para administrar usuarios
- **THEN** el sistema bloquea la operación advirtiendo que el sistema no puede quedar sin capacidad de administración

---

### Requirement: Matriz de selección de permisos por submenú
El sistema SHALL presentar la matriz de permisos estructurada según los menús y submenús reales de la aplicación (`Ventas`, `Finanzas`, `Catálogo`, `Compras`, `Seguridad`), permitiendo seleccionar o deseleccionar submenús individualmente, por menú completo o de forma global (todos los permisos), visualizando estados seleccionados, no seleccionados y parcialmente seleccionados (indeterminados).

#### Scenario: Selección individual de submenú
- **WHEN** se marca un submenú específico dentro de un menú principal
- **THEN** el permiso del submenú se añade a la selección y el menú principal adopta estado parcial si contiene otros submenús desmarcados

#### Scenario: Toggle grupal por menú principal
- **WHEN** se activa el selector del menú principal
- **THEN** todos los submenús dependientes de dicho menú pasan a estado seleccionado

#### Scenario: Toggle global de todos los permisos
- **WHEN** se utiliza la acción para seleccionar todos los permisos
- **THEN** la totalidad de submenús del sistema quedan seleccionados

---

### Requirement: Control de navegación y filtrado en frontend
El sistema SHALL filtrar reactivamente la navegación del usuario autenticado en el Navbar mostrando únicamente los submenús autorizados y ocultando los menús principales que carezcan de submenús permitidos. Ante intentos de acceso por URL directa no autorizada, el Angular Guard SHALL bloquear la vista y redirigir a la pantalla por defecto permitida, o a una vista de acceso no disponible si no posee permisos.

#### Scenario: Ocultamiento de menús no autorizados
- **WHEN** un usuario con permisos limitados (por ejemplo, solo POS) inicia sesión
- **THEN** el Navbar muestra únicamente las opciones autorizadas y oculta los menús que no tienen ningún submenú permitido

#### Scenario: Bloqueo de acceso por URL directa no autorizada
- **WHEN** un usuario introduce directamente en el navegador una URL hacia una pantalla para la que no tiene permiso
- **THEN** el Guard intercepta la navegación y redirige automáticamente al usuario a su pantalla autorizada predeterminada sin generar ciclos de redirección

#### Scenario: Usuario sin permisos asignados
- **WHEN** un usuario autenticado no cuenta con ningún submenú autorizado en su perfil
- **THEN** el sistema presenta una pantalla informativa de acceso no disponible sin exponer contenido protegido

---

### Requirement: Protección de endpoints y seguridad en backend
El sistema SHALL validar la autorización en cada endpoint del backend de forma independiente del frontend, verificando que el usuario autenticado cuente con el permiso correspondiente a la operación, rechazando solicitudes no autorizadas con código HTTP 403 Forbidden e invalidando inmediatamente los tokens de usuarios que hayan sido dados de baja.

#### Scenario: Rechazo de solicitud sin permiso administrativo
- **WHEN** un usuario sin permiso de administración de usuarios envía una petición HTTP a `/api/users`
- **THEN** el backend responde con código HTTP 403 Forbidden y mensaje de acceso denegado

#### Scenario: Invalidación inmediata de usuario dado de baja
- **WHEN** un usuario cuya cuenta fue dada de baja intenta realizar una petición con un token JWT emitido previamente
- **THEN** el filtro de autenticación consulta el estado en base de datos, detecta la baja lógica y deniega la solicitud con código HTTP 401 Unauthorized

---

### Requirement: Migración de datos inicial y compatibilidad
El sistema SHALL ejecutar una migración de base de datos idempotente que incorpore las columnas de auditoría a `app_users`, cree la tabla `user_permissions` y asigne automáticamente todos los permisos disponibles a los usuarios preexistentes al momento de la migración (incluyendo `admin` y `seller`), preservando sus contraseñas, nombres de usuario y registros de auditoría anteriores.

#### Scenario: Ejecución de migración sobre usuarios existentes
- **WHEN** se aplica la migración V9 en la base de datos
- **THEN** los usuarios existentes reciben acceso a todos los submenús del sistema, se conservan sus hashes de contraseña intactos y quedan habilitados para ingresar normalmente
