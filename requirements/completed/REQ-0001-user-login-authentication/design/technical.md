# Technical Design - REQ-0001: Login y Autenticación de Usuarios

---

## 🏛️ 1. Arquitectura General y Paquetes

Se implementa una arquitectura en capas dentro de `akhana-admin` (`src/main/java/com/akhana/akhana_admin/`):

```
com.akhana.akhana_admin/
├── model/
│   ├── User.java                # Entidad JPA (@Table(name = "app_users"))
│   └── Role.java                # Enum (ADMIN, SELLER)
├── repository/
│   └── UserRepository.java      # Interfaz JpaRepository<User, UUID>
├── dto/
│   ├── LoginRequest.java        # Record de entrada (@NotBlank username, password)
│   └── LoginResponse.java       # Record de respuesta exitosa (id, username, role)
├── service/
│   └── AuthService.java         # Interfaz y Servicio de autenticación y verificación BCrypt
├── controller/
│   └── AuthController.java      # Endpoint REST POST /api/auth/login
└── config/
    ├── SecurityConfig.java      # SecurityFilterChain y Bean PasswordEncoder
    └── DataInitializer.java     # CommandLineRunner para seeds idempotentes
```

---

## 🗄️ 2. Modelo de Base de Datos y Persistencia

### Script de Migración SQL (`database/migrations/V1__initial_setup.sql`)
```sql
CREATE TABLE IF NOT EXISTS app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'SELLER',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_users_username ON app_users(username);
```

### Entidad JPA (`User.java`)
- `@Entity`
- `@Table(name = "app_users")`
- Atributos:
  - `UUID id` (`@Id`, `@GeneratedValue`)
  - `String username` (`nullable = false, unique = true`)
  - `String password` (`name = "password_hash", nullable = false`)
  - `Role role` (`@Enumerated(EnumType.STRING)`)
  - `boolean active` (`nullable = false`)

---

## 🔌 3. Contratos de API REST

### Endpoint: `POST /api/auth/login`
- **Público:** Permitido sin autenticación previa en Spring Security.
- **Request Headers:** `Content-Type: application/json`
- **Request Body (`LoginRequest`):**
  ```json
  {
    "username": "admin",
    "password": "12345admin"
  }
  ```

#### Respuestas HTTP:
- **200 OK (Credenciales válidas y usuario activo):**
  ```json
  {
    "id": "c1f7a62e-4b6a-4d2c-8cb3-7f289d023b11",
    "username": "admin",
    "role": "ADMIN",
    "message": "Authentication successful"
  }
  ```
- **401 Unauthorized (Credenciales inválidas o usuario inactivo):**
  ```json
  {
    "error": "Unauthorized",
    "message": "Invalid username or password"
  }
  ```
  *Nota: Mensaje genérico para prevenir ataques de enumeración de usuarios.*
- **400 Bad Request (Campos vacíos o malformados):**
  ```json
  {
    "error": "Bad Request",
    "message": "Username and password must not be blank"
  }
  ```

---

## 🔒 4. Configuración de Seguridad (`SecurityConfig.java`)
- Bean `PasswordEncoder`: `BCryptPasswordEncoder` (fuerza por defecto 10).
- `SecurityFilterChain`:
  - Deshabilitar `csrf()` (arquitectura API REST stateless).
  - Configurar `sessionManagement()` en `SessionCreationPolicy.STATELESS`.
  - Autorización: `.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()`, cualquier otra petición `.authenticated()`.
  - Preparado para incorporar filtros JWT en requerimientos posteriores.

---

## 🌱 5. Inicialización Idempotente (`DataInitializer.java`)
Implementa `CommandLineRunner`:
1. Verifica si `userRepository.findByUsername("admin").isEmpty()`:
   - Si no existe: crea `User(username="admin", password=passwordEncoder.encode("12345admin"), role=ADMIN, active=true)`.
2. Verifica si `userRepository.findByUsername("seller").isEmpty()`:
   - Si no existe: crea `User(username="seller", password=passwordEncoder.encode("12345seller"), role=SELLER, active=true)`.
3. Si ya existen: no realiza ninguna acción ni sobreescritura.
