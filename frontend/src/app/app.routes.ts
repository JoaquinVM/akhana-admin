import { Routes } from '@angular/router';
import { LoginComponent } from './pages/login/login.component';
import { ChangePasswordComponent } from './pages/change-password/change-password.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { SectionPageComponent } from './pages/section-page/section-page.component';
import { SuppliersComponent } from './pages/suppliers/suppliers.component';
import { PurchasesComponent } from './pages/purchases/purchases.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import { TagsComponent } from './pages/tags/tags.component';
import { ProductsComponent } from './pages/products/products.component';
import { PosComponent } from './pages/pos/pos.component';
import { CashHistoryComponent } from './pages/cash/cash-history/cash-history.component';
import { FinanceSummaryComponent } from './pages/finances/summary/finance-summary.component';
import { TransactionsComponent } from './pages/finances/transactions/transactions.component';
import { AccountsComponent } from './pages/finances/accounts/accounts.component';
import { FinancialCategoriesComponent } from './pages/finances/categories/categories.component';
import { UsersComponent } from './pages/security/users/users.component';
import { authGuard, guestGuard, sessionGuard } from './core/auth/auth.guard';
import { permissionGuard } from './core/auth/permission.guard';
import { PERMISSIONS } from './core/navigation/navigation.config';

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
        data: { permission: PERMISSIONS.POS }
      },
      {
        path: 'pos/sale',
        // Diferida: con el cobro al costo y a crédito el bundle inicial pasaba el límite de 1 MB
        loadComponent: () =>
          import('./pages/pos/register-sale/register-sale.component').then(m => m.RegisterSaleComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.POS }
      },
      {
        path: 'cash/history',
        component: CashHistoryComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.CASH_HISTORY }
      },
      {
        path: 'sales',
        // Carga diferida: el bundle inicial está en el límite de presupuesto (1 MB)
        loadComponent: () => import('./pages/sales/sales-history.component').then(m => m.SalesHistoryComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.SALES_HISTORY }
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
        data: { permission: PERMISSIONS.FINANCES_SUMMARY }
      },
      {
        path: 'finances/transactions',
        component: TransactionsComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.FINANCES_TRANSACTIONS }
      },
      {
        path: 'finances/accounts',
        component: AccountsComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.FINANCES_ACCOUNTS }
      },
      {
        path: 'finances/categories',
        component: FinancialCategoriesComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.FINANCES_CATEGORIES }
      },
      {
        path: 'reports',
        // Carga diferida: el bundle inicial está en el límite de presupuesto (1 MB)
        loadComponent: () => import('./pages/reports/reports.component').then(m => m.ReportsComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.REPORTS }
      },
      {
        path: 'migration',
        // FIN-11: se usa una sola vez, en carga diferida
        loadComponent: () => import('./pages/migration/migration.component').then(m => m.MigrationComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.MIGRATION }
      },
      {
        path: 'products',
        component: ProductsComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.PRODUCTS }
      },
      {
        path: 'categories',
        component: CategoriesComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.CATEGORIES }
      },
      {
        path: 'tags',
        component: TagsComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.TAGS }
      },
      {
        path: 'suppliers',
        component: SuppliersComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.SUPPLIERS }
      },
      {
        path: 'consumptions',
        // Carga diferida: el bundle inicial está en el límite de presupuesto (1 MB)
        loadComponent: () => import('./pages/consumptions/consumptions.component').then(m => m.ConsumptionsComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.CONSUMPTIONS }
      },
      {
        path: 'purchases',
        component: PurchasesComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.PURCHASES }
      },
      {
        path: 'debts',
        // Carga diferida: mantiene el bundle inicial dentro del presupuesto
        loadComponent: () => import('./pages/debts/debts.component').then(m => m.DebtsComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.DEBTS }
      },
      {
        path: 'users',
        component: UsersComponent,
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.USERS }
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
