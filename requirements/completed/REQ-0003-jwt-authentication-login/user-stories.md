# Historias de Usuario - REQ-0003: Autenticación JWT

---

### US-001: Generación de JWT en Login Exitoso
- **ID:** `US-001` (Trazabilidad: `REQ-0003`)
- **Como:** Usuario registrado (admin o seller)
- **Quiero:** Recibir un JSON Web Token (JWT) firmado tras autenticarme con mis credenciales válidas
- **Para:** Disponer de una credencial temporal que me permita realizar llamadas a endpoints protegidos de la API.

#### Criterios de Aceptación:
```gherkin
Escenario: Login exitoso genera JWT firmado
  Dado un usuario "admin" activo con credenciales correctas
  Cuando realiza una petición POST a "/api/auth/login"
  Entonces el backend responde con código HTTP 200 OK
  Y la respuesta contiene el campo "token" con un JWT firmado
  Y el token contiene los claims: sub (ID), username ("admin"), role ("ADMIN"), iat y exp
  Y el token no contiene la contraseña ni datos sensibles
```

---

### US-002: Configuración Externa de Seguridad para JWT
- **ID:** `US-002` (Trazabilidad: `REQ-0003`)
- **Como:** Administrador / Ingeniero DevOps
- **Quiero:** Que la clave secreta y la expiración del JWT estén desacopladas del código fuente
- **Para:** Poder rotar claves o ajustar tiempos de expiración mediante properties o variables de entorno sin recompilar.

#### Criterios de Aceptación:
```gherkin
Escenario: Configuración de JWT mediante application.properties
  Dado el archivo application.properties
  Cuando se definen "security.jwt.secret" y "security.jwt.expiration"
  Entonces el JwtService utiliza estos valores para firmar y validar tokens
  Y permite sobreescritura mediante variables de entorno JWT_SECRET y JWT_EXPIRATION
```

---

### US-003: Validación Automática de JWT e Identificación en SecurityContext
- **ID:** `US-003` (Trazabilidad: `REQ-0003`)
- **Como:** API del backend
- **Quiero:** Interceptar peticiones con encabezado "Authorization: Bearer <token>" y validar el JWT
- **Para:** Establecer la identidad y el rol del usuario en el SecurityContext antes de que la petición llegue al controlador.

#### Criterios de Aceptación:
```gherkin
Escenario: Petición a endpoint protegido con token válido
  Dado un token JWT válido generado para el usuario "seller" con rol "SELLER"
  Cuando se envía una petición GET a un endpoint protegido con "Authorization: Bearer <token>"
  Entonces la petición se procesa exitosamente
  Y el SecurityContext contiene al usuario "seller" con la autoridad "ROLE_SELLER"

Escenario: Petición a endpoint protegido sin token
  Dado un endpoint protegido
  Cuando se envía una petición sin el header "Authorization"
  Entonces el backend responde con código HTTP 401 Unauthorized

Escenario: Petición a endpoint protegido con token inválido o expirado
  Dado un endpoint protegido
  Cuando se envía una petición con un token con firma alterada o expirado
  Entonces el backend responde con código HTTP 401 Unauthorized
```
