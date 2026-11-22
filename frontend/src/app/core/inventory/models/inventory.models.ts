import { Product, SaleUnit, StockStatus } from '../../product/models/product.models';

// INV-10: the error the server answers when a sale or consumption takes more than the recorded stock
export const INSUFFICIENT_STOCK = 'Insufficient Stock';

export interface StockPackage {
  name: string;
  baseQuantity: number;
}

// INV-01, INV-14: a row of Existencias
export interface StockItem {
  productId: string;
  code: string;
  name: string;
  categoryId: string | null;
  categoryName: string | null;
  supplierName: string | null;
  saleUnit: SaleUnit;
  status: StockStatus;
  negative: boolean;
  quantity: number | null;
  averageCost: number | null;
  value: number | null;
  incoming: number;
  packages: StockPackage[];
}

export interface StockLevel {
  productId: string;
  quantity: number;
}

export type StockMovementType = 'INITIAL_LOAD' | 'PURCHASE' | 'PURCHASE_REVERSAL' | 'PURCHASE_CORRECTION' | 'SALE'
  | 'AT_COST_SALE' | 'CONSUMPTION' | 'SALE_VOID' | 'CONSUMPTION_VOID' | 'COUNT_ADJUSTMENT' | 'ADJUSTMENT' | 'AVERAGE_RESET';

export interface KardexRow {
  id: string;
  createdAt: string;
  type: StockMovementType;
  typeLabel: string;
  reference: string | null;
  reason: string | null;
  comment: string | null;
  quantity: number;
  unitCost: number | null;
  value: number;
  expensedValue: number;
  balanceQuantity: number;
  balanceValue: number;
  balanceAverageCost: number | null;
  createdBy: string;
}

// INV-03
export interface Kardex {
  productId: string;
  code: string;
  name: string;
  saleUnit: SaleUnit;
  status: StockStatus;
  from: string;
  to: string;
  openingQuantity: number;
  openingValue: number;
  openingAverageCost: number | null;
  rows: KardexRow[];
  quantity: number;
  value: number;
  averageCost: number | null;
  packages: StockPackage[];
}

export type StockAdjustmentReason = 'MERMA' | 'ROTURA' | 'VENCIMIENTO' | 'SOBRANTE';

export const ADJUSTMENT_REASONS: { value: StockAdjustmentReason; label: string; adds: boolean }[] = [
  { value: 'MERMA', label: 'Merma', adds: false },
  { value: 'ROTURA', label: 'Rotura sin nadie que pague', adds: false },
  { value: 'VENCIMIENTO', label: 'Vencimiento', adds: false },
  { value: 'SOBRANTE', label: 'Sobrante', adds: true }
];

export interface StockAdjustmentRequest {
  reason: StockAdjustmentReason;
  quantity: number;
  comment?: string | null;
}

export type StockCountStatus = 'ABIERTO' | 'CONFIRMADO' | 'DESCARTADO';

export interface CountPackage {
  id: string;
  name: string;
  baseQuantity: number;
  barcode: string | null;
}

export interface CountProduct {
  productId: string;
  code: string;
  name: string;
  saleUnit: SaleUnit;
  status: StockStatus;
  barcodes: string[];
  packages: CountPackage[];
  countedQuantity: number | null;
  breakdown: string | null;
  countedBy: string | null;
  countedAt: string | null;
}

// INV-05: blind while open: no stock, no expected quantity
export interface StockCount {
  id: string;
  number: number;
  categoryId: string | null;
  categoryName: string | null;
  status: StockCountStatus;
  createdBy: string;
  createdAt: string;
  closedBy: string | null;
  closedAt: string | null;
  countedProducts: number;
  products: CountProduct[];
}

export interface StockCountLineRequest {
  packages: { presentationId: string; quantity: number }[];
  loose: number;
}

export interface StockCountReviewLine {
  productId: string;
  code: string;
  name: string;
  saleUnit: SaleUnit;
  initialLoad: boolean;
  countedQuantity: number;
  breakdown: string;
  expectedQuantity: number;
  difference: number;
  unitCost: number;
  differenceValue: number;
}

export interface StockCountReview {
  id: string;
  number: number;
  categoryName: string | null;
  lines: StockCountReviewLine[];
  notCounted: number;
  differenceValue: number;
}

// «3» or «0.250 kg»: a stock in the base unit (CAT-14)
export function stockQuantityText(quantity: number, saleUnit: SaleUnit | null | undefined): string {
  return saleUnit === 'KILOGRAM' ? `${quantity.toFixed(3)} kg` : `${Math.round(quantity)}`;
}

// INV-11: «Quedan N» for a product that controls its stock; nothing for the others
export function remainingText(product: Pick<Product, 'stockStatus' | 'stock' | 'saleUnit'>): string | null {
  if (product.stockStatus !== 'CONTROLLED' || product.stock == null) return null;
  return `Quedan ${stockQuantityText(product.stock, product.saleUnit)}`;
}

// CAT-15: «40 = 3 Caja ×12 + 4», with the largest package first; nothing when there are no packages or it is not whole
export function packageEquivalence(quantity: number, saleUnit: SaleUnit, packages: StockPackage[]): string | null {
  if (saleUnit !== 'UNIT' || packages.length === 0 || quantity <= 0) return null;
  let left = Math.round(quantity);
  const parts: string[] = [];
  for (const pkg of [...packages].sort((a, b) => b.baseQuantity - a.baseQuantity)) {
    const size = Math.round(pkg.baseQuantity);
    if (size < 2) continue;
    const count = Math.floor(left / size);
    if (count > 0) {
      parts.push(`${count} ${pkg.name}`);
      left -= count * size;
    }
  }
  if (parts.length === 0) return null;
  if (left > 0) parts.push(`${left}`);
  return `${Math.round(quantity)} = ${parts.join(' + ')}`;
}

// INV-10: «No hay stock registrado de X (quedan N). ¿Vender igual?» for each product that takes more than it has
export function stockShortages(lines: { product: Product; baseQuantity: number }[]): string[] {
  const byProduct = new Map<string, { product: Product; quantity: number }>();
  for (const line of lines) {
    if (line.product.stockStatus !== 'CONTROLLED' || line.product.stock == null) continue;
    const entry = byProduct.get(line.product.id) ?? { product: line.product, quantity: 0 };
    entry.quantity += line.baseQuantity;
    byProduct.set(line.product.id, entry);
  }
  const messages: string[] = [];
  for (const { product, quantity } of byProduct.values()) {
    const stock = product.stock ?? 0;
    if (Math.round(quantity * 1000) > Math.round(stock * 1000)) {
      messages.push(`No hay stock registrado de ${product.name} (quedan ${stockQuantityText(stock, product.saleUnit)}). ¿Vender igual?`);
    }
  }
  return messages;
}
