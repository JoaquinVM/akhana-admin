import type { PaymentMethod, SaleStatus, SaleItem, Sale, SaleType } from '../../cash/models/cash.models';

export type { PaymentMethod, SaleStatus, SaleItem, Sale, SaleType };

export interface SaleItemRequest {
  productId: string;
  quantity: number;
  discountPerUnit?: number;
}

export interface SaleDetailRequest {
  items: SaleItemRequest[];
  globalDiscountAmount?: number;
  paymentMethod: PaymentMethod;
  amountCash?: number;
  amountQr?: number;
  amountReceived?: number;
  description?: string;
  // CON-02 / DEU-05: sin saleType la venta es NORMAL; el crédito exige cliente activo
  saleType?: SaleType;
  customerId?: string | null;
  creditAmount?: number;
}

export interface VoidSaleRequest {
  voidReason: string;
}
