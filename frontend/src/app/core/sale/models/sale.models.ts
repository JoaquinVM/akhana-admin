import type { PaymentMethod, SaleStatus, SaleItem, Sale } from '../../cash/models/cash.models';

export type { PaymentMethod, SaleStatus, SaleItem, Sale };

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
}

export interface VoidSaleRequest {
  voidReason: string;
}
