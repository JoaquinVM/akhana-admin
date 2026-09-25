# Technical Design - REQ-0004: Frontend Login y Autenticación JWT

---

## 🏛️ 1. Arquitectura de Módulos y Archivos en Angular 21

Se implementa una arquitectura modular desacoplada en `frontend/src/app/`:

```text
frontend/src/app/
├── core/
│   └── auth/
│       ├── models/
│       │   └── auth.models.ts         # Contratos LoginRequest, LoginResponse, UserSession, Role
│       ├── auth.service.ts            # Servicio centralizado con Angular Signals y persistencia localStorage
│       ├── auth.guard.ts              # Guards funcionales (authGuard y guestGuard)
│       └── auth.interceptor.ts        # HttpInterceptorFn para inyección de Bearer y captura de 401
├── pages/
│   ├── login/
│   │   ├── login.component.ts         # Standalone component con ReactiveFormsModule
│   │   ├── login.component.html       # Maquetación con el logo de Akhana y estados de error/carga
│   │   └── login.component.css        # Estilos visuales con paleta verde bosque y dorado zen
│   └── dashboard/
│       ├── dashboard.component.ts     # Vista protegida básica de bienvenida
│       ├── dashboard.component.html   # Despliegue de usuario, rol y botón de logout
│       └── dashboard.component.css    # Estilo coherente con la identidad visual
├── app.routes.ts                      # Definición de rutas y vinculación de guards
├── app.config.ts                      # Configuración de providers (withInterceptors([authInterceptor]))
└── app.html                           # Contenedor raíz con <router-outlet />
```

---

## 📦 2. Modelos de Datos (`auth.models.ts`)

```typescript
export type Role = 'ADMIN' | 'SELLER';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  id: string;
  username: string;
  role: Role;
  message: string;
}

export interface UserSession {
  id: string;
  username: string;
  role: Role;
}
```

---

## ⚡ 3. Gestión de Estado Reactivo (`AuthService`)

- **Angular Signals:**
  - `private readonly _currentUser = signal<UserSession | null>(this.restoreSession());`
  - `readonly currentUser = this._currentUser.asReadonly();`
  - `readonly isAuthenticated = computed(() => !!this._currentUser() && !!this.getToken());`
  - `readonly userRole = computed(() => this._currentUser()?.role ?? null);`
- **Persistencia en `localStorage`:**
  - `akhana_token`: Almacena el JWT en texto plano.
  - `akhana_user`: Almacena el JSON serializado de `{ id, username, role }`.
- **Restauración de Sesión:**
  - Al instanciarse el servicio, lee `localStorage`. Si existen token y usuario válidos, inicializa el Signal en memoria para mantener al usuario autenticado tras recargar la página (`F5`).
- **Logout:**
  - Limpia las llaves en `localStorage`, actualiza el Signal a `null` y redirige a `/login`.

---

## 🛡️ 4. HTTP Interceptor Funcional (`auth.interceptor.ts`)

- Implementado como `HttpInterceptorFn`:
  1. Si la URL incluye `/api/auth/login`, no altera la petición y la envía directamente.
  2. Si existe un token en `AuthService`, clona la petición agregando:
     `Authorization: Bearer <token>`
  3. Intercepta respuestas de error con operador RxJS `catchError`:
     - Si `error.status === 401` y la petición no era de login:
       - Ejecuta `authService.logout()`.
       - Evita bucles infinitos de redirección.
       - Propaga el error tipado.

---

## 🚦 5. Guards Funcionales (`auth.guard.ts`)

- **`authGuard: CanActivateFn`:**
  - Comprueba `authService.isAuthenticated()`.
  - Si es verdadero ➔ Permite el acceso (`true`).
  - Si es falso ➔ Redirige con `router.createUrlTree(['/login'])`.

- **`guestGuard: CanActivateFn`:**
  - Comprueba `authService.isAuthenticated()`.
  - Si es falso ➔ Permite el acceso a `/login` (`true`).
  - Si es verdadero ➔ Redirige con `router.createUrlTree(['/dashboard'])`.

---

## 🌐 6. Configuración de Conectividad y CORS

- En desarrollo, Angular consumirá el backend en `http://localhost:8080/api`.
- Se configurará `proxy.conf.json` en `frontend/` apuntando `/api` a `http://localhost:8080` para eliminar cualquier posible problema de CORS en desarrollo local.
