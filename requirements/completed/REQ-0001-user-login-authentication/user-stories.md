# Historias de Usuario - REQ-0001: Login y Autenticación de Usuarios

---

### US-001: Persistencia de Usuarios y Modelo de Datos Seguro
- **ID:** `US-001` (Trazabilidad: `REQ-0001`)
- **Como:** Desarrollador del sistema
- **Quiero:** Contar con una entidad JPA `User` y repositorio Spring Data JPA
- **Para:** Almacenar usuarios de forma segura con sus contraseñas hasheadas, roles y estados de activación en la base de datos.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Persistencia de un usuario con contraseña hasheada
  Dado un nuevo usuario con username "operador1" y contraseña hasheada con BCrypt
  Cuando se guarda en el repositorio de usuarios
  Entonces el usuario se persiste con un UUID generado
  Y la columna de contraseña contiene un hash BCrypt (prefijo $2a$ o $2b$)
  Y el username es único en la base de datos
```

---

### US-002: Inicialización Idempotente de Usuarios Semilla (Seed Data)
- **ID:** `US-002` (Trazabilidad: `REQ-0001`)
- **Como:** Administrador de la plataforma
- **Quiero:** Que al arrancar la aplicación se creen automáticamente las cuentas `admin` y `seller`
- **Para:** Disponer de credenciales de acceso iniciales sin requerir inserciones manuales en SQL.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Creación inicial cuando la base de datos está vacía
  Dado que la aplicación arranca por primera vez
  Cuando el CommandLineRunner de inicialización se ejecuta
  Entonces se crea el usuario "admin" con rol "ADMIN", activo y contraseña hasheada de "12345admin"
  Y se crea el usuario "seller" con rol "SELLER", activo y contraseña hasheada de "12345seller"

Escenario: Idempotencia en reinicios posteriores
  Dado que los usuarios "admin" y "seller" ya existen en la base de datos
  Cuando la aplicación vuelve a iniciar
  Entonces no se insertan registros duplicados
  Y no se alteran las contraseñas ni los datos de los usuarios existentes
```

---

### US-003: Autenticación de Usuarios mediante Endpoint de Login
- **ID:** `US-003` (Trazabilidad: `REQ-0001`)
- **Como:** Usuario registrado (admin o seller)
- **Quiero:** Enviar mi username y contraseña al endpoint de login
- **Para:** Validar mis credenciales y autenticarme exitosamente en el sistema.

#### Criterios de Aceptación (Gherkin):
```gherkin
Escenario: Login exitoso con credenciales correctas y usuario activo
  Dado que existe un usuario "admin" activo con contraseña hasheada de "12345admin"
  Cuando se envía una petición POST a "/api/auth/login" con username "admin" y password "12345admin"
  Entonces el backend responde con código HTTP 200 OK
  Y el cuerpo de la respuesta contiene el id, username "admin" y rol "ADMIN"

Escenario: Login fallido por contraseña incorrecta
  Dado que existe el usuario "admin"
  Cuando se envía una petición POST a "/api/auth/login" con username "admin" y password "passwordErroneo"
  Entonces el backend responde con código HTTP 401 Unauthorized
  Y el mensaje de error es genérico sin revelar qué campo falló

Escenario: Login fallido por usuario inexistente
  Dado que no existe el usuario "fantasma" en el sistema
  Cuando se envía una petición POST a "/api/auth/login" con username "fantasma" y password "cualquiera"
  Entonces el backend responde con código HTTP 401 Unauthorized
  Y la respuesta no revela que el usuario no existe

Escenario: Login rechazado para usuario inactivo
  Dado que existe un usuario "seller" pero su estado es active = false
  Cuando se envía una petición POST a "/api/auth/login" con las credenciales correctas de "seller"
  Entonces el backend responde con código HTTP 401 Unauthorized
```
