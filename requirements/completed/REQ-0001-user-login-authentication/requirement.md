# REQ-0001: Login y Autenticación de Usuarios

- **Identificador:** `REQ-0001`
- **Título:** Login y Autenticación de Usuarios con Spring Boot y JPA
- **Complejidad:** `MEDIUM`
- **Estado:** `COMPLETED`
- **Fecha de Creación:** 2026-09-25
- **Fecha de Cierre:** 2026-09-25
- **Módulo Principal:** `backend/akhana-admin`
- **Rama Asociada:** `feature/REQ-0001-user-login-authentication`

---

## 🎯 1. Objetivo
Implementar un mecanismo de autenticación robusto para el backend utilizando Spring Boot + Spring Data JPA, que permita a los usuarios registrados iniciar sesión mediante nombre de usuario y contraseña en formato seguro (BCrypt), asegurando la creación idempotente de los usuarios iniciales del sistema.

---

## 👥 2. Usuarios Iniciales y Credenciales Semilla
Al iniciar la aplicación por primera vez, el sistema debe garantizar la existencia de las siguientes cuentas iniciales:

| Username | Contraseña Inicial (Seed) | Rol | Estado |
| :--- | :--- | :--- | :--- |
| `admin` | `12345admin` | `ADMIN` | Activo (`true`) |
| `seller` | `12345seller` | `SELLER` | Activo (`true`) |

> [!IMPORTANT]
> Las contraseñas en texto plano **nunca** deben persistirse en la base de datos. Se almacenará exclusivamente su hash resultante generado con `BCryptPasswordEncoder`.

---

## 📋 3. Requisitos Funcionales
1. **Entidad User (JPA):**
   - Identificador único (`UUID` o `Long`).
   - `username`: Nombre de usuario único, no nulo.
   - `password`: Hash seguro de la contraseña, no nulo.
   - `role`: Rol del usuario (`ADMIN`, `SELLER`).
   - `active`: Booleano para indicar si el usuario está habilitado para operar.
2. **Inicialización Idempotente:**
   - Crear automáticamente los usuarios `admin` y `seller` solo si no existen previamente.
   - Si ya existen, no duplicarlos ni sobreescribir innecesariamente sus credenciales.
3. **Endpoint de Autenticación (`POST /api/auth/login`):**
   - Recibir payload JSON: `{ "username": "...", "password": "..." }`.
   - Validar credenciales utilizando `PasswordEncoder.matches()`.
   - Validar que `active == true`.
   - Si la autenticación es exitosa: Devolver HTTP 200 OK con payload informativo (id, username, role).
   - Si las credenciales son incorrectas o el usuario está inactivo: Devolver HTTP 401 Unauthorized genérico, sin revelar si el usuario existe o si falló la contraseña.

---

## 🛠️ 4. Requisitos Técnicos y de Seguridad
- Framework: Spring Boot (Spring MVC + Spring Data JPA).
- Seguridad: Spring Security con `BCryptPasswordEncoder` como bean inyectable.
- Endpoints públicos: `/api/auth/login` debe ser accesible sin autenticación previa.
- Arquitectura preparada para incorporar posteriormente JWT y autorización basada en roles (RBAC).
