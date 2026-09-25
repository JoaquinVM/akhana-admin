# Registro de Decisiones Técnicas - REQ-0004

---

### DEC-001: Gestión de Estado con Angular Signals
- **Contexto:** Se requiere una fuente única de verdad para el estado de autenticación en Angular 21.
- **Decisión:** Utilizar Angular Signals (`signal<UserSession | null>`, `computed(...)`) en `AuthService`.
- **Razón:** Proporciona reactividad granular, rendimiento óptimo sin suscripciones manuales a RxJS para estado de vista y compatibilidad con el estándar moderno de Angular.
- **Impacto:** Notificación instantánea a componentes y guards cuando cambia el estado de sesión.

---

### DEC-002: Persistencia con `localStorage`
- **Contexto:** La sesión del usuario debe sobrevivir a recargas de página (`F5`).
- **Decisión:** Almacenar `akhana_token` (string JWT) y `akhana_user` (JSON con id, username, rol) en `localStorage`.
- **Razón:** Sencillo, estándar en SPAs, y desacoplado de cookies que requerirían backend stateful.
- **Impacto:** Restauración automática de la sesión al inicializar `AuthService`.

---

### DEC-003: Interceptores y Guards Funcionales (`HttpInterceptorFn` y `CanActivateFn`)
- **Contexto:** Angular desaconseja el uso de guards e interceptores basados en clases (`@Injectable() implements CanActivate`).
- **Decisión:** Implementar `authInterceptor: HttpInterceptorFn` y `authGuard / guestGuard: CanActivateFn`.
- **Razón:** Menor sobrecarga de código, composición funcional limpia con `inject()`, estándar oficial de Angular 17-21.
- **Impacto:** Código conciso, fácil de probar y alineado a las mejores prácticas vigentes.

---

### DEC-004: Configuración de Proxy de Desarrollo (`proxy.conf.json`)
- **Contexto:** En desarrollo local, Angular corre en el puerto 4200 y Spring Boot en el puerto 8080.
- **Decisión:** Crear `proxy.conf.json` en `frontend/` mapeando `/api` hacia `http://localhost:8080`.
- **Razón:** Evita problemas de bloqueo CORS entre distintos orígenes en local y prepara el frontend para consumir la misma ruta relativa `/api` que existirá en producción.
- **Impacto:** Comunicación fluida y sin configuraciones complejas de headers en el cliente.
