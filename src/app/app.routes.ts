import { Routes } from '@angular/router';
import { PosTerminalComponent } from './components/pos-terminal/pos-terminal.component';
import { SalesHistoryComponent } from './components/sales-history/sales-history.component';
import { ProductManagementComponent } from './components/product-management/product-management.component';
import { CatalogManagementComponent } from './components/catalog-management/catalog-management.component';

export const routes: Routes = [
  { path: '', redirectTo: 'pos', pathMatch: 'full' },
  { path: 'pos', component: PosTerminalComponent, title: 'Terminal POS • Akhana' },
  { path: 'sales', component: SalesHistoryComponent, title: 'Consulta de Ventas • Akhana' },
  { path: 'products', component: ProductManagementComponent, title: 'Gestión de Productos • Akhana' },
  { path: 'catalog', component: CatalogManagementComponent, title: 'Catálogo y Entidades • Akhana' },
  { path: '**', redirectTo: 'pos' }
];
