# Registro de Implementación Real - REQ-0003

> **Nota:** Este documento refleja los archivos, componentes y pruebas efectivamente implementados y verificados.

---

## 📦 Componentes Implementados

### 1. Dependencias y Configuración
- **`backend/build.gradle`**: Incorporación de JJWT 0.12.6 (`io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`, `jjwt-jackson`).
- **`backend/src/main/resources/application.properties`**:
  - `security.jwt.secret`: Clave externa HMAC-SHA256 (con variable de entorno `${JWT_SECRET}`).
  - `security.jwt.expiration`: Tiempo de expiración de 24h (con variable de entorno `${JWT_EXPIRATION}`).

### 2. Capa de Servicios Criptográficos
- **`JwtService.java`** y **`JwtServiceImpl.java`**:
  - Algoritmo de firma: HMAC-SHA256 (`HS256`).
  - Claims incluidos: `sub` (User ID UUID String), `username`, `role`, `iat`, `exp`.
  - Métodos para extracción de claims y validación de firma y fecha de expiración.
- **`AuthServiceImpl.java`**:
  - Inyección de `JwtService` en el flujo de login.
  - Validación de credenciales y usuario activo mediante JPA + BCrypt.
  - Generación del JWT únicamente tras autenticación exitosa.

### 3. DTOs de Transferencia
- **`LoginResponse.java`**: Record extendido con el campo `token: String` como primer parámetro, conservando constructor de retrocompatibilidad.
- **`UserProfileResponse.java`**: Nuevo record con `UUID id`, `String username`, `Role role` para exposición segura de perfiles autenticados.

### 4. Capa de Seguridad y Filtros
- **`JwtAuthenticationFilter.java`**:
  - Extiende `OncePerRequestFilter`.
  - Extrae token de `Authorization: Bearer <token>`.
  - Valida la integridad y expiración del token.
  - Comprueba que el usuario exista en BD y esté activo.
  - Genera `UsernamePasswordAuthenticationToken.authenticated(...)` con rol `ROLE_<ROLE>`.
  - Persiste el contexto en `SecurityContextHolder` y `RequestAttributeSecurityContextRepository`.
- **`JwtAuthenticationEntryPoint.java`**:
  - Intercepta accesos no autorizados o tokens expirados/inválidos devolviendo HTTP 401 Unauthorized estructurado en formato JSON.
- **`SecurityConfig.java`**:
  - Sesiones sin estado (`SessionCreationPolicy.STATELESS`).
  - Registro de `JwtAuthenticationFilter` antes de `UsernamePasswordAuthenticationFilter`.
  - Registro de `JwtAuthenticationEntryPoint` en `exceptionHandling()`.
  - `POST /api/auth/login` configurado como público (`permitAll()`); demás endpoints requieren autenticación.

### 5. Controladores REST
- **`AuthController.java`**:
  - `POST /api/auth/login`: Retorna 200 OK con token JWT y datos del usuario logueado.
  - `GET /api/auth/me`: Endpoint protegido que extrae al usuario directamente de `Authentication` / `SecurityContext`.

---

## 🧪 Pruebas Automatizadas

1. **`JwtServiceTest.java`**:
   - Generación de token y validación de claims (`sub`, `username`, `role`, fechas).
   - Invalidez por username incorrecto o token manipulado.
   - Detección de token expirado.
2. **`AuthServiceTest.java`**:
   - Verificación de generación de JWT en login exitoso.
   - Verificación de que no se genera token ante credenciales erróneas o usuario inactivo.
3. **`AuthControllerTest.java`**:
   - `POST /api/auth/login` con 200 OK y presencia de `token`.
   - `POST /api/auth/login` con 401 y 400.
   - `GET /api/auth/me` con 200 OK cuando el token Bearer es válido.
   - `GET /api/auth/me` con 401 cuando falta el encabezado o el token es inválido.
4. **`JwtAuthenticationIntegrationTest.java`**:
   - Flujo end-to-end con usuario `admin`: login genera JWT y accede a `/api/auth/me`.
   - Flujo end-to-end con usuario `seller`: login genera JWT y accede a `/api/auth/me`.
   - Rechazo 401 sin token y con token inválido.

**Resultado total:** 21 pruebas ejecutadas, 21 exitosas (100% pass).
