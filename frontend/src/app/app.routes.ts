import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { ChangePasswordComponent } from './pages/change-password/change-password.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { SectionPageComponent } from './pages/section-page/section-page.component';
import { SuppliersComponent } from './pages/suppliers/suppliers.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import { TagsComponent } from './pages/tags/tags.component';
import { ProductsComponent } from './pages/products/products.component';
import { PosComponent } from './pages/pos/pos.component';
import { RegisterSaleComponent } from './pages/pos/register-sale/register-sale.component';
import { CashHistoryComponent } from './pages/cash/cash-history/cash-history.component';
import { FinanceSummaryComponent } from './pages/finances/summary/finance-summary.component';
import { TransactionsComponent } from './pages/finances/transactions/transactions.component';
import { AccountsComponent } from './pages/finances/accounts/accounts.component';
import { FinancialCategoriesComponent } from './pages/finances/categories/categories.component';
import { UsersComponent } from './pages/security/users/users.component';
import { authGuard, guestGuard, sessionGuard } from './core/auth/auth.guard';
import { permissionGuard } from './core/auth/permission.guard';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [guestGuard]
  },
  {
    path: 'change-password',
    component: ChangePasswordComponent,
    canActivate: [sessionGuard]
  },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'pos',
        component: PosComponent,
        canActivate: [permissionGuard],
        data: { permission: 'SALES_POS' }
      },
      {
        path: 'pos/sale',
        component: RegisterSaleComponent,
        canActivate: [permissionGuard],
        data: { permission: 'SALES_POS' }
      },
      {
        path: 'cash/history',
        component: CashHistoryComponent,
        canActivate: [permissionGuard],
        data: { permission: 'SALES_CASH_HISTORY' }
      },
      {
        path: 'sales',
        component: SectionPageComponent,
        canActivate: [permissionGuard],
        data: {
          permission: 'SALES_HISTORY',
          title: 'Ventas',
          subtitle: 'Historial y Reporte de Transacciones'
        }
      },
      {
        path: 'finances',
        redirectTo: 'finances/summary',
        pathMatch: 'full'
      },
      {
        path: 'finances/summary',
        component: FinanceSummaryComponent,
        canActivate: [permissionGuard],
        data: { permission: 'FINANCES_SUMMARY' }
      },
      {
        path: 'finances/transactions',
        component: TransactionsComponent,
        canActivate: [permissionGuard],
        data: { permission: 'FINANCES_TRANSACTIONS' }
      },
      {
        path: 'finances/accounts',
        component: AccountsComponent,
        canActivate: [permissionGuard],
        data: { permission: 'FINANCES_ACCOUNTS' }
      },
      {
        path: 'finances/categories',
        component: FinancialCategoriesComponent,
        canActivate: [permissionGuard],
        data: { permission: 'FINANCES_CATEGORIES' }
      },
      {
        path: 'products',
        component: ProductsComponent,
        canActivate: [permissionGuard],
        data: { permission: 'CATALOG_PRODUCTS' }
      },
      {
        path: 'categories',
        component: CategoriesComponent,
        canActivate: [permissionGuard],
        data: { permission: 'CATALOG_CATEGORIES' }
      },
      {
        path: 'tags',
        component: TagsComponent,
        canActivate: [permissionGuard],
        data: { permission: 'CATALOG_TAGS' }
      },
      {
        path: 'suppliers',
        component: SuppliersComponent,
        canActivate: [permissionGuard],
        data: { permission: 'PURCHASES_SUPPLIERS' }
      },
      {
        path: 'users',
        component: UsersComponent,
        canActivate: [permissionGuard],
        data: { permission: 'SECURITY_USERS' }
      },
      {
        path: 'unauthorized',
        component: SectionPageComponent,
        data: {
          title: 'Acceso Restringido',
          subtitle: 'No cuenta con permisos asignados para acceder a ningún módulo del sistema. Comuníquese con el administrador.'
        }
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
