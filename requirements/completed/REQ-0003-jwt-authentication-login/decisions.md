# Registro de Decisiones y Desviaciones Técnicas - REQ-0003

---

### DEC-001: Biblioteca JJWT 0.12.6
- **Contexto:** Se requiere generar y validar JWTs firmados criptográficamente en Spring Boot con Java 21.
- **Decisión:** Utilizar `io.jsonwebtoken:jjwt-api:0.12.6` junto con sus implementaciones de runtime `jjwt-impl` y `jjwt-jackson`.
- **Razón:** Es la biblioteca estándar más utilizada, moderna y segura para JWT en el ecosistema Java, completamente compatible con Spring Boot 3/4.
- **Impacto:** Firma y parseo eficiente sin necesidad de dependencias pesadas.
- **Autor:** Technical Design Agent.
- **Fecha:** 2026-09-25.

---

### DEC-002: Endpoint de Verificación `GET /api/auth/me`
- **Contexto:** Se debe garantizar que el `SecurityContext` contenga al usuario autenticado y que los endpoints puedan identificarlo directamente sin confiar en datos enviados por el cliente.
- **Decisión:** Crear el endpoint protegido `GET /api/auth/me` que inyecta `Authentication` y extrae los datos del usuario.
- **Razón:** Valida de forma demostrable el criterio de aceptación número 6 del requerimiento.
- **Impacto:** Proporciona un endpoint protegido estándar que además será consumido por el frontend en el futuro para obtener el perfil del usuario logueado.
- **Autor:** Technical Design Agent.
- **Fecha:** 2026-09-25.
