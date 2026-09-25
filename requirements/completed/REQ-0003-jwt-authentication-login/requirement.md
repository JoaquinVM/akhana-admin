# REQ-0003: Incorporar Autenticación JWT al Login Existente

- **Identificador:** `REQ-0003`
- **Título:** Incorporar Autenticación JWT al Login Existente
- **Complejidad:** `MEDIUM`
- **Estado:** `CLOSED`
- **Fecha de Cierre:** 2026-09-25
- **Módulo Principal:** `backend/`
- **Rama Asociada:** `feature/REQ-0003-jwt-authentication-login`

---

## 🎯 1. Objetivo
Modificar el login existente en el backend Spring Boot para que, tras validar exitosamente las credenciales de un usuario registrado, genere un **JSON Web Token (JWT)** firmado. Asimismo, incorporar un filtro en Spring Security que valide automáticamente el JWT en el encabezado `Authorization: Bearer <token>` de las solicitudes subsiguientes y establezca al usuario autenticado en el `SecurityContext`.

---

## 📋 2. Requisitos Funcionales

1. **Generación de JWT en Login Exitoso:**
   - Cuando las credenciales sean correctas y el usuario esté activo, generar un JWT firmado mediante algoritmo HMAC-SHA (HS256 o superior).
   - Incluir los claims mínimos requeridos:
     - `sub`: ID del usuario (`user.getId().toString()`).
     - `username`: Nombre de usuario (`user.getUsername()`).
     - `role`: Rol del usuario (`user.getRole().name()`).
     - `iat`: Fecha y hora de emisión (*issued at*).
     - `exp`: Fecha y hora de expiración (*expiration*).
   - **No incluir** contraseñas ni datos sensibles dentro del token.
   - Devolver el token en la respuesta del login agregándolo al contrato existente (`LoginResponse`), conservando `id`, `username`, `role` y `message`.

2. **Validación Automática de JWT en Solicitudes:**
   - Extraer el token del encabezado HTTP `Authorization: Bearer <token>`.
   - Validar la firma criptográfica con la clave secreta configurada.
   - Validar que el token no haya expirado.
   - Extraer la identidad y el rol del usuario desde los claims del token.
   - Establecer la autenticación correspondiente en el `SecurityContextHolder` para que los controladores puedan identificar al usuario autenticado.

3. **Protección de APIs:**
   - Mantener `POST /api/auth/login` como endpoint público.
   - Exigir JWT válido para cualquier otro endpoint protegido.
   - Una solicitud protegida sin token o con token inválido/expirado debe responder **HTTP 401 Unauthorized**.

---

## 🔒 3. Requisitos Técnicos y de Seguridad

- **Configuración Externa:**
  - Clave secreta (`security.jwt.secret`) y tiempo de expiración (`security.jwt.expiration`) configuradas en `application.properties` con soporte para variables de entorno (`JWT_SECRET`, `JWT_EXPIRATION`).
  - La clave secreta no debe estar hardcodeada.
- **No persistir el JWT:** El token no debe almacenarse en la base de datos relacional.
- **Identificación Confiable:** La identidad del usuario en endpoints protegidos debe provenir exclusivamente del contexto de seguridad (`Authentication`), nunca de un `userId` enviado en el cuerpo o parámetros por el cliente.
