import { Product, ProductPresentation } from '../../../core/product/models/product.models';

// One line of the register-sale cart
export interface CartItem {
  key: string;
  product: Product;
  presentation: ProductPresentation | null;
  quantity: number;
  unitPrice: number;
  unitDiscount: number;
  unitFinalPrice: number;
  subtotal: number;
  // POS-11: free line of a special product, with its typed name; each one is its own line
  freeName?: string | null;
}

export function isWeightedProduct(product: Product): boolean {
  return product.saleUnit === 'KILOGRAM';
}

// «Otro peso»: quantity in kilos with up to 3 decimals
export function isWeightLine(item: CartItem): boolean {
  return isWeightedProduct(item.product) && !item.presentation;
}

export function lineName(item: CartItem): string {
  if (item.freeName) return item.freeName;
  return item.presentation ? `${item.product.name} · ${item.presentation.name}` : item.product.name;
}

// POS-11, POS-13: who the free line belongs to
export function freeLineLabel(item: CartItem): string {
  return item.product.specialType === 'THIRD_PARTY'
    ? `De ${item.product.ownerCreditorName ?? 'tercero'}`
    : 'Sin identificar';
}
