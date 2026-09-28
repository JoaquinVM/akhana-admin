# Historias de Usuario - REQ-0008: Gestión de Categorías

## US-01: Listado, Filtrado y Búsqueda de Categorías
**Como** administrador del catálogo,  
**Quiero** visualizar la lista de categorías ordenadas alfabéticamente (A-Z), filtrar por estado (Activos/Eliminados) y buscar por nombre,  
**Para** localizar rápidamente las categorías existentes y consultar su estado y color identificativo.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Carga inicial de categorías
  Dado que el usuario navega a /categories
  Cuando la página carga
  Entonces se muestran las categorías con estado ACTIVO ordenadas de la A a la Z
  Y el filtro "Activos" se encuentra seleccionado por defecto
  Y el color de cada categoría se visualiza gráficamente en la tabla

Escenario: Filtrado por categorías eliminadas
  Dado que el usuario está en el listado de categorías
  Cuando selecciona el filtro "Eliminados"
  Entonces se muestran únicamente las categorías con estado ELIMINADO
  Y los registros eliminados solo muestran la opción de Auditoría (sin Editar ni Eliminar)

Escenario: Búsqueda en tiempo real por nombre
  Dado que el usuario está en el listado de categorías
  Cuando ingresa "ferti" en el campo de búsqueda
  Entonces se muestran únicamente las categorías activas cuyo nombre contenga "ferti" de forma insensible a mayúsculas
```

---

## US-02: Creación de Categoría con Selector Visual de Color
**Como** administrador del catálogo,  
**Quiero** registrar una nueva categoría mediante un modal seleccionando su nombre, descripción y un color visual,  
**Para** clasificar los productos del catálogo de forma visual e intuitiva.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Creación exitosa
  Dado que el usuario abre el modal de "Nueva Categoría"
  Cuando ingresa un nombre válido y selecciona un color mediante el selector visual
  Y pulsa "Crear Categoría"
  Entonces se envía la información al backend
  Y el registro se almacena con estado ACTIVO y datos de auditoría
  Y el modal se cierra y la tabla se actualiza

Escenario: Validación de campos obligatorios con botón Guardar activo
  Dado que el usuario abre el modal de "Nueva Categoría"
  Cuando pulsa "Crear Categoría" sin ingresar el nombre
  Entonces el sistema ejecuta la validación del formulario
  Y se muestra el mensaje de error "El nombre es obligatorio"
  Y no se realiza la llamada al backend

Escenario: Intento de registro con nombre duplicado existente
  Dado que existe una categoría activa con nombre "Sustratos"
  Cuando el usuario intenta crear otra categoría con nombre "sustratos"
  Entonces el backend responde con error 409 Conflict
  Y el modal permanece abierto mostrando el mensaje devuelto por el servidor
```

---

## US-03: Edición de Categoría y Detección de Cambios
**Como** administrador del catálogo,  
**Quiero** editar los datos de una categoría activa existente,  
**Para** mantener actualizada la información del catálogo.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Estado del botón guardar en edición
  Dado que el usuario abre el modal de edición de una categoría activa
  Entonces el botón "Guardar Cambios" inicia deshabilitado
  Cuando el usuario modifica el color o la descripción
  Entonces el botón "Guardar Cambios" se habilita
  Y los campos modificados muestran un indicador visual de modificación
  Cuando el usuario revierte los campos a sus valores iniciales
  Entonces el botón "Guardar Cambios" vuelve a deshabilitarse y los indicadores desaparecen

Escenario: Descarte de cambios con confirmación
  Dado que el usuario modificó algún campo en el modal de edición
  Cuando pulsa la "X" o el botón "Cancelar"
  Entonces se muestra el modal de confirmación "¿Descartar cambios?"
  Si selecciona "Continuar editando", el formulario permanece abierto
  Si selecciona "Descartar cambios", el modal se cierra sin guardar
```

---

## US-04: Eliminación Lógica y Auditoría
**Como** administrador del catálogo,  
**Quiero** eliminar lógicamente una categoría activa y consultar su trazabilidad de auditoría,  
**Para** mantener un registro histórico inmutable de operaciones.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Eliminación lógica con confirmación
  Dado que una categoría está en estado ACTIVO
  Cuando el usuario pulsa el botón Eliminar y confirma la acción en el modal de confirmación
  Entonces el backend actualiza el estado a ELIMINADO junto con deletedBy y deletedAt
  Y el registro desaparece del filtro Activos

Escenario: Consulta de auditoría
  Dado cualquier registro de categoría (ACTIVO o ELIMINADO)
  Cuando el usuario pulsa el botón de Auditoría
  Entonces se abre el modal de auditoría reutilizable
  Y se muestran únicamente los campos que tienen valor
  Y el modal se puede cerrar haciendo clic fuera de él o en la "X"
```
