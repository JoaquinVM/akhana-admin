import { NavGroup } from './models/navigation.models';
import { Role } from '../auth/models/auth.models';

/**
 * Permissions per screen (table 2.2 of specs/requirements.md): whoever enters a screen can do everything in it.
 * Same keys as the backend Permission enum, which requires them on every endpoint with @PreAuthorize.
 * Roles group permissions; the user does not pick them one by one.
 */
export const PERMISSIONS = {
  POS: 'POS',
  SALES_HISTORY: 'SALES_HISTORY',
  CASH_HISTORY: 'CASH_HISTORY',
  PRODUCTS: 'PRODUCTS',
  CATEGORIES: 'CATEGORIES',
  TAGS: 'TAGS',
  SUPPLIERS: 'SUPPLIERS',
  PURCHASES: 'PURCHASES',
  DEBTS: 'DEBTS',
  CONSUMPTIONS: 'CONSUMPTIONS',
  FINANCES_SUMMARY: 'FINANCES_SUMMARY',
  FINANCES_TRANSACTIONS: 'FINANCES_TRANSACTIONS',
  FINANCES_ACCOUNTS: 'FINANCES_ACCOUNTS',
  FINANCES_CATEGORIES: 'FINANCES_CATEGORIES',
  REPORTS: 'REPORTS',
  USERS: 'USERS',
  MIGRATION: 'MIGRATION'
} as const;

export type PermissionKey = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

export interface RoleOption {
  value: Role;
  label: string;
  description: string;
}

export const ROLE_OPTIONS: RoleOption[] = [
  {
    value: 'OPERATOR',
    label: 'Operador',
    description: 'POS (caja, ventas, gastos y sus anulaciones), historiales de ventas y cajas, catálogo, proveedores, pedidos con sus pagos, deudas con personas y consumos internos. No entra a finanzas ni usuarios.'
  },
  {
    value: 'ADMINISTRATOR',
    label: 'Administrador',
    description: 'Todas las pantallas: lo del operador más finanzas, reportes, migración y usuarios.'
  }
];

export function roleLabel(role: Role | null | undefined): string {
  return ROLE_OPTIONS.find(option => option.value === role)?.label ?? '';
}

export const NAVIGATION_CONFIG: NavGroup[] = [
  {
    id: 'sales',
    label: 'Ventas',
    children: [
      {
        label: 'POS',
        route: '/pos',
        badge: 'Cerrada',
        permissionKey: PERMISSIONS.POS
      },
      {
        label: 'Ventas',
        route: '/sales',
        caption: 'Historial',
        permissionKey: PERMISSIONS.SALES_HISTORY
      },
      {
        label: 'Consumos',
        route: '/consumptions',
        caption: 'Al costo',
        permissionKey: PERMISSIONS.CONSUMPTIONS
      },
      {
        label: 'Historial de cajas',
        route: '/cash/history',
        caption: 'Cajas',
        permissionKey: PERMISSIONS.CASH_HISTORY
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
        permissionKey: PERMISSIONS.FINANCES_SUMMARY
      },
      {
        label: 'Transacciones',
        route: '/finances/transactions',
        permissionKey: PERMISSIONS.FINANCES_TRANSACTIONS
      },
      {
        label: 'Cuentas',
        route: '/finances/accounts',
        permissionKey: PERMISSIONS.FINANCES_ACCOUNTS
      },
      {
        label: 'Categorías',
        route: '/finances/categories',
        permissionKey: PERMISSIONS.FINANCES_CATEGORIES
      },
      {
        label: 'Reportes',
        route: '/reports',
        permissionKey: PERMISSIONS.REPORTS
      },
      {
        label: 'Migración',
        route: '/migration',
        caption: 'Una vez',
        permissionKey: PERMISSIONS.MIGRATION
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
        permissionKey: PERMISSIONS.PRODUCTS
      },
      {
        label: 'Categorías',
        route: '/categories',
        permissionKey: PERMISSIONS.CATEGORIES
      },
      {
        label: 'Etiquetas',
        route: '/tags',
        permissionKey: PERMISSIONS.TAGS
      }
    ]
  },
  {
    id: 'purchases',
    label: 'Compras',
    children: [
      {
        label: 'Pedidos',
        route: '/purchases',
        permissionKey: PERMISSIONS.PURCHASES
      },
      {
        label: 'Deudas',
        route: '/debts',
        permissionKey: PERMISSIONS.DEBTS
      },
      {
        label: 'Proveedores',
        route: '/suppliers',
        permissionKey: PERMISSIONS.SUPPLIERS
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
        permissionKey: PERMISSIONS.USERS
      }
    ]
  }
];
