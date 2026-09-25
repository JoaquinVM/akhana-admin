# Estrategia de Pruebas - REQ-0004: Frontend Login y Autenticación JWT

---

## 🎯 Objetivo de Calidad
Asegurar la confiabilidad de la autenticación en el cliente, la correcta inyección de tokens en peticiones HTTP, la protección de rutas mediante guards y la validación accesible de formularios.

---

## 🧪 Casos de Prueba Programados

### 1. `auth.service.spec.ts` (Servicio de Autenticación)
- `should authenticate user and store token and user session in localStorage`
- `should restore existing session from localStorage upon initialization`
- `should clear session and update signals to null on logout`
- `should correctly report isAuthenticated status`
- `should propagate login error on invalid credentials`

### 2. `auth.interceptor.spec.ts` (HTTP Interceptor)
- `should add Authorization Bearer header to protected API requests`
- `should NOT add Authorization header to /api/auth/login`
- `should trigger logout when a protected request receives 401 Unauthorized`

### 3. `auth.guard.spec.ts` (Guards de Ruta)
- `authGuard should allow access to protected routes when user is authenticated`
- `authGuard should redirect unauthenticated users to /login`
- `guestGuard should allow unauthenticated users to access /login`
- `guestGuard should redirect authenticated users away from /login to /dashboard`

### 4. `login.component.spec.ts` (Pantalla de Login)
- `should render Akhana logo, username and password fields and submit button`
- `should show validation error when fields are empty`
- `should call authService.login and navigate to /dashboard on success`
- `should display error message on 401 invalid credentials`
- `should display network error message when server is unreachable`
- `should toggle password visibility when eye button is clicked`

### 5. `dashboard.component.spec.ts` (Vista Protegida)
- `should display current authenticated username and role`
- `should call authService.logout on clicking logout button`
