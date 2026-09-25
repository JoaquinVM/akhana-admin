# Registro de Implementación Real - REQ-0001: Login y Autenticación de Usuarios

- **Estado:** Implementación Completada y Verificada
- **Fecha:** 2026-09-25
- **Branch:** `feature/REQ-0001-user-login-authentication`
- **Tests Status:** `BUILD SUCCESSFUL` (10/10 tests pasados)

---

## 📁 Archivos Creados y Modificados

### 1. Configuración y Dependencias
- [akhana-admin/build.gradle](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/build.gradle):
  - Añadido `org.springframework.boot:spring-boot-starter-security`
  - Añadido `org.springframework.security:spring-security-test`

### 2. Base de Datos y Scripts SQL
- [database/migrations/V1__initial_setup.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/migrations/V1__initial_setup.sql): Script DDL creando tabla `app_users` con campos `id` (UUID), `username`, `password_hash`, `role`, `active`, timestamps e índices.
- [database/schemas/schema.sql](file:///Users/joaquin/Documents/Akhana%20Admin/database/schemas/schema.sql): Actualización de la definición canónica del esquema de base de datos.

### 3. Modelo de Dominio y Persistencia (JPA)
- [akhana-admin/src/main/java/com/akhana/akhana_admin/model/Role.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/model/Role.java): Enum con roles `ADMIN` y `SELLER`.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/model/User.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/model/User.java): Entidad JPA mapeada a `app_users` con claves UUID y auditoría de timestamps.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/repository/UserRepository.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/repository/UserRepository.java): Repositorio Spring Data JPA con `findByUsername(String username)`.

### 4. Seguridad y Semillas de Datos (Seeds)
- [akhana-admin/src/main/java/com/akhana/akhana_admin/config/SecurityConfig.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/config/SecurityConfig.java):
  - Exposición de bean `BCryptPasswordEncoder`
  - Configuración stateless en `SecurityFilterChain`
  - Exposición pública de `POST /api/auth/login`
- [akhana-admin/src/main/java/com/akhana/akhana_admin/config/DataInitializer.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/config/DataInitializer.java): `CommandLineRunner` que crea de forma idempotente las cuentas iniciales `admin` (`12345admin`) y `seller` (`12345seller`) hasheadas con BCrypt.

### 5. DTOs, Excepciones y Servicios REST
- [akhana-admin/src/main/java/com/akhana/akhana_admin/dto/LoginRequest.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/dto/LoginRequest.java): Record con validación `@NotBlank`.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/dto/LoginResponse.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/dto/LoginResponse.java): Record de respuesta exitosa (id, username, role, message).
- [akhana-admin/src/main/java/com/akhana/akhana_admin/dto/ErrorResponse.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/dto/ErrorResponse.java): DTO de respuesta de error genérico.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/exception/AuthenticationFailedException.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/exception/AuthenticationFailedException.java): Excepción específica de fallo de autenticación.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/exception/GlobalExceptionHandler.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/exception/GlobalExceptionHandler.java): `@RestControllerAdvice` capturando excepciones y retornando HTTP 401 Unauthorized y HTTP 400 Bad Request.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/service/AuthService.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/service/AuthService.java): Interfaz de servicio de autenticación.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/service/impl/AuthServiceImpl.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/service/impl/AuthServiceImpl.java): Implementación con validación de credenciales con `passwordEncoder.matches()` y verificación de `user.isActive()`.
- [akhana-admin/src/main/java/com/akhana/akhana_admin/controller/AuthController.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/main/java/com/akhana/akhana_admin/controller/AuthController.java): Endpoint REST `POST /api/auth/login`.

### 6. Suite de Pruebas Automatizadas
- [akhana-admin/src/test/java/com/akhana/akhana_admin/service/AuthServiceTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/test/java/com/akhana/akhana_admin/service/AuthServiceTest.java): Pruebas unitarias de AuthService (login exitoso, contraseña incorrecta, usuario inexistente, usuario inactivo).
- [akhana-admin/src/test/java/com/akhana/akhana_admin/config/DataInitializerTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/test/java/com/akhana/akhana_admin/config/DataInitializerTest.java): Pruebas unitarias de idempotencia en la inicialización de seeds.
- [akhana-admin/src/test/java/com/akhana/akhana_admin/controller/AuthControllerTest.java](file:///Users/joaquin/Documents/Akhana%20Admin/akhana-admin/src/test/java/com/akhana/akhana_admin/controller/AuthControllerTest.java): Pruebas de integración MockMvc del endpoint (200 OK, 401 Unauthorized, 400 Bad Request).
