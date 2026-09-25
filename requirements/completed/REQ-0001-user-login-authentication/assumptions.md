# Supuestos Operativos (Assumptions) - REQ-0001

### ASSUMPTION-001: Identificador de Entidad User
- **Supuesto:** Se utilizará `UUID` como clave primaria de la entidad `User`.
- **Justificación:** Se alinea con el esquema canónico definido en `database/schemas/schema.sql` y las mejores prácticas para evitar enumeración de IDs.

### ASSUMPTION-002: Nombre de la Tabla en Base de Datos
- **Supuesto:** La tabla en PostgreSQL se nombrará `users` o `app_users` para evitar palabras reservadas del motor relacional (`USER` es palabra reservada en PostgreSQL y SQL ANSI).
- **Justificación:** Previene conflictos sintácticos en PostgreSQL. Se anotará `@Table(name = "app_users")`.

### ASSUMPTION-003: Contrato de Respuesta Exitoso
- **Supuesto:** El endpoint `POST /api/auth/login` devolverá un DTO `LoginResponse` con los campos: `{ "id": UUID, "username": String, "role": String, "message": "Authentication successful" }`.
- **Justificación:** Proporciona al cliente la información mínima necesaria del usuario autenticado sin filtrar datos sensibles.

### ASSUMPTION-004: Rol como Enum
- **Supuesto:** Los roles `ADMIN` y `SELLER` se modelarán como un Enum `Role` en Java y se persistirán como `EnumType.STRING`.
- **Justificación:** Tipado fuerte y consistencia con las convenciones del backend Spring Boot.
