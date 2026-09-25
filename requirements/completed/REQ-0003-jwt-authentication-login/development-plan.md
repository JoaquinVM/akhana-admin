# Development Plan - REQ-0003: Autenticación JWT

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad de implementar la solución según su criterio técnico.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Dependencias y Propiedades
- **`TASK-01`**: Agregar dependencias de JJWT (`io.jsonwebtoken:jjwt-api:0.12.6`, `jjwt-impl`, `jjwt-jackson`) en `backend/build.gradle`.
- **`TASK-02`**: Configurar `security.jwt.secret` y `security.jwt.expiration` en `backend/src/main/resources/application.properties`.

### Fase 2: Servicio Criptográfico de JWT
- **`TASK-03`**: Crear interfaz `JwtService` y su implementación `JwtServiceImpl` con métodos para generar token, extraer username, extraer role, extraer claims y validar expiración/firma.
- **`TASK-04`**: Crear tests unitarios en `JwtServiceTest` para verificar generación, claims (`sub`, `username`, `role`), y validación de expiración.

### Fase 3: Integración de JWT en Login Existente
- **`TASK-05`**: Actualizar record `LoginResponse` para incluir el campo `token: String`.
- **`TASK-06`**: Inyectar `JwtService` en `AuthServiceImpl` y generar el JWT tras validar exitosamente credenciales y estado activo.
- **`TASK-07`**: Actualizar tests existentes de `AuthServiceTest` y `AuthControllerTest` para verificar la presencia del token.

### Fase 4: Filtro de Seguridad y Protección de Endpoints
- **`TASK-08`**: Crear DTO `UserProfileResponse` y endpoint protegido `GET /api/auth/me` en `AuthController`.
- **`TASK-09`**: Implementar `JwtAuthenticationFilter` (extiende `OncePerRequestFilter`) para interceptar encabezado `Authorization: Bearer <token>`, validar token y establecer autenticación en `SecurityContextHolder`.
- **`TASK-10`**: Crear `JwtAuthenticationEntryPoint` para retornar HTTP 401 Unauthorized estructurado cuando falte autenticación.
- **`TASK-11`**: Registrar `JwtAuthenticationFilter` antes de `UsernamePasswordAuthenticationFilter` en `SecurityConfig` y configurar `exceptionHandling()`.

### Fase 5: Pruebas Integrales y Validación
- **`TASK-12`**: Crear suite de tests de integración `JwtAuthenticationIntegrationTest` validando:
  - Login exitoso devuelve token firmado con claims correctos.
  - Llamada a `GET /api/auth/me` con token válido responde 200 OK con el usuario en sesión.
  - Llamada a `GET /api/auth/me` sin token responde 401 Unauthorized.
  - Llamada a `GET /api/auth/me` con token inválido/expirado responde 401 Unauthorized.
- **`TASK-13`**: Ejecutar `./gradlew test` en `backend/` y verificar que todos los tests pasen exitosamente.
