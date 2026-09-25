# REQ-0004: Login y Autenticación JWT en el Frontend

- **Identificador:** `REQ-0004`
- **Título:** Login y Autenticación JWT en el Frontend
- **Complejidad:** `MEDIUM`
- **Estado:** `CLOSED`
- **Fecha de Creación:** 2026-09-25
- **Fecha de Cierre:** 2026-09-25
- **Módulo Principal:** `frontend/`
- **Rama Asociada:** `main`

---

## 🎯 1. Objetivo
Implementar una pantalla de login moderna, responsiva y estéticamente alineada con la identidad visual del logo de **Akhana** en el frontend Angular 21. El frontend debe comunicarse con el endpoint existente del backend (`POST /api/auth/login`), almacenar el JWT recibido en almacenamiento persistente de cliente (`localStorage`), mantener la sesión ante recargas de página, inyectar automáticamente el token en llamadas autenticadas mediante un HTTP Interceptor y proteger las rutas privadas mediante un Auth Guard.

---

## 📋 2. Requisitos Funcionales

1. **Pantalla y Formulario de Login:**
   - Visualmente inspirada en el logo de Akhana (círculo dorado zen, tipografía verde bosque elegante, acentos orgánicos botánicos).
   - Elementos visuales: Logo de Akhana, título/subtítulo del sistema administrativo, campo `Usuario`, campo `Contraseña` (oculto visualmente con opción opcional de alternar visibilidad) y botón `Iniciar sesión`.
   - Validaciones directas en el formulario:
     - Campos obligatorios (usuario y contraseña).
     - Deshabilitación o bloqueo de envío si hay campos vacíos.
     - Mensajes de error claros e integrados visualmente en el formulario.
   - Estado de carga interactivo (*loading spinner* o texto "Iniciando sesión...") para evitar envíos múltiples concurrentes.

2. **Servicio Centralizado de Autenticación (`AuthService`):**
   - Centralizar todas las operaciones de autenticación en `src/app/core/auth/auth.service.ts` utilizando Angular Signals para reactividad.
   - Métodos:
     - `login(credentials: LoginRequest): Observable<LoginResponse>`
     - `logout(): void`
     - `getToken(): string | null`
     - `isAuthenticated(): boolean` (signal reactivo `currentUser`)
     - `getCurrentUser(): UserSession | null`
     - `clearSession(): void`
   - Persistencia: Almacenar token y datos del usuario (`id`, `username`, `role`) en `localStorage` para sobrevivir recargas de página.

3. **HTTP Interceptor (`auth.interceptor.ts`):**
   - Interceptor funcional de Angular (`HttpInterceptorFn`).
   - Agregar automáticamente el encabezado `Authorization: Bearer <token>` a todas las solicitudes dirigidas a endpoints protegidos.
   - Omitir la inyección del encabezado en la petición de login (`/api/auth/login`).
   - Capturar respuestas `401 Unauthorized` en solicitudes protegidas: limpiar automáticamente la sesión y redirigir al usuario a `/login` sin generar bucles infinitos.

4. **Protección de Rutas (Auth Guard & Guest Guard):**
   - `authGuard`: Si un usuario no autenticado intenta acceder a una ruta protegida (`/dashboard`, etc.), redirigirlo inmediatamente a `/login`.
   - `guestGuard`: Si un usuario ya autenticado intenta acceder a `/login`, redirigirlo a `/dashboard`.
   - Ruta protegida de destino (`/dashboard`): Vista administrativa mínima que despliega saludo al usuario autenticado, su rol (`ADMIN` o `SELLER`) y botón funcional de "Cerrar sesión" (*Logout*).

5. **Manejo de Errores y Experiencia de Usuario:**
   - Error 401 en login: Mostrar mensaje general y seguro ("Credenciales no válidas. Verifique su usuario y contraseña.") sin detallar si falló el usuario o la clave.
   - Error de red / Backend no disponible: Mostrar mensaje informativo ("No fue posible comunicarse con el servidor. Intente nuevamente.").
   - Redirección limpia a `/login` al presionar "Cerrar sesión".

---

## 🚫 3. Límites Explícitos (Fuera de Scope)
- No implementar registro de usuarios (*sign-up*).
- No implementar recuperación o cambio de contraseñas.
- No implementar login con proveedores externos o redes sociales.
- No implementar refresh tokens.
- No implementar matriz de permisos o restricciones por rol (la información del rol `ADMIN` / `SELLER` se almacena y expone, pero no bloquea secciones en este requerimiento).
- No alterar ni duplicar la lógica de autenticación en el backend.

---

## ✅ 4. Cierre Formal del Requerimiento
- **Fecha de Aprobación:** 2026-09-25
- **Aprobado por:** Desarrollador Humano (HITL 3)
- **Resultado de Validación:**
  - Login funcional y reactivo probado en navegador contra backend Spring Boot y PostgreSQL.
  - Validación de credenciales erróneas y correctas (`admin`, `seller`).
  - Persistencia de sesión en recarga y control de rutas con Guards.
  - Cierre de sesión voluntario verificado.
  - 20/20 pruebas unitarias aprobadas en Vitest.
  - Build de producción empaquetado sin errores.

