import { NavGroup } from './models/navigation.models';

export const NAVIGATION_CONFIG: NavGroup[] = [
  {
    id: 'sales',
    label: 'Ventas',
    children: [
      {
        label: 'POS',
        route: '/pos',
        badge: 'Cerrada'
      },
      {
        label: 'Ventas',
        route: '/sales',
        caption: 'Historial'
      },
      {
        label: 'Historial de cajas',
        route: '/cash/history',
        caption: 'Cajas'
      }
    ]
  },
  {
    id: 'catalog',
    label: 'Catálogo',
    children: [
      {
        label: 'Productos',
        route: '/products'
      },
      {
        label: 'Categorías',
        route: '/categories'
      },
      {
        label: 'Etiquetas',
        route: '/tags'
      }
    ]
  },
  {
    id: 'purchases',
    label: 'Compras',
    children: [
      {
        label: 'Proveedores',
        route: '/suppliers'
      }
    ]
  },
  {
    id: 'security',
    label: 'Seguridad',
    children: [
      {
        label: 'Usuarios',
        route: '/users'
      }
    ]
  }
];
