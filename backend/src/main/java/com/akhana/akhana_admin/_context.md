# Contexto de Paquete: com.akhana.akhana_admin

## Propósito y Responsabilidad
Paquete raíz de la aplicación Spring Boot. Contiene la clase principal de arranque `AkhanaAdminApplication.java` y los subpaquetes de autenticación, seguridad y servicios.

## Estructura de Subpaquetes Implementada
- `controller/`: 
  - `AuthController.java`: Endpoints `POST /api/auth/login` (público) y `GET /api/auth/me` (protegido).
- `service/`: 
  - `AuthService.java` y `impl/AuthServiceImpl.java`: Autenticación con BCrypt, verificación de usuario activo y generación de JWT.
  - `JwtService.java` y `impl/JwtServiceImpl.java`: Generación y validación de tokens JWT mediante JJWT (claims: `sub`, `username`, `role`, `iat`, `exp`).
- `repository/`: 
  - `UserRepository.java`: Repositorio Spring Data JPA para la entidad `User`.
- `model/`: 
  - `User.java`: Entidad JPA persistida en tabla `app_users`.
  - `Role.java`: Enum con roles `ADMIN` y `SELLER`.
- `dto/`: 
  - `LoginRequest.java`: DTO de entrada para login.
  - `LoginResponse.java`: DTO de respuesta exitosa (incluye `token`, `id`, `username`, `role`, `message`).
  - `UserProfileResponse.java`: DTO de perfil extraído desde `SecurityContext`.
  - `ErrorResponse.java`: DTO de error estructurado.
- `config/`: 
  - `SecurityConfig.java`: Configuración de Spring Security stateless con `JwtAuthenticationFilter` antes de `UsernamePasswordAuthenticationFilter` y `JwtAuthenticationEntryPoint`.
  - `JwtAuthenticationFilter.java`: Filtro `OncePerRequestFilter` para validación de `Authorization: Bearer <token>` y persistencia en `RequestAttributeSecurityContextRepository`.
  - `JwtAuthenticationEntryPoint.java`: Manejador de respuestas 401 Unauthorized estructuradas.
  - `DataInitializer.java`: Inicialización idempotente de seeds `admin` y `seller`.
- `exception/`: 
  - `AuthenticationFailedException.java`: Excepción de autenticación.
  - `GlobalExceptionHandler.java`: Manejador `@RestControllerAdvice` retornando HTTP 401 y 400.

## Regla de Oro
Mantener el código limpio, desacoplado y con pruebas unitarias e integración en `src/test/java/com/akhana/akhana_admin/`.
