# Supuestos Operativos (Assumptions) - REQ-0003

### ASSUMPTION-001: Nombres de Propiedades de Configuración
- **Supuesto:** Se utilizarán las claves de configuración:
  ```properties
  security.jwt.secret=${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}
  security.jwt.expiration=${JWT_EXPIRATION:86400000}
  ```
- **Justificación:** Proporciona un secreto seguro por defecto (clave HMAC-SHA de 256 bits en hexadecimal) para entornos locales de desarrollo, permitiendo sobreescribirlo limpiamente en producción mediante variables de entorno `JWT_SECRET` y `JWT_EXPIRATION` (86400000 ms = 24 horas).

### ASSUMPTION-002: Contrato de `LoginResponse`
- **Supuesto:** El record `LoginResponse` se ampliará agregando el campo `String token`:
  ```java
  public record LoginResponse(
      String token,
      UUID id,
      String username,
      Role role,
      String message
  ) {}
  ```
- **Justificación:** Cumple con el ejemplo sugerido en el requerimiento (`token`, `username`, `role`), mantiene los campos que ya consumían las pruebas (`id`, `message`) y no rompe el contrato existente.

### ASSUMPTION-003: Endpoint de Verificación del Contexto de Seguridad
- **Supuesto:** Se añadirá un endpoint protegido `GET /api/auth/me` que extrae el usuario autenticado desde el `SecurityContext` (`Authentication.getName()`) y devuelve los datos del usuario en sesión.
- **Justificación:** Permite verificar de forma directa y fehaciente el cumplimiento del criterio: *"el usuario autenticado debe quedar disponible mediante el mecanismo de seguridad de Spring para identificar quién realiza la solicitud sin confiar en un userId enviado por el cliente"*.
