# Contexto de Directorio: backend/

## Propósito y Responsabilidad
Capa de lógica de negocio, procesamiento transaccional, APIs REST y seguridad del sistema Akhana Admin.

## Estructura y Servicios
- `akhana-admin/` (o módulo raíz Spring Boot): Servicio principal desarrollado en Java 17+ y Gradle.
  - Paquete base: `com.akhana.akhana_admin`
  - Puerto del servidor: `8080` (configurado en `application.properties`)
  - Conexión PostgreSQL: `jdbc:postgresql://localhost:5432/akhana`

## Comandos de Verificación
- Compilación y build: `./gradlew build -x test`
- Pruebas unitarias/integración: `./gradlew test`
- Ejecución local: `./gradlew bootRun`

## Convenciones de Arquitectura Backend
1. **Separación en Capas:**
   - `controller`: Exposición de endpoints REST, validación de payloads y códigos HTTP estándar.
   - `service`: Reglas de negocio puras, lógica de transacciones (`@Transactional`).
   - `repository`: Acceso a datos mediante Spring Data JPA / JDBC.
   - `model` / `entity`: Entidades JPA mapeadas a las tablas en `database/`.
   - `dto`: Objetos de transferencia de datos de entrada/salida desacoplados de las entidades de BD.
2. **Manejo de Errores:** Control global de excepciones con `@RestControllerAdvice` retornando respuestas estructuradas (`ProblemDetail` o DTO estándar de error).
3. **Seguridad:** Autenticación stateless (JWT / Spring Security) y validación de roles de usuario.
