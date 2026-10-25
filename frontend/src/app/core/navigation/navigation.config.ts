import { NavGroup } from './models/navigation.models';

export interface PermissionItemConfig {
  key: string;
  label: string;
}

export interface PermissionGroupConfig {
  id: string;
  label: string;
  permissions: PermissionItemConfig[];
}

export const PERMISSION_GROUPS_CONFIG: PermissionGroupConfig[] = [
  {
    id: 'sales',
    label: 'Ventas',
    permissions: [
      { key: 'SALES_POS', label: 'POS' },
      { key: 'SALES_HISTORY', label: 'Ventas' },
      { key: 'SALES_CASH_HISTORY', label: 'Historial de cajas' }
    ]
  },
  {
    id: 'finances',
    label: 'Finanzas',
    permissions: [
      { key: 'FINANCES_SUMMARY', label: 'Resumen' },
      { key: 'FINANCES_TRANSACTIONS', label: 'Transacciones' },
      { key: 'FINANCES_ACCOUNTS', label: 'Cuentas' },
      { key: 'FINANCES_CATEGORIES', label: 'Categorías' }
    ]
  },
  {
    id: 'catalog',
    label: 'Catálogo',
    permissions: [
      { key: 'CATALOG_PRODUCTS', label: 'Productos' },
      { key: 'CATALOG_CATEGORIES', label: 'Categorías' },
      { key: 'CATALOG_TAGS', label: 'Etiquetas' }
    ]
  },
  {
    id: 'purchases',
    label: 'Compras',
    permissions: [
      { key: 'PURCHASES_SUPPLIERS', label: 'Proveedores' }
    ]
  },
  {
    id: 'security',
    label: 'Seguridad',
    permissions: [
      { key: 'SECURITY_USERS', label: 'Usuarios' }
    ]
  }
];

export const NAVIGATION_CONFIG: NavGroup[] = [
  {
    id: 'sales',
    label: 'Ventas',
    children: [
      {
        label: 'POS',
        route: '/pos',
        badge: 'Cerrada',
        permissionKey: 'SALES_POS'
      },
      {
        label: 'Ventas',
        route: '/sales',
        caption: 'Historial',
        permissionKey: 'SALES_HISTORY'
      },
      {
        label: 'Historial de cajas',
        route: '/cash/history',
        caption: 'Cajas',
        permissionKey: 'SALES_CASH_HISTORY'
      }
    ]
  },
  {
    id: 'finances',
    label: 'Finanzas',
    children: [
      {
        label: 'Resumen',
        route: '/finances/summary',
        permissionKey: 'FINANCES_SUMMARY'
      },
      {
        label: 'Transacciones',
        route: '/finances/transactions',
        permissionKey: 'FINANCES_TRANSACTIONS'
      },
      {
        label: 'Cuentas',
        route: '/finances/accounts',
        permissionKey: 'FINANCES_ACCOUNTS'
      },
      {
        label: 'Categorías',
        route: '/finances/categories',
        permissionKey: 'FINANCES_CATEGORIES'
      }
    ]
  },
  {
    id: 'catalog',
    label: 'Catálogo',
    children: [
      {
        label: 'Productos',
        route: '/products',
        permissionKey: 'CATALOG_PRODUCTS'
      },
      {
        label: 'Categorías',
        route: '/categories',
        permissionKey: 'CATALOG_CATEGORIES'
      },
      {
        label: 'Etiquetas',
        route: '/tags',
        permissionKey: 'CATALOG_TAGS'
      }
    ]
  },
  {
    id: 'purchases',
    label: 'Compras',
    children: [
      {
        label: 'Proveedores',
        route: '/suppliers',
        permissionKey: 'PURCHASES_SUPPLIERS'
      }
    ]
  },
  {
    id: 'security',
    label: 'Seguridad',
    children: [
      {
        label: 'Usuarios',
        route: '/users',
        permissionKey: 'SECURITY_USERS'
      }
    ]
  }
];
