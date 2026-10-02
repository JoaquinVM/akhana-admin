# Historias de Usuario — REQ-0010: Gestión de Productos

### US-01: Listado de Productos con Filtros y Búsqueda
**Como** administrador o vendedor del sistema,  
**Quiero** visualizar la tabla de productos ordenada alfabéticamente con información comercial, categoría, proveedor, etiquetas y utilidades,  
**Para** tener visibilidad clara del catálogo de productos y su rentabilidad.

```gherkin
Scenario: Carga inicial de productos
  Given que el usuario navega a "/products"
  Then el listado muestra los productos activos e inactivos (filtro "Activos/Inactivos")
  And la tabla está ordenada inicialmente por Nombre de forma ascendente (A-Z)
  And cada fila muestra: Código, Nombre, Categoría con su pastilla de color, Proveedor, Etiquetas con sus colores, Precio de compra, Precio de venta, Utilidad (fija y porcentual), Estado y Acciones.

Scenario: Filtrado de productos por estado
  Given que el usuario se encuentra en el listado de productos
  When selecciona el botón de filtro "Activos"
  Then solo se muestran los productos con estado "ACTIVO"
  When selecciona "Inactivos"
  Then solo se muestran los productos con estado "INACTIVO"
  When selecciona "Eliminados"
  Then solo se muestran los productos con estado "ELIMINADO"

Scenario: Búsqueda reactiva por código o nombre
  Given que el usuario ingresa un término en la barra de búsqueda
  When escribe un fragmento del código o del nombre
  Then la tabla se filtra en tiempo real mostrando las coincidencias sin distinguir mayúsculas de minúsculas
  And se combinan los resultados con el filtro de estado activo.
```

---

### US-02: Creación de Producto con Cálculo Automático de Utilidad y Selectores Visuales
**Como** usuario autenticado,  
**Quiero** registrar un nuevo producto seleccionando su categoría con color, su proveedor, etiquetas opcionales y definiendo precios de compra y venta,  
**Para** incorporar nuevos artículos al catálogo asegurando cálculos automáticos de margen y consistencia visual.

```gherkin
Scenario: Apertura de formulario de creación
  Given que el usuario pulsa "+ Nuevo Producto"
  Then se abre el modal de formulario
  And el botón Guardar permanece habilitado
  And el estado por defecto es "ACTIVO"
  And los campos de utilidad fija y utilidad porcentual están en 0.00 y son de solo lectura.

Scenario: Cálculo automático de utilidad
  Given que el usuario ingresa un precio de compra de 40.00
  When ingresa un precio de venta de 50.00
  Then el campo "Utilidad fija" muestra automáticamente "10.00"
  And el campo "Utilidad %" muestra automáticamente "25.00%" sin permitir edición manual.

Scenario: Envío de formulario válido
  Given que el usuario completa todos los campos obligatorios (código, nombre, categoría, proveedor, precios)
  When pulsa "Guardar"
  Then los datos se envían al backend
  And el producto se crea en la base de datos con estado "ACTIVO"
  And la tabla se actualiza inmediatamente mostrando el nuevo producto.

Scenario: Envío de formulario con código o nombre duplicado
  Given que el usuario ingresa un código o nombre ya existente en un producto activo o inactivo
  When pulsa "Guardar"
  Then el backend responde con HTTP 409 Conflict
  And el frontend muestra el mensaje de error de negocio devuelto por el servidor.
```

---

### US-03: Edición de Producto con Dirty-Checking e Indicadores de Modificación
**Como** administrador del sistema,  
**Quiero** modificar los datos de un producto activo o inactivo con detección precisa de cambios,  
**Para** mantener la información del catálogo actualizada sin guardar datos redundantes.

```gherkin
Scenario: Apertura de edición
  Given un producto activo o inactivo en la tabla
  When el usuario pulsa el botón "Editar"
  Then el modal se abre con los valores actuales cargados
  And el botón "Guardar" inicia deshabilitado
  And ningún campo muestra el badge "Modificado".

Scenario: Detección reactiva de cambios
  Given el modal de edición abierto
  When el usuario modifica el precio de venta
  Then el botón "Guardar" se habilita
  And el campo de precio de venta muestra el indicador visual ".field-modified-badge"
  And las utilidades fija y porcentual se recalculan automáticamente
  When el usuario regresa el precio de venta a su valor original
  Then el botón "Guardar" vuelve a deshabilitarse y el indicador desaparece.
```

---

### US-04: Conmutación de Estado y Eliminación Lógica con Confirmación
**Como** administrador del sistema,  
**Quiero** desactivar, reactivar o eliminar lógicamente un producto mediante confirmación,  
**Para** controlar la disponibilidad operativa de los artículos con seguridad.

```gherkin
Scenario: Desactivar producto activo
  Given un producto con estado "ACTIVO"
  When el usuario pulsa la acción de cambiar estado a inactivo
  Then se abre el modal de confirmación
  When confirma la acción
  Then el estado cambia a "INACTIVO" en el backend y se actualiza la tabla.

Scenario: Reactivar producto inactivo
  Given un producto con estado "INACTIVO"
  When el usuario pulsa la acción de activar
  Then se abre el modal de confirmación
  When confirma la acción
  Then el estado vuelve a "ACTIVO".

Scenario: Eliminación lógica
  Given un producto con estado "ACTIVO" o "INACTIVO"
  When el usuario pulsa la acción "Eliminar"
  Then se solicita confirmación
  When confirma la eliminación
  Then el estado cambia a "ELIMINADO"
  And se registran "deleted_by" y "deleted_at"
  And el producto desaparece de la vista activa (permanece accesible en el filtro "Eliminados").
```

---

### US-05: Auditoría Inmutable del Producto
**Como** supervisor o auditor,  
**Quiero** consultar la trazabilidad histórica de un producto en cualquier estado,  
**Para** saber quién y cuándo lo creó, modificó o eliminó.

```gherkin
Scenario: Consultar auditoría de producto activo o eliminado
  Given cualquier producto en la tabla (incluyendo eliminados)
  When el usuario pulsa el icono de reloj de auditoría
  Then se abre el modal de auditoría
  And se muestran únicamente los datos que tienen valor (creación, edición, eliminación)
  And el modal puede cerrarse haciendo clic en el backdrop o en la 'X'.
```
