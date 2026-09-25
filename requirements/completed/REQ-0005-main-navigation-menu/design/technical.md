# Diseño Técnico de Arquitectura - REQ-0005: Menú de Navegación Principal

## 🏛️ 1. Arquitectura de Componentes Frontend

Se adopta una arquitectura modular, escalable y guiada por configuración (data-driven) en Angular 21:

```text
src/app/
├── core/
│   ├── auth/                      (Existente de REQ-0004)
│   └── navigation/
│       ├── models/
│       │   └── navigation.models.ts   (Interfaces NavItem, NavGroup)
│       └── navigation.config.ts       (Configuración centralizada de grupos y rutas)
├── layout/
│   ├── main-layout/
│   │   ├── main-layout.component.ts   (Shell principal: Navbar + router-outlet)
│   │   ├── main-layout.component.html
│   │   └── main-layout.component.css
│   └── navbar/
│       ├── navbar.component.ts        (Barra superior, dropdowns hover, active state, logout)
│       ├── navbar.component.html
│       └── navbar.component.css
├── pages/
│   ├── login/                         (Existente de REQ-0004)
│   └── section-page/
│       ├── section-page.component.ts  (Componente reutilizable para despliegue de títulos)
│       ├── section-page.component.html
│       └── section-page.component.css
└── app.routes.ts                      (Definición de rutas con authGuard y redirect a /pos)
```

---

## 📄 2. Modelos de Navegación y Configuración Centralizada

### `navigation.models.ts`:
```typescript
export interface NavItem {
  label: string;
  route: string;
  icon?: string;
}

export interface NavGroup {
  id: string;
  label: string;
  icon?: string;
  children: NavItem[];
}
```

### `navigation.config.ts`:
```typescript
import { NavGroup } from './models/navigation.models';

export const NAVIGATION_CONFIG: NavGroup[] = [
  {
    id: 'sales',
    label: 'Ventas',
    children: [
      { label: 'POS', route: '/pos' },
      { label: 'Ventas', route: '/sales' }
    ]
  },
  {
    id: 'catalog',
    label: 'Catálogo',
    children: [
      { label: 'Productos', route: '/products' },
      { label: 'Categorías', route: '/categories' },
      { label: 'Etiquetas', route: '/tags' }
    ]
  },
  {
    id: 'security',
    label: 'Seguridad',
    children: [
      { label: 'Usuarios', route: '/users' }
    ]
  }
];
```

---

## 🔀 3. Estrategia de Enrutamiento y Protección

### `app.routes.ts`:
```typescript
export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [guestGuard]
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'pos', component: SectionPageComponent, data: { title: 'POS' } },
      { path: 'sales', component: SectionPageComponent, data: { title: 'Ventas' } },
      { path: 'products', component: SectionPageComponent, data: { title: 'Productos' } },
      { path: 'categories', component: SectionPageComponent, data: { title: 'Categorías' } },
      { path: 'tags', component: SectionPageComponent, data: { title: 'Etiquetas' } },
      { path: 'users', component: SectionPageComponent, data: { title: 'Usuarios' } },
      { path: '', pathMatch: 'full', redirectTo: 'pos' },
      { path: 'dashboard', redirectTo: 'pos' }
    ]
  },
  {
    path: '**',
    redirectTo: 'pos'
  }
];
```

---

## ⚡ 4. Reactividad y Detección de Grupo Activo

En `NavbarComponent`:
- Se inyecta `Router` y `AuthService`.
- Se expone el método reactivo `isGroupActive(group: NavGroup): boolean` que compara la URL actual (`this.router.url`) con las rutas de los hijos del grupo (`group.children.some(child => this.router.url === child.route || this.router.url.startsWith(child.route + '/'))`).
- En la plantilla se utiliza `[class.active]="isGroupActive(group)"` para resaltar el botón padre.
- Para cada subopción en el menú desplegable, se utiliza la directiva `routerLinkActive="active"` de Angular.
- El cierre de sesión invoca `authService.logout()`, redirigiendo de inmediato a `/login`.
