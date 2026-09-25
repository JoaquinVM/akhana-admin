# Development Plan - REQ-0005: Menú de Navegación Principal

> **Nota:** Este plan es una propuesta estructurada aprobada, no una camisa de fuerza. El desarrollador humano tiene plena libertad técnica para adaptar o evolucionar la solución.

---

## 📅 Fases y Tareas de Implementación

### Fase 1: Modelado y Configuración Centralizada
- **`TASK-01`**: Crear interfaces `NavItem` y `NavGroup` en `src/app/core/navigation/models/navigation.models.ts`.
- **`TASK-02`**: Definir la estructura centralizada `NAVIGATION_CONFIG` en `src/app/core/navigation/navigation.config.ts` con los grupos `Ventas`, `Catálogo`, `Seguridad` y sus respectivas rutas.

### Fase 2: Componentes de Layout y Vistas de Sección
- **`TASK-03`**: Implementar `SectionPageComponent` en `src/app/pages/section-page/` para desplegar el título dinámico de la sección activa obtenido desde los datos de ruta.
- **`TASK-04`**: Implementar `NavbarComponent` en `src/app/layout/navbar/` con disposición horizontal compacta, despliegue hover con CSS, detección de grupo activo, enlaces routerLink y control de sesión/logout con `AuthService`.
- **`TASK-05`**: Implementar `MainLayoutComponent` en `src/app/layout/main-layout/` como contenedor de la barra de navegación superior y el contenido principal.

### Fase 3: Enrutamiento y Protección
- **`TASK-06`**: Actualizar `src/app/app.routes.ts` configurando el layout principal protegido con `authGuard`, las 6 rutas hijas (`/pos`, `/sales`, `/products`, `/categories`, `/tags`, `/users`), y la redirección por defecto de `/` a `/pos`.

### Fase 4: Pruebas y Validación de Calidad
- **`TASK-07`**: Crear pruebas unitarias para `navbar.component.spec.ts`, `section-page.component.spec.ts` y `main-layout.component.spec.ts`.
- **`TASK-08`**: Ejecutar suite completa de pruebas unitarias (`npm test`) y verificar que todos los tests pasen al 100%.
- **`TASK-09`**: Ejecutar `npm run build` para garantizar cero errores de compilación TypeScript y presupuestos de estilo válidos.
