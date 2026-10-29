import type { PaymentMethod, SaleStatus, SaleItem, Sale, SaleType } from '../../cash/models/cash.models';

import type { PageResponse } from '../../user/models/user.models';

export type { PaymentMethod, SaleStatus, SaleItem, Sale, SaleType, PageResponse };

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  EFECTIVO: 'Efectivo',
  QR: 'QR',
  MIXTO: 'Mixto',
  CREDITO: 'Crédito',
  HISTORICO: 'Histórico'
};

// POS-09: history filters; a method includes the sales that used it (Efectivo and QR include Mixto,
// Crédito includes the at-cost sales partly on credit)
export interface SaleFilters {
  from?: string | null;
  to?: string | null;
  status?: SaleStatus | null;
  saleType?: SaleType | null;
  paymentMethod?: PaymentMethod | null;
  createdBy?: string | null;
  // POS-12: only sales with lines pending assignment
  pendingAssignment?: boolean;
}

export interface SaleItemRequest {
  productId: string;
  quantity: number;
  discountPerUnit?: number;
  // CAT-11: with a presentation the quantity is whole packs
  presentationId?: string | null;
  // POS-11: typed name and price, only for special products
  freeName?: string | null;
  freePrice?: number | null;
}

export interface SaleDetailRequest {
  items: SaleItemRequest[];
  globalDiscountAmount?: number;
  paymentMethod: PaymentMethod;
  amountCash?: number;
  amountQr?: number;
  amountReceived?: number;
  description?: string;
  // CON-02 / DEU-05: without saleType the sale is NORMAL; credit requires an active customer
  saleType?: SaleType;
  customerId?: string | null;
  creditAmount?: number;
}

export interface VoidSaleRequest {
  voidReason: string;
}
