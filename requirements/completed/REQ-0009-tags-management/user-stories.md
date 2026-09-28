# Historias de Usuario — REQ-0009: Gestión de Etiquetas

## US-01: Listado de Etiquetas y Orden Inicial
**Como** administrador del sistema,  
**Quiero** visualizar el catálogo de etiquetas en una tabla ordenada alfabéticamente de la A a la Z,  
**Para** clasificar y localizar rápidamente los atributos o descriptores de productos.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Carga inicial de la pantalla de etiquetas
  Dado que el usuario navega a "/tags"
  Cuando el catálogo de etiquetas es cargado
  Entonces la tabla muestra las columnas Color, Nombre, Estado y Acciones
  Y el filtro "Activos" se encuentra seleccionado por defecto
  Y el listado se muestra ordenado alfabéticamente por Nombre en sentido ascendente (A-Z)
  Y el color se visualiza mediante una píldora estética con el círculo cromático y código hexadecimal
```

---

## US-02: Filtro por Estado (Activos / Eliminados)
**Como** usuario del sistema,  
**Quiero** filtrar el listado de etiquetas entre "Activos" y "Eliminados",  
**Para** consultar el catálogo operativo o auditar registros descontinuados.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Cambio al filtro de etiquetas eliminadas
  Dado que el usuario está en "/tags" con el filtro "Activos"
  Cuando hace clic en el botón de filtro "Eliminados"
  Entonces se muestran únicamente las etiquetas con estado "ELIMINADO"
  Y las filas muestran el badge de estado "Eliminado"
  Y las acciones disponibles para cada fila se limitan exclusivamente a "Consultar Auditoría"
```

---

## US-03: Búsqueda en Tiempo Real
**Como** usuario,  
**Quiero** filtrar etiquetas escribiendo parte de su nombre en el buscador,  
**Para** encontrar descriptores específicos al instante.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Búsqueda parcial de etiquetas por nombre
  Dado que el usuario está en el listado de etiquetas
  Cuando escribe un término de búsqueda en la barra de texto
  Entonces la tabla filtra inmediatamente las etiquetas cuyo nombre contenga el término
  Y la búsqueda es insensible a mayúsculas y minúsculas
  Y se actualiza el contador de etiquetas encontradas
```

---

## US-04: Creación de Etiqueta con Selector de Color
**Como** administrador,  
**Quiero** registrar una nueva etiqueta seleccionando su color visualmente en un modal,  
**Para** enriquecer el catálogo sin necesidad de recordar códigos hexadecimales.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Creación exitosa de una etiqueta
  Dado que el usuario abre el modal "Nueva Etiqueta"
  Cuando ingresa un nombre válido y selecciona un color en la paleta visual
  Y presiona "Crear Etiqueta"
  Entonces se envía la petición al backend
  Y el registro se persiste con estado "ACTIVO" y datos de auditoría
  Y el modal se cierra y la etiqueta aparece en el listado ordenado A-Z

Escenario: Intento de guardar con campos requeridos vacíos
  Dado que el usuario abre el modal "Nueva Etiqueta"
  Cuando presiona "Crear Etiqueta" sin completar los campos
  Entonces el botón ejecuta la validación, marcando los campos requeridos en rojo
  Y no se envía la petición al backend
```

---

## US-05: Unicidad de Nombre y Manejo de Conflictos
**Como** sistema,  
**Quiero** rechazar etiquetas activas con nombres duplicados a nivel de backend,  
**Para** evitar inconsistencias garantizando la integridad de datos.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Conflicto de nombre duplicado con etiqueta activa
  Dado que ya existe una etiqueta activa llamada "Vegano"
  Cuando el usuario intenta crear o actualizar otra etiqueta con el nombre "vegano"
  Entonces el backend responde con código HTTP 409 Conflict
  Y el modal permanece abierto mostrando el mensaje devuelto por el servidor

Escenario: Reutilización de nombre de etiqueta eliminada
  Dado que existe una etiqueta llamada "Oferta" con estado "ELIMINADO"
  Cuando el usuario crea una nueva etiqueta con el nombre "Oferta"
  Entonces el backend procesa la creación exitosamente con código HTTP 201 Created
```

---

## US-06: Edición con Detección de Cambios (Dirty Checking)
**Como** usuario,  
**Quiero** editar una etiqueta activa visualizando qué campos he modificado,  
**Para** asegurar que no realizo actualizaciones innecesarias o accidentales.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Comportamiento reactivo del botón Guardar en edición
  Dado que el usuario abre el modal "Editar Etiqueta"
  Entonces los campos cargan los valores actuales y "Guardar Cambios" inicia deshabilitado
  Cuando el usuario cambia el nombre o el color
  Entonces "Guardar Cambios" se habilita y el campo muestra la etiqueta "Modificado"
  Cuando el usuario regresa el campo a su valor original
  Entonces "Guardar Cambios" vuelve a deshabilitarse
```

---

## US-07: Descarte de Cambios al Cerrar Modal
**Como** usuario,  
**Quiero** ser advertido si intento cerrar el formulario teniendo cambios sin guardar,  
**Para** prevenir la pérdida accidental de datos ingresados.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Descarte de cambios confirmatorio
  Dado que el usuario modificó algún campo en el modal de etiqueta
  Cuando pulsa "Cancelar" o el botón "X"
  Entonces el modal de formulario no se cierra y se despliega el diálogo "¿Descartar cambios?"
  Cuando el usuario selecciona "Descartar cambios"
  Entonces ambos modales se cierran y se descartan las modificaciones
```

---

## US-08: Eliminación Lógica y Auditoría
**Como** administrador,  
**Quiero** eliminar lógicamente una etiqueta y auditar en cualquier momento su ciclo de vida,  
**Para** preservar la trazabilidad histórica de los productos clasificados.

### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Eliminación lógica con confirmación
  Dado que el usuario presiona "Eliminar" en una etiqueta activa
  Cuando confirma en el diálogo de eliminación
  Entonces el backend actualiza su estado a "ELIMINADO", "deleted_by" y "deleted_at"
  Y el registro desaparece del filtro "Activos" y se encuentra en "Eliminados"

Escenario: Consulta de auditoría con icono unificado
  Dado que el usuario presiona el icono de reloj de auditoría en cualquier etiqueta
  Entonces se abre el modal de auditoría desplegando usuario y fecha de creación, edición o eliminación
  Y el modal se cierra al hacer clic fuera del backdrop
```
