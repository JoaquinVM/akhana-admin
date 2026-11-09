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
  // POS-15: at least one read of this line was a scan
  scanned?: boolean;
  // POS-10: total typed on a line of «Otro peso», charged instead of weight x price per kilo
  lineTotal?: number | null;
  // POS-10: «Otro peso» written in grams; quantity stays in kilos
  inGrams?: boolean;
}

// Quantity x price as the server rounds it (HALF_UP), in whole grams and cents so the half cent is exact
export function lineAmount(quantity: number, price: number): number {
  const product = Math.round(quantity * 1000) * Math.round(price * 100);
  return Math.floor((product + 500) / 1000) / 100;
}

// The line's gross amount as the server books it: a total raised by hand replaces weight x price per kilo
export function lineGross(item: CartItem): number {
  const calculated = lineAmount(item.quantity, item.unitPrice);
  return item.lineTotal != null && item.lineTotal > calculated ? item.lineTotal : calculated;
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
