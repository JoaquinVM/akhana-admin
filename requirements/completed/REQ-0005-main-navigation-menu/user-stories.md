# Historias de Usuario - REQ-0005: Menú de Navegación Principal

---

### US-01: Barra de Navegación Superior Compacta
**Como** usuario autenticado en Akhana Admin,  
**quiero** visualizar un menú horizontal en la parte superior con los grupos de funcionalidades,  
**para** navegar de manera clara e intuitiva sin que un sidebar lateral reduzca mi espacio de trabajo.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Visualización de grupos en la barra superior
  Given que el usuario está autenticado en la aplicación
  When se carga cualquier sección del sistema administrativo
  Then se visualiza la barra horizontal superior ocupando solo el espacio vertical necesario
  And se aprecian los grupos principales: "Ventas", "Catálogo" y "Seguridad" en una sola fila
  And no existe ningún sidebar lateral
```

---

### US-02: Menús Desplegables al Pasar el Cursor (Hover)
**Como** usuario del sistema,  
**quiero** que al posicionar el cursor sobre un grupo aparezcan sus opciones en un menú flotante,  
**para** acceder rápidamente a la sección deseada sin clics innecesarios.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Despliegue de opciones al hacer hover
  Given que el usuario está visualizando la barra de navegación superior
  When posiciona el puntero del ratón sobre el grupo "Ventas"
  Then se despliega suavemente un submenú con las opciones "POS" y "Ventas"
  When posiciona el puntero sobre "Catálogo"
  Then se despliega el submenú con "Productos", "Categorías" y "Etiquetas"
  When posiciona el puntero sobre "Seguridad"
  Then se despliega el submenú con "Usuarios"

Scenario: Cierre automático al retirar el cursor
  Given que el submenú de un grupo se encuentra visible
  When el usuario mueve el puntero fuera del grupo y de la lista desplegable
  Then el submenú se oculta automáticamente
```

---

### US-03: Navegación y Ruta por Defecto (/pos)
**Como** usuario administrativo o vendedor,  
**quiero** que el sistema inicie por defecto en el punto de venta (/pos) y me permita navegar a las 6 rutas establecidas,  
**para** empezar a operar de inmediato en la sección principal y cambiar de contexto con facilidad.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Redirección automática a la ruta inicial
  Given que el usuario autenticado ingresa a la raíz "/" del sistema
  When la aplicación resuelve el enrutamiento
  Then es redirigido automáticamente a la ruta "/pos"
  And la página muestra el título "POS"

Scenario: Navegación a las distintas secciones
  Given que el usuario se encuentra en "/pos"
  When hace clic en la opción "Productos" dentro del grupo "Catálogo"
  Then la URL del navegador cambia a "/products"
  And se muestra el título "Productos" en el contenido principal
```

---

### US-04: Identificación Visual del Estado Activo
**Como** usuario del sistema,  
**quiero** identificar a simple vista en qué sección me encuentro,  
**para** no perder la ubicación dentro de la jerarquía de la aplicación.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Indicador visual de grupo y opción activa
  Given que el usuario ha navegado a la ruta "/products"
  When observa la barra de navegación superior
  Then el grupo "Catálogo" se destaca visualmente con estilo activo
  When despliega el menú de "Catálogo"
  Then la opción "Productos" cuenta con un estilo distintivo (color/fondo/acento) frente a "Categorías" y "Etiquetas"
```

---

### US-05: Estructura Centralizada y Escalable
**Como** desarrollador del sistema,  
**quiero** que la estructura del menú esté centralizada en un archivo de configuración tipado,  
**para** agregar nuevos módulos o subopciones en el futuro sin modificar la lógica interna del componente visual.

#### Criterios de Aceptación (Gherkin):
```gherkin
Scenario: Extensibilidad del menú
  Given el archivo de configuración `navigation.config.ts`
  When se define un nuevo grupo o enlace
  Then el componente de navegación lo renderiza automáticamente sin requerir cambios en su plantilla o lógica
```
