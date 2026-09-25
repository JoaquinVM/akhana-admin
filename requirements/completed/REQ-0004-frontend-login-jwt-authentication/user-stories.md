# Historias de Usuario - REQ-0004: Frontend Login y Autenticación JWT

---

### US-001: Pantalla y Formulario de Login con Identidad Akhana
**Como** usuario del sistema administrativo Akhana,  
**Quiero** disponer de una pantalla de login responsive con la identidad visual corporativa,  
**Para** ingresar mis credenciales de forma intuitiva, atractiva y segura.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Visualización inicial de la pantalla de login
  Dado que un usuario no autenticado navega a "/login"
  Entonces debe ver el logo oficial de Akhana centrado
  Y debe ver los campos "Usuario" y "Contraseña"
  Y el campo "Contraseña" debe ocultar los caracteres introducidos
  Y el botón "Iniciar sesión" debe estar visible con estilo verde y dorado

Escenario: Validación de campos vacíos en el formulario
  Dado que el formulario de login tiene uno o ambos campos vacíos
  Cuando el usuario intenta enviar el formulario o sale de un campo requerido
  Entonces se debe mostrar un mensaje de validación indicando que el campo es obligatorio
  Y no se debe enviar ninguna petición HTTP al backend
```

---

### US-002: Autenticación con Backend y Persistencia de Sesión
**Como** usuario registrado (`admin` o `seller`),  
**Quiero** enviar mis credenciales al backend para autenticarme,  
**Para** obtener acceso al sistema y mantener mi sesión activa al recargar la página.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Login exitoso y almacenamiento de JWT
  Dado que el usuario introduce "admin" y "12345admin"
  Cuando hace clic en "Iniciar sesión"
  Entonces se envía una petición POST a "/api/auth/login"
  Y el botón muestra un estado de carga mientras se procesa la solicitud
  Y al recibir 200 OK con el token JWT
  Entonces el token y los datos de usuario se guardan en localStorage
  Y el usuario es redirigido a "/dashboard"

Escenario: Persistencia de sesión tras recarga
  Dado que un usuario se encuentra autenticado con un JWT válido en localStorage
  Cuando el usuario presiona recargar página (F5)
  Entonces el sistema restaura automáticamente la sesión sin pedir login nuevamente
  Y el usuario permanece en "/dashboard"

Escenario: Credenciales incorrectas
  Dado que el usuario introduce credenciales no válidas
  Cuando el backend responde con HTTP 401 Unauthorized
  Entonces el formulario muestra el mensaje "Credenciales no válidas. Verifique su usuario y contraseña."
  Y el usuario permanece en "/login"

Escenario: Error de conexión con el backend
  Dado que el backend se encuentra inaccesible
  Cuando el usuario intenta iniciar sesión
  Entonces el formulario muestra el mensaje "No fue posible comunicarse con el servidor. Intente nuevamente."
  Y el usuario permanece en "/login" con opción de reintentar
```

---

### US-003: Inyección de Token vía HTTP Interceptor y Detección de 401
**Como** desarrollador frontend,  
**Quiero** que todas las peticiones a endpoints protegidos incluyan automáticamente el JWT en el encabezado `Authorization`,  
**Para** no tener que manipular manualmente el token en cada componente y garantizar la revocación limpia si expira.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Inyección automática de Bearer token
  Dado que un usuario autenticado realiza una petición HTTP a un endpoint protegido
  Cuando la petición es procesada por el interceptor
  Entonces se agrega el encabezado "Authorization: Bearer <token>"
  Y si la petición es a "/api/auth/login", no se agrega dicho encabezado

Escenario: Sesión expirada o token revocado
  Dado que una petición protegida retorna HTTP 401 Unauthorized
  Cuando el interceptor captura el error
  Entonces se eliminan el token y los datos del usuario de localStorage
  Y se redirige inmediatamente al usuario a "/login"
```

---

### US-004: Protección de Rutas con Auth Guard y Cierre de Sesión (Logout)
**Como** administrador del sistema,  
**Quiero** que las rutas protegidas no sean accesibles sin autenticación y poder cerrar sesión en cualquier momento,  
**Para** resguardar la información del panel de administración.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Acceso directo a ruta protegida sin autenticación
  Dado que un usuario no autenticado intenta navegar a "/dashboard"
  Cuando el Auth Guard evalúa el acceso
  Entonces el acceso es denegado
  Y el usuario es redirigido a "/login"

Escenario: Acceso a "/login" por usuario ya autenticado
  Dado que un usuario autenticado navega a "/login"
  Cuando el Guest Guard evalúa el acceso
  Entonces el usuario es redirigido a "/dashboard"

Escenario: Cierre de sesión voluntario
  Dado que el usuario autenticado hace clic en el botón "Cerrar sesión" en el dashboard
  Cuando se invoca el método logout
  Entonces se elimina el JWT y la información del usuario de localStorage
  Y se limpia el estado en AuthService
  Y el usuario es redirigido inmediatamente a "/login"
```
