# Technical Design - REQ-0003: Autenticación JWT

---

## 🏛️ 1. Arquitectura de Componentes

Se integran los siguientes componentes dentro de `backend/src/main/java/com/akhana/akhana_admin/`:

```
com.akhana.akhana_admin/
├── config/
│   ├── JwtAuthenticationFilter.java  # OncePerRequestFilter para validar Authorization: Bearer <token>
│   ├── JwtAuthenticationEntryPoint.java # Retorna HTTP 401 Unauthorized estructurado si no hay token
│   └── SecurityConfig.java           # Registro del filtro JWT antes de UsernamePasswordAuthenticationFilter
├── dto/
│   ├── LoginResponse.java            # Record extendido con campo String token
│   └── UserProfileResponse.java      # Record de perfil autenticado (/api/auth/me)
├── service/
│   ├── JwtService.java               # Interfaz para generación y validación de tokens
│   └── impl/
│       ├── JwtServiceImpl.java       # Implementación criptográfica basada en io.jsonwebtoken (JJWT)
│       └── AuthServiceImpl.java      # Inyección de JwtService en el login exitoso
└── controller/
    └── AuthController.java           # POST /api/auth/login y GET /api/auth/me (protegido)
```

---

## 🔑 2. Generación y Estructura del JWT

### Claims del Token:
- **`sub`**: Identificador único del usuario (`UUID` en formato String).
- **`username`**: Nombre de usuario (`admin` o `seller`).
- **`role`**: Rol del usuario (`ADMIN` o `SELLER`).
- **`iat`**: Fecha/hora actual (`Instant.now()`).
- **`exp`**: Fecha/hora de expiración (`Instant.now().plusMillis(expiration)`).
- **Algoritmo de Firma:** HMAC-SHA256 (`HS256`) mediante `io.jsonwebtoken.security.Keys.hmacShaKeyFor()`.

---

## ⚙️ 3. Propiedades Externas (`application.properties`)

```properties
# JWT Configuration
security.jwt.secret=${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
security.jwt.expiration=${JWT_EXPIRATION:86400000}
```
- La clave secreta de 256 bits está desacoplada del código fuente y puede sobreescribirse mediante la variable de entorno `JWT_SECRET`.
- El tiempo de expiración (por defecto 24 horas = 86,400,000 ms) es configurable mediante `JWT_EXPIRATION`.

---

## 🛡️ 4. Flujo del Filtro `JwtAuthenticationFilter`

```text
Solicitud HTTP entrante
        ↓
¿Header "Authorization" inicia con "Bearer "?
   ├─ NO ➔ Continuar filterChain (si el endpoint requiere auth, SecurityFilterChain rechazará con 401)
   └─ SÍ ➔ Extraer token
             ↓
        ¿Token válido y firma correcta?
           ├─ NO ➔ Continuar filterChain (SecurityContext permanecerá vacío ➔ 401)
           └─ SÍ ➔ Extraer username y role
                     ↓
                ¿Usuario existe en BD y active == true?
                   ├─ NO ➔ Continuar filterChain ➔ 401
                   └─ SÍ ➔ Construir UsernamePasswordAuthenticationToken con authority "ROLE_" + role
                             ↓
                        Establecer en SecurityContextHolder.getContext().setAuthentication(auth)
                             ↓
                        Continuar filterChain al Controller
```

---

## 🔌 5. Contratos de API REST

### 1. `POST /api/auth/login` (Público)
- **Response HTTP 200 OK:**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "id": "43adda0b-ba55-49ec-a2a1-d46b3b9395a7",
    "username": "admin",
    "role": "ADMIN",
    "message": "Authentication successful"
  }
  ```

### 2. `GET /api/auth/me` (Protegido - Requiere Bearer Token)
- **Header Requerido:** `Authorization: Bearer <token>`
- **Response HTTP 200 OK:**
  ```json
  {
    "id": "43adda0b-ba55-49ec-a2a1-d46b3b9395a7",
    "username": "admin",
    "role": "ADMIN"
  }
  ```
- **Response HTTP 401 Unauthorized (Sin token o token inválido/expirado):**
  ```json
  {
    "error": "Unauthorized",
    "message": "Full authentication is required to access this resource"
  }
  ```
