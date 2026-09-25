# Registro de Autonomía y Decisiones - REQ-0003

## Información del Requerimiento
- **ID:** `REQ-0003`
- **Título:** Incorporar Autenticación JWT al Login Existente
- **Complejidad:** `MEDIUM`
- **Fecha:** 2026-09-25

---

## Acciones y Decisiones Autónomas

| Paso | Acción Realizada | Justificación Técnica |
|:---|:---|:---|
| 1 | Adición de dependencias JJWT 0.12.6 en `backend/build.gradle` | Estándar de la industria, seguro y completamente compatible con Java 21 y Spring Boot. |
| 2 | Externalización de `security.jwt.secret` y `expiration` en `application.properties` | Garantiza que las credenciales no estén hardcodeadas y permite sobreescritura por variables de entorno `JWT_SECRET` y `JWT_EXPIRATION`. |
| 3 | Creación de `JwtService` y `JwtServiceImpl` | Desacopla la lógica criptográfica de generación/parseo de tokens de la lógica de negocio de autenticación. |
| 4 | Extensión de `LoginResponse` con `token` | Mantiene el contrato existente y agrega el token sin romper compatibilidad. |
| 5 | Inyección de `JwtService` en `AuthServiceImpl` | Asegura que el token solo se emita si las credenciales y el estado activo fueron previamente validados. |
| 6 | Creación de `JwtAuthenticationFilter` y `RequestAttributeSecurityContextRepository` | Valida el encabezado `Authorization: Bearer <token>`, autentica en Spring Security y persiste el contexto para compatibilidad estricta con Spring Security 6/7. |
| 7 | Creación de `JwtAuthenticationEntryPoint` | Garantiza respuestas estándar HTTP 401 Unauthorized en formato JSON para accesos sin credenciales válidas. |
| 8 | Endpoint de verificación `GET /api/auth/me` | Demuestra la identificación del usuario directamente desde el `SecurityContext` sin requerir `userId` del cliente. |
| 9 | Suite integral de 21 pruebas automatizadas | Valida unitariamente la firma de tokens, expiración, controladores e integración end-to-end con usuarios `admin` y `seller`. |
