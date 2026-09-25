# Contexto de Paquete: com.akhana.akhana_admin

## Propósito y Responsabilidad
Paquete raíz de la aplicación Spring Boot. Contiene la clase principal de arranque `AkhanaAdminApplication.java` y los subpaquetes de autenticación, seguridad y servicios.

## Estructura de Subpaquetes Implementada
- `controller/`: 
  - `AuthController.java`: Endpoint `POST /api/auth/login`.
- `service/`: 
  - `AuthService.java` y `impl/AuthServiceImpl.java`: Autenticación con BCrypt y verificación de usuario activo.
- `repository/`: 
  - `UserRepository.java`: Repositorio Spring Data JPA para la entidad `User`.
- `model/`: 
  - `User.java`: Entidad JPA persistida en tabla `app_users`.
  - `Role.java`: Enum con roles `ADMIN` y `SELLER`.
- `dto/`: 
  - `LoginRequest.java`: DTO de entrada.
  - `LoginResponse.java`: DTO de respuesta exitosa.
  - `ErrorResponse.java`: DTO de error estructurado.
- `config/`: 
  - `SecurityConfig.java`: Configuración de Spring Security stateless y bean `BCryptPasswordEncoder`.
  - `DataInitializer.java`: Inicialización idempotente de seeds `admin` y `seller`.
- `exception/`: 
  - `AuthenticationFailedException.java`: Excepción de autenticación.
  - `GlobalExceptionHandler.java`: Manejador `@RestControllerAdvice` retornando HTTP 401 y 400.

## Regla de Oro
Mantener el código limpio, desacoplado y con pruebas unitarias en `src/test/java/com/akhana/akhana_admin/`.
