import { NavGroup } from './models/navigation.models';

export const NAVIGATION_CONFIG: NavGroup[] = [
  {
    id: 'sales',
    label: 'Ventas',
    children: [
      {
        label: 'POS',
        route: '/pos',
        badge: 'Activo'
      },
      {
        label: 'Ventas',
        route: '/sales',
        caption: 'Historial'
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
