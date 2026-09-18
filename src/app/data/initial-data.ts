import { Category, Provider, Group, Tag, Product, User, SystemConfig } from '../models/domain.model';

export const INITIAL_CONFIG: SystemConfig = {
  maxItemDiscount: 5.0, // Máximo 5 Bs de descuento por producto
  maxGlobalDiscount: 20.0, // Máximo 20 Bs de descuento global por venta
  defaultTaxRate: 0.13, // 13% IVA Bolivia
  currencySymbol: 'Bs',
  googleAppsScriptUrl: ''
};

export const INITIAL_USERS: User[] = [
  {
    id: 'user-01',
    username: 'admin',
    fullName: 'Administrador Akhana',
    role: 'ADMIN',
    avatarUrl: ''
  },
  {
    id: 'user-02',
    username: 'cajero',
    fullName: 'Juan Pérez',
    role: 'VENDEDOR',
    avatarUrl: ''
  }
];

export const INITIAL_CATEGORIES: Category[] = [
  { id: 'cat-1', name: 'Panadería & Granos', description: 'Panes artesanales y horneados diarios', color: '#E5A823', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'cat-2', name: 'Charcutería & Embutidos', description: 'Cortes seleccionados y embutidos premium', color: '#C23B2A', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'cat-3', name: 'Bebidas & Cafés', description: 'Bebidas frías, jugos naturales y café orgánico', color: '#3B6C2B', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'cat-4', name: 'Aceites & Abarrotes', description: 'Aceites de oliva, salsas y despensa gourmet', color: '#7EB53F', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'cat-5', name: 'Lácteos & Quesos', description: 'Quesos de granja, leche fresca y yogur', color: '#2563EB', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'cat-6', name: 'Snacks Saludables', description: 'Frutos secos, barras y chocolates orgánicos', color: '#D97706', createdAt: '2026-09-01T08:00:00Z' }
];

export const INITIAL_GROUPS: Group[] = [
  { id: 'grp-1', name: 'Alimentos Frescos', description: 'Perecederos y rotación diaria', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'grp-2', name: 'Despensa Gourmet', description: 'Conservas y aceites de larga vida', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'grp-3', name: 'Bebidas Comerciales', description: 'Gaseosas y aguas embotelladas', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'grp-4', name: 'Panadería y Pastelería', description: 'Horneados del día', createdAt: '2026-09-01T08:00:00Z' }
];

export const INITIAL_PROVIDERS: Provider[] = [
  { id: 'prov-1', code: 'PRV-001', name: 'Distribuidora Andina SRL', description: 'Bebidas y gaseosas', phone: '+591 2 2445566', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'prov-2', code: 'PRV-002', name: 'Finca El Valle Orgánico', description: 'Aceites y conservas', phone: '+591 4 4556677', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'prov-3', code: 'PRV-003', name: 'Charcutería Serrana', description: 'Embutidos y fiambres al corte', phone: '+591 3 3667788', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'prov-4', code: 'PRV-004', name: 'Molinos del Sur', description: 'Harinas y panificación', phone: '+591 2 2778899', createdAt: '2026-09-01T08:00:00Z' }
];

export const INITIAL_TAGS: Tag[] = [
  { id: 'tag-1', name: 'Alta Rotación', description: 'Productos de rápida salida', color: '#E5A823', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'tag-2', name: 'Orgánico', description: 'Certificación biológica 100%', color: '#3B6C2B', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'tag-3', name: 'Refrigerado', description: 'Requiere cadena de frío', color: '#2563EB', createdAt: '2026-09-01T08:00:00Z' },
  { id: 'tag-4', name: 'Pesable', description: 'Venta por balanza en gramos', color: '#7EB53F', createdAt: '2026-09-01T08:00:00Z' }
];

export const INITIAL_PRODUCTS: Product[] = [
  // 1. Panadería & Desayuno
  {
    id: 'prod-01',
    code: 'PAN-001',
    barcode: '7771234001',
    name: 'Pan Baguette Tradicional',
    groupId: 'grp-4',
    providerId: 'prov-4',
    tagIds: ['tag-1'],
    cost: 0.50,
    salePrice: 0.90,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 45,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-02',
    code: 'PAN-002',
    barcode: '7771234002',
    name: 'Medialuna de Manteca',
    groupId: 'grp-4',
    providerId: 'prov-4',
    tagIds: ['tag-1'],
    cost: 0.40,
    salePrice: 0.75,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 60,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-03',
    code: 'PAN-003',
    barcode: '7771234003',
    name: 'Pan de Molde Integral 500g',
    groupId: 'grp-4',
    providerId: 'prov-4',
    tagIds: ['tag-2'],
    cost: 1.10,
    salePrice: 2.00,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 25,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-04',
    code: 'LAC-001',
    barcode: '7771234004',
    name: 'Leche Fresca Entera 1L',
    groupId: 'grp-1',
    providerId: 'prov-1',
    tagIds: ['tag-1', 'tag-3'],
    cost: 0.90,
    salePrice: 1.50,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 30,
    createdAt: '2026-09-01T08:00:00Z'
  },

  // 2. Charcutería & Fiambres (Pesables & Cortes)
  {
    id: 'prod-05',
    code: 'CHA-001',
    barcode: '7771234005',
    name: 'Jamón Serrano Selección (100g)',
    groupId: 'grp-1',
    providerId: 'prov-3',
    tagIds: ['tag-1', 'tag-3', 'tag-4'],
    cost: 2.80,
    salePrice: 4.50,
    saleType: 'weight',
    isFrequent: true,
    isActive: true,
    currentStock: 12500, // 12.5 kg en gramos
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-06',
    code: 'CHA-002',
    barcode: '7771234006',
    name: 'Queso Gouda Artesanal (100g)',
    groupId: 'grp-1',
    providerId: 'prov-3',
    tagIds: ['tag-3', 'tag-4'],
    cost: 2.20,
    salePrice: 3.80,
    saleType: 'weight',
    isFrequent: true,
    isActive: true,
    currentStock: 8500,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-07',
    code: 'CHA-003',
    barcode: '7771234007',
    name: 'Salame Crespón Italiano (100g)',
    groupId: 'grp-1',
    providerId: 'prov-3',
    tagIds: ['tag-3', 'tag-4'],
    cost: 1.90,
    salePrice: 3.20,
    saleType: 'weight',
    isFrequent: true,
    isActive: true,
    currentStock: 6200,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-08',
    code: 'CHA-004',
    barcode: '7771234008',
    name: 'Mortadela con Pistachos (100g)',
    groupId: 'grp-1',
    providerId: 'prov-3',
    tagIds: ['tag-4'],
    cost: 1.10,
    salePrice: 1.80,
    saleType: 'weight',
    isFrequent: true,
    isActive: true,
    currentStock: 14000,
    createdAt: '2026-09-01T08:00:00Z'
  },

  // 3. Bebidas & Café
  {
    id: 'prod-09',
    code: 'BEB-001',
    barcode: '7771234009',
    name: 'Coca-Cola 500ml',
    groupId: 'grp-3',
    providerId: 'prov-1',
    tagIds: ['tag-1'],
    cost: 1.10,
    salePrice: 1.80,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 72,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-10',
    code: 'BEB-002',
    barcode: '7771234010',
    name: 'Agua Mineral Manantial 1L',
    groupId: 'grp-3',
    providerId: 'prov-1',
    tagIds: ['tag-1'],
    cost: 0.50,
    salePrice: 1.00,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 48,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-11',
    code: 'BEB-003',
    barcode: '7771234011',
    name: 'Café Espresso Orgánico Akhana',
    groupId: 'grp-3',
    providerId: 'prov-2',
    tagIds: ['tag-1', 'tag-2'],
    cost: 0.80,
    salePrice: 2.00,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 100,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-12',
    code: 'BEB-004',
    barcode: '7771234012',
    name: 'Jugo de Naranja Natural 500ml',
    groupId: 'grp-3',
    providerId: 'prov-2',
    tagIds: ['tag-1', 'tag-2'],
    cost: 1.20,
    salePrice: 2.20,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 20,
    createdAt: '2026-09-01T08:00:00Z'
  },

  // 4. Aceites & Despensa
  {
    id: 'prod-13',
    code: 'ABA-001',
    barcode: '7771234013',
    name: 'Aceite de Oliva Extra Virgen 500ml',
    groupId: 'grp-2',
    providerId: 'prov-2',
    tagIds: ['tag-2'],
    cost: 6.00,
    salePrice: 9.00,
    saleType: 'unit',
    isFrequent: false,
    isActive: true,
    currentStock: 18,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-14',
    code: 'ABA-002',
    barcode: '7771234014',
    name: 'Vinagre Balsámico Modena 250ml',
    groupId: 'grp-2',
    providerId: 'prov-2',
    tagIds: ['tag-2'],
    cost: 3.50,
    salePrice: 5.50,
    saleType: 'unit',
    isFrequent: false,
    isActive: true,
    currentStock: 14,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-15',
    code: 'SNK-001',
    barcode: '7771234015',
    name: 'Barra Energética de Quinua y Miel',
    groupId: 'grp-2',
    providerId: 'prov-2',
    tagIds: ['tag-1', 'tag-2'],
    cost: 0.60,
    salePrice: 1.10,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 40,
    createdAt: '2026-09-01T08:00:00Z'
  },
  {
    id: 'prod-16',
    code: 'SNK-002',
    barcode: '7771234016',
    name: 'Papas Rústicas Artesanales 150g',
    groupId: 'grp-2',
    providerId: 'prov-4',
    tagIds: ['tag-1'],
    cost: 1.30,
    salePrice: 2.40,
    saleType: 'unit',
    isFrequent: true,
    isActive: true,
    currentStock: 35,
    createdAt: '2026-09-01T08:00:00Z'
  }
];
