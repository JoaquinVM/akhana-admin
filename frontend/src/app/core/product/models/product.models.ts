export type ProductStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-08: KILOGRAM means the sell price is Bs per kg and quantities take up to 3 decimals
export type SaleUnit = 'UNIT' | 'KILOGRAM';

// POS-11: special products sold in the POS with a typed name and price
export type ProductSpecialType = 'UNIDENTIFIED' | 'THIRD_PARTY';

export const SPECIAL_TYPE_LABELS: Record<ProductSpecialType, string> = {
  UNIDENTIFIED: 'Sin identificar',
  THIRD_PARTY: 'De tercero'
};

// CAT-11: fixed weight of a product by kilo; without a fixed price, the price is weight x price per kilo.
// price and cost come computed from the server
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
  // CAT-11: active and inactive, lightest first
  presentations?: ProductPresentation[];
  // Warnings on save (sold at a loss, fixed prices that do not change)
  warnings?: string[];
  // POS-11, POS-13: null for normal products
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
  // CAT-11: the full list; without it the server keeps the presentations
  presentations?: ProductPresentationRequest[];
  // POS-11: chosen on create and never changed; a third-party one requires an owner (POS-13)
  specialType?: ProductSpecialType | null;
  ownerCreditorId?: string | null;
}

// CAT-11: marking a product by kilo proposes these, without fixed price
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

// CAT-11: at cost it charges weight x cost per kilo, without the fixed price
export function presentationPrice(presentation: ProductPresentation, product: Product, atCost: boolean): number {
  if (atCost) return Math.round(presentation.weightKg * product.buyPrice * 100) / 100;
  return presentation.price;
}
