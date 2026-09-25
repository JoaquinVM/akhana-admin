# Development Plan - REQ-0001: Login y Autenticación de Usuarios

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad de implementar la solución según su criterio técnico.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Dependencias y Base de Datos
- **`TASK-01`**: Agregar dependencia `org.springframework.boot:spring-boot-starter-security` en `akhana-admin/build.gradle` y compilar.
- **`TASK-02`**: Crear script de migración SQL `database/migrations/V1__initial_setup.sql` y actualizar `database/schemas/schema.sql`.

### Fase 2: Modelo de Dominio y Repositorio
- **`TASK-03`**: Crear enum `Role` (`ADMIN`, `SELLER`).
- **`TASK-04`**: Crear entidad JPA `User` con mapeo a tabla `app_users`, UUID, hash de contraseña y estado activo.
- **`TASK-05`**: Crear interfaz `UserRepository` con método `Optional<User> findByUsername(String username)`.

### Fase 3: Seguridad y Semillas de Datos (Seed)
- **`TASK-06`**: Crear clase `SecurityConfig` exponiendo bean `BCryptPasswordEncoder` y configurando `SecurityFilterChain` permitiendo `/api/auth/login`.
- **`TASK-07`**: Implementar `DataInitializer` (`CommandLineRunner`) para creación idempotente de `admin` y `seller` con contraseñas hasheadas.

### Fase 4: Capa de Servicio y Controladores REST
- **`TASK-08`**: Crear DTOs `LoginRequest`, `LoginResponse`, `ErrorResponse`.
- **`TASK-09`**: Implementar `AuthService` con lógica de validación de usuario activo y `passwordEncoder.matches()`.
- **`TASK-10`**: Implementar `AuthController` exponiendo `POST /api/auth/login`.

### Fase 5: Pruebas y Validación (TDD)
- **`TASK-11`**: Crear suite de tests `AuthServiceTest` y `AuthControllerTest` cubriendo:
  - Login exitoso (200 OK)
  - Contraseña incorrecta (401 Unauthorized)
  - Usuario inexistente (401 Unauthorized)
  - Usuario inactivo (401 Unauthorized)
  - Idempotencia del inicializador de datos
- **`TASK-12`**: Ejecutar `./gradlew test` y `./gradlew build` para verificar cobertura y estabilidad.
