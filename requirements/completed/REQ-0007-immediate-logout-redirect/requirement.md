# Requerimiento — REQ-0007: Redirección Inmediata a Login al Cerrar Sesión

## 1. Objetivo

Corregir el flujo de cierre de sesión en la aplicación web para que, al pulsar **Cerrar Sesión**, el sistema invalide inmediatamente la sesión del usuario y redirija de forma instantánea a la pantalla de Login (`/login`), impidiendo la permanencia o interacción en pantallas protegidas sin requerir un refresh manual o peticiones posteriores.

---

## 2. Comportamiento Actual vs Esperado

### Comportamiento Actual
- Al hacer clic en **Cerrar Sesión**, se eliminan los datos de sesión en almacenamiento y los signals de usuario pasan a `null` (desaparece el nombre en el navbar).
- Sin embargo, no se ejecuta una navegación explícita; el usuario permanece en la pantalla activa.
- La redirección a `/login` solo se produce si el usuario interactúa ejecutando una petición HTTP que retorne 401 (capturada por el interceptor), o si refresca la página (capturada por el `authGuard`).

### Comportamiento Esperado
Al hacer clic en **Cerrar Sesión**:
1. Invalidar/eliminar la información de la sesión actual (`localStorage`, signals reactivos).
2. Redirigir **inmediatamente** al usuario a la pantalla de Login (`/login`).
3. Cerrar cualquier menú desplegable activo.
4. Impedir que el usuario continúe en pantallas protegidas o interactúe con componentes protegidos.
5. No depender de recargas de página, acciones posteriores o expiración de tiempos.

---

## 3. Criterios de Aceptación

1. Al invocar la acción de cerrar sesión desde la barra de navegación o mediante `AuthService.logout()`, el sistema elimina el token y los datos de usuario de `localStorage`.
2. El signal `isAuthenticated` pasa inmediatamente a `false` y `currentUser` a `null`.
3. El sistema ejecuta inmediatamente la navegación a la ruta `/login`.
4. Los menús desplegables del navbar se cierran.
5. Las pruebas unitarias de `AuthService` y componentes relacionados validan que `router.navigate(['/login'])` se ejecuta al hacer logout.
6. La compilación de producción y la suite de pruebas unitarias finalizan con 100% de éxito.
