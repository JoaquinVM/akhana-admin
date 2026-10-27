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

// POS-09: filtros del historial; un método incluye las ventas que lo usaron (Efectivo y QR incluyen Mixto,
// Crédito incluye las ventas al costo con una parte a crédito)
export interface SaleFilters {
  from?: string | null;
  to?: string | null;
  status?: SaleStatus | null;
  saleType?: SaleType | null;
  paymentMethod?: PaymentMethod | null;
  createdBy?: string | null;
  // POS-12: solo ventas con líneas por asignar
  pendingAssignment?: boolean;
}

export interface SaleItemRequest {
  productId: string;
  quantity: number;
  discountPerUnit?: number;
  // CAT-11: con presentación la cantidad son bolsitas enteras
  presentationId?: string | null;
  // POS-11: nombre y precio escritos, solo en productos especiales
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
  // CON-02 / DEU-05: sin saleType la venta es NORMAL; el crédito exige cliente activo
  saleType?: SaleType;
  customerId?: string | null;
  creditAmount?: number;
}

export interface VoidSaleRequest {
  voidReason: string;
}
