import { inject } from '@angular/core';
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
import { AuthService } from './core/auth/auth.service';

// DSH-04: whoever has the dashboard starts there; everyone else in the POS
export const homeRedirect = () => inject(AuthService).hasPermission(PERMISSIONS.DASHBOARD) ? 'dashboard' : 'pos';

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
        // Lazy loaded: with at-cost and credit checkout the initial bundle went over 1 MB
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
        // Lazy loaded: the initial bundle is at its budget limit (1 MB)
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
        path: 'dashboard',
        // Lazy loaded: the initial bundle is over its budget (DSH-01 to DSH-04)
        loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.DASHBOARD }
      },
      {
        path: 'reports',
        // Lazy loaded: the initial bundle is at its budget limit (1 MB)
        loadComponent: () => import('./pages/reports/reports.component').then(m => m.ReportsComponent),
        canActivate: [permissionGuard],
        data: { permission: PERMISSIONS.REPORTS }
      },
      {
        path: 'migration',
        // FIN-11: used only once, lazy loaded
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
        // Lazy loaded: the initial bundle is at its budget limit (1 MB)
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
        // Lazy loaded: keeps the initial bundle within budget
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
        redirectTo: homeRedirect,
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: 'pos'
  }
];
