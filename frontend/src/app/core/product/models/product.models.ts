export type ProductStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

// CAT-08: KILOGRAM means the sell price is Bs per kg and quantities take up to 3 decimals
export type SaleUnit = 'UNIT' | 'KILOGRAM';

// POS-11: special products sold in the POS with a typed name and price
export type ProductSpecialType = 'UNIDENTIFIED' | 'THIRD_PARTY';

export const SPECIAL_TYPE_LABELS: Record<ProductSpecialType, string> = {
  UNIDENTIFIED: 'Sin identificar',
  THIRD_PARTY: 'De tercero'
};

// CAT-15: a package of a product: how many base units it holds (Caja ×12 = 12, 1/2 libra = 0.227 kg). The
// presentations of CAT-11 are the packages that are sold. Without its own price, a sold package costs base quantity x
// product price; price (null when it is not sold) and cost come computed from the server
export interface ProductPresentation {
  id: string;
  name: string;
  baseQuantity: number;
  fixedPrice: number | null;
  price: number | null;
  cost: number;
  active: boolean;
  sold: boolean;
  barcode?: string | null;
}

export interface ProductPresentationRequest {
  id?: string | null;
  name: string;
  baseQuantity: number;
  fixedPrice?: number | null;
  active: boolean;
  sold: boolean;
  // Blank: no barcode
  barcode: string;
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
  // CAT-15: packages, active and inactive, smallest first
  presentations?: ProductPresentation[];
  // Warnings on save (sold at a loss, fixed prices that do not change)
  warnings?: string[];
  // POS-11, POS-13: null for normal products
  specialType?: ProductSpecialType | null;
  ownerCreditorId?: string | null;
  ownerCreditorName?: string | null;
  // CAT-12: reference to the active photo; the image is requested apart
  photoId?: string | null;
  // CAT-10: the product's own active barcodes (those of its packages come with each package), and «No trae»
  barcodes?: string[];
  noBarcode?: boolean;
}

// COM-10: a product that looks like a box of another one, with the package it would become
export interface BoxProposal {
  boxProductId: string;
  boxCode: string;
  boxName: string;
  boxSellPrice: number;
  boxBuyPrice: number;
  boxBarcodes: string[];
  supplierName: string | null;
  baseProductId: string;
  baseCode: string;
  baseName: string;
  baseSellPrice: number;
  baseBuyPrice: number;
  proposedName: string;
  proposedBaseQuantity: number;
  proposedPrice: number;
}

export interface BoxConversionRequest {
  name: string;
  baseQuantity: number;
  fixedPrice: number | null;
  barcode: string | null;
}

// CAT-10: a row of «Cargar códigos», most sold first
export interface BarcodePendingProduct {
  id: string;
  code: string;
  name: string;
  supplierName: string | null;
  sellPrice: number;
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
  // CAT-15: the full list; without it the server keeps the packages
  presentations?: ProductPresentationRequest[];
  // POS-11: chosen on create and never changed; a third-party one requires an owner (POS-13)
  specialType?: ProductSpecialType | null;
  ownerCreditorId?: string | null;
  // CAT-10: the full list; without it the server keeps the barcodes
  barcodes?: string[];
}

// CAT-11: marking a product by kilo proposes these, without fixed price
export const SUGGESTED_PRESENTATIONS: ReadonlyArray<{ name: string; baseQuantity: number }> = [
  { name: '100 g', baseQuantity: 0.1 },
  { name: '1/2 libra', baseQuantity: 0.227 }
];

export function isSpecialProduct(product: Pick<Product, 'specialType'>): boolean {
  return !!product.specialType;
}

export function activePresentations(product: Product): ProductPresentation[] {
  return (product.presentations ?? []).filter((p) => p.active);
}

// CAT-15: the packages the POS and Consumos offer: active and sold whole
export function soldPresentations(product: Product): ProductPresentation[] {
  return activePresentations(product).filter((p) => p.sold);
}

// CAT-11, CAT-15: at cost it charges base quantity x cost, without its own price; both come computed by the server
export function presentationPrice(presentation: ProductPresentation, atCost: boolean): number {
  return atCost ? presentation.cost : presentation.price ?? presentation.cost;
}

// «12 u» or «0.227 kg»: a quantity in the product's base unit
export function baseQuantityText(quantity: number, saleUnit: SaleUnit | null | undefined): string {
  return saleUnit === 'KILOGRAM' ? `${quantity.toFixed(3)} kg` : `${Math.round(quantity)} u`;
}

// CAT-15: the package of a product a barcode belongs to, if any
export function presentationByBarcode(product: Product, barcode: string): ProductPresentation | null {
  return activePresentations(product).find((p) => p.barcode === barcode) ?? null;
}
