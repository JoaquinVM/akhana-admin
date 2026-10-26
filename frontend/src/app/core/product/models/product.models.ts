export type ProductStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-08: KILOGRAM means the sell price is Bs per kg and quantities take up to 3 decimals
export type SaleUnit = 'UNIT' | 'KILOGRAM';

// CAT-11: peso fijo de un producto por kilo; sin precio fijo, el precio es peso x precio por kilo.
// price y cost vienen calculados del servidor
export interface ProductPresentation {
  id: string;
  name: string;
  weightKg: number;
  fixedPrice: number | null;
  price: number;
  cost: number;
  active: boolean;
}

export interface ProductPresentationRequest {
  id?: string | null;
  name: string;
  weightKg: number;
  fixedPrice?: number | null;
  active: boolean;
}

export interface ProductTagItem {
  id: string;
  name: string;
  color: string;
}

export interface Product {
  id: string;
  code: string;
  name: string;
  categoryId: string;
  categoryName: string;
  categoryColor: string;
  supplierId: string;
  supplierName: string;
  description?: string | null;
  tags: ProductTagItem[];
  buyPrice: number;
  sellPrice: number;
  fixedProfit: number;
  percentageProfit: number;
  status: ProductStatus;
  saleUnit: SaleUnit;
  createdBy: string;
  createdAt: string;
  updatedBy?: string | null;
  updatedAt?: string | null;
  deletedBy?: string | null;
  deletedAt?: string | null;
  // CAT-11: activas e inactivas, de menor a mayor peso
  presentations?: ProductPresentation[];
  // Avisos al guardar (venta a pérdida, precios fijos que no cambian)
  warnings?: string[];
}

export interface ProductRequest {
  code: string;
  name: string;
  categoryId: string;
  supplierId: string;
  description?: string | null;
  tagIds: string[];
  buyPrice: number;
  sellPrice: number;
  status?: ProductStatus;
  saleUnit?: SaleUnit;
  // CAT-11: la lista completa; sin ella el servidor mantiene las presentaciones
  presentations?: ProductPresentationRequest[];
}

// CAT-11: al marcar un producto por kilo se proponen estas, sin precio fijo
export const SUGGESTED_PRESENTATIONS: ReadonlyArray<{ name: string; weightKg: number }> = [
  { name: '100 g', weightKg: 0.1 },
  { name: '1/2 libra', weightKg: 0.227 }
];

export function activePresentations(product: Product): ProductPresentation[] {
  return (product.presentations ?? []).filter((p) => p.active);
}

// CAT-11: al costo se cobra peso x costo por kilo, sin el precio fijo
export function presentationPrice(presentation: ProductPresentation, product: Product, atCost: boolean): number {
  if (atCost) return Math.round(presentation.weightKg * product.buyPrice * 100) / 100;
  return presentation.price;
}
