import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { SectionPageComponent } from './pages/section-page/section-page.component';
import { SuppliersComponent } from './pages/suppliers/suppliers.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import { TagsComponent } from './pages/tags/tags.component';
import { ProductsComponent } from './pages/products/products.component';
import { PosComponent } from './pages/pos/pos.component';
import { RegisterSaleComponent } from './pages/pos/register-sale/register-sale.component';
import { CashHistoryComponent } from './pages/cash/cash-history/cash-history.component';
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
        component: PosComponent
      },
      {
        path: 'pos/sale',
        component: RegisterSaleComponent
      },
      {
        path: 'cash/history',
        component: CashHistoryComponent
      },
      {
        path: 'sales',
        component: SectionPageComponent,
        data: { title: 'Ventas', subtitle: 'Historial y Reporte de Transacciones' }
      },
      {
        path: 'products',
        component: ProductsComponent
      },
      {
        path: 'categories',
        component: CategoriesComponent
      },
      {
        path: 'tags',
        component: TagsComponent
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
