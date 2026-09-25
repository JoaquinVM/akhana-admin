# Registro de Decisiones y Desviaciones Técnicas - REQ-0001

---

### DEC-001: Mapeo de Tabla `app_users` en Lugar de `user`
- **Contexto:** La entidad JPA se denomina `User`. En PostgreSQL, la palabra `USER` es una palabra clave reservada del sistema.
- **Decisión:** Mapear explícitamente `@Table(name = "app_users")` para evitar problemas sintácticos en queries DDL/DML.
- **Razón:** Compatibilidad y estabilidad con PostgreSQL 17.
- **Impacto:** Ninguno sobre la lógica de negocio; previene errores de sintaxis en JPA.
- **Autor:** Technical Design Agent.
- **Fecha:** 2026-09-25.

---

### DEC-002: Respuestas 401 Genéricas para Prevención de Enumeración
- **Contexto:** El requerimiento solicita no revelar si fue el usuario o la contraseña lo que falló.
- **Decisión:** Retornar exactamente el mismo mensaje `"Invalid username or password"` con código HTTP 401 Unauthorized tanto si el usuario no existe en la base de datos como si la contraseña no coincide o si el usuario está inactivo.
- **Razón:** Práctica de seguridad recomendada por OWASP para evitar ataques de enumeración de cuentas.
- **Impacto:** Cumple estrictamente con el criterio de seguridad solicitado.
- **Autor:** Requirement / Security Review.
- **Fecha:** 2026-09-25.
