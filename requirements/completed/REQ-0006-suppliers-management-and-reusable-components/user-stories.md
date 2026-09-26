# Historias de Usuario - REQ-0006: Gestión de Proveedores y Componentes Reutilizables

---

### US-01: Incorporación del Menú Compras y Proveedores
**Como** usuario autenticado en Akhana Admin,  
**quiero** visualizar el grupo "Compras" con la opción "Proveedores" en la barra de navegación superior,  
**para** acceder directamente a la pantalla `/suppliers`.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Navegación al módulo de Proveedores
  Given que el usuario está en cualquier pantalla del sistema
  When pasa el cursor sobre el grupo "Compras"
  Then se despliega la opción "Proveedores"
  When hace clic en "Proveedores"
  Then es redirigido a "/suppliers"
  And el menú desplegable se cierra automáticamente
  And el grupo "Compras" y la opción "Proveedores" se muestran como activos
```

---

### US-02: Listado y Búsqueda de Proveedores
**Como** administrador o encargado de compras,  
**quiero** visualizar la lista de proveedores ordenados alfabéticamente por nombre y buscar por nombre o código,  
**para** encontrar proveedores rápidamente sin ver registros eliminados.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Listado inicial de proveedores
  Given que existen proveedores registrados con estados ACTIVO, INACTIVO y ELIMINADO
  When el usuario accede a "/suppliers"
  Then se muestra una tabla con las columnas: Código, Nombre, Teléfono, Estado y Acciones
  And el listado se encuentra ordenado por Nombre de forma ascendente (A-Z)
  And los proveedores con estado "ELIMINADO" no aparecen en el listado

Scenario: Búsqueda insensible a mayúsculas y minúsculas
  Given que el usuario está en el listado de proveedores
  When escribe un término en la caja de búsqueda (ej. "bio" o "PROV-01")
  Then la tabla filtra en tiempo real mostrando los proveedores cuyo nombre o código coincidan parcialmente sin importar mayúsculas
```

---

### US-03: Creación de Proveedor con Unicidad en Backend
**Como** usuario del sistema,  
**quiero** registrar un nuevo proveedor con sus datos principales,  
**para** incorporarlo al catálogo de compras con estado activo y trazabilidad automática.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Creación exitosa de proveedor
  Given que el usuario abre el formulario de nuevo proveedor
  When ingresa un nombre y código obligatorios no existentes
  And presiona el botón "Guardar"
  Then el backend persiste el proveedor con estado "ACTIVO"
  And registra automáticamente el usuario autenticado como creador y la fecha actual
  And el proveedor se refleja de inmediato en la tabla

Scenario: Rechazo de nombre o código duplicado en backend
  Given que ya existe un proveedor ACTIVO o INACTIVO con código "PROV-100"
  When el usuario intenta crear un nuevo proveedor con código "PROV-100"
  Then el backend rechaza la operación con código de conflicto
  And el frontend muestra el mensaje de error de negocio devuelto por el servidor
```

---

### US-04: Edición de Proveedor
**Como** usuario del sistema,  
**quiero** modificar los datos de un proveedor activo o inactivo,  
**para** mantener actualizada su información de contacto y descripción.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Edición de proveedor activo o inactivo
  Given un proveedor con estado "ACTIVO" o "INACTIVO" en la tabla
  When el usuario hace clic en la acción "Editar"
  Then se abre el formulario precargado con sus datos actuales
  When modifica el teléfono o la descripción y guarda
  Then el backend actualiza los datos y registra el usuario y fecha de modificación
  And la información histórica de creación permanece inalterada
```

---

### US-05: Eliminación Lógica con Modal Reutilizable
**Como** administrador del sistema,  
**quiero** dar de baja un proveedor mediante eliminación lógica previa confirmación,  
**para** retirar el proveedor de la operación activa sin perder su historial.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Confirmación y eliminación lógica
  Given un proveedor en estado "ACTIVO"
  When el usuario hace clic en la acción "Eliminar"
  Then se presenta el componente modal de confirmación reutilizable
  When el usuario confirma la acción
  Then el estado del proveedor cambia a "ELIMINADO" en backend
  And se registran el usuario y fecha de eliminación
  And el registro no se elimina físicamente de la base de datos
  And el proveedor desaparece del listado normal

Scenario: Cancelación de la eliminación
  Given el modal de confirmación abierto para un proveedor
  When el usuario pulsa "Cancelar"
  Then el modal se cierra y el proveedor conserva su estado sin modificaciones
```

---

### US-06: Consulta de Auditoría con Componente Reutilizable
**Como** usuario con permisos de auditoría,  
**quiero** ver la trazabilidad de cualquier proveedor (incluso eliminados),  
**para** conocer quién y cuándo lo creó, modificó o eliminó.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Visualización de campos con valor en el modal de auditoría
  Given un proveedor que fue creado y posteriormente modificado pero no eliminado
  When el usuario hace clic en la acción "Auditoría"
  Then se abre el componente modal de auditoría reutilizable
  And se muestran los campos: Usuario de creación, Fecha de creación, Usuario de edición y Fecha de edición
  And no se muestran campos nulos o vacíos relativos a eliminación
```
