export type ProductStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-08: KILOGRAM means the sell price is Bs per kg and quantities take up to 3 decimals
export type SaleUnit = 'UNIT' | 'KILOGRAM';

// POS-11: productos especiales que se venden en el POS con nombre y precio escritos
export type ProductSpecialType = 'UNIDENTIFIED' | 'THIRD_PARTY';

export const SPECIAL_TYPE_LABELS: Record<ProductSpecialType, string> = {
  UNIDENTIFIED: 'Sin identificar',
  THIRD_PARTY: 'De tercero'
};

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
  // POS-11, POS-13: nulos en los productos normales
  specialType?: ProductSpecialType | null;
  ownerCreditorId?: string | null;
  ownerCreditorName?: string | null;
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
  // POS-11: se elige al crear y no cambia; el de tercero exige dueña (POS-13)
  specialType?: ProductSpecialType | null;
  ownerCreditorId?: string | null;
}

// CAT-11: al marcar un producto por kilo se proponen estas, sin precio fijo
export const SUGGESTED_PRESENTATIONS: ReadonlyArray<{ name: string; weightKg: number }> = [
  { name: '100 g', weightKg: 0.1 },
  { name: '1/2 libra', weightKg: 0.227 }
];

export function isSpecialProduct(product: Pick<Product, 'specialType'>): boolean {
  return !!product.specialType;
}

export function activePresentations(product: Product): ProductPresentation[] {
  return (product.presentations ?? []).filter((p) => p.active);
}

// CAT-11: al costo se cobra peso x costo por kilo, sin el precio fijo
export function presentationPrice(presentation: ProductPresentation, product: Product, atCost: boolean): number {
  if (atCost) return Math.round(presentation.weightKg * product.buyPrice * 100) / 100;
  return presentation.price;
}
