import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { SectionPageComponent } from './pages/section-page/section-page.component';
import { SuppliersComponent } from './pages/suppliers/suppliers.component';
import { authGuard, guestGuard } from './core/auth/auth.guard';

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
      {
        path: 'pos',
        component: SectionPageComponent,
        data: { title: 'POS', subtitle: 'Punto de Venta y Terminal Principal' }
      },
      {
        path: 'sales',
        component: SectionPageComponent,
        data: { title: 'Ventas', subtitle: 'Historial y Reporte de Transacciones' }
      },
      {
        path: 'products',
        component: SectionPageComponent,
        data: { title: 'Productos', subtitle: 'Catálogo Maestro de Artículos' }
      },
      {
        path: 'categories',
        component: SectionPageComponent,
        data: { title: 'Categorías', subtitle: 'Clasificación de Productos' }
      },
      {
        path: 'tags',
        component: SectionPageComponent,
        data: { title: 'Etiquetas', subtitle: 'Descriptores y Atributos' }
      },
      {
        path: 'suppliers',
        component: SuppliersComponent
      },
      {
        path: 'users',
        component: SectionPageComponent,
        data: { title: 'Usuarios', subtitle: 'Gestión de Cuentas y Accesos' }
      },
      {
        path: '',
        redirectTo: 'pos',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'pos'
  }
];
