export interface Category {
  id: string;
  name: string;
  description?: string;
  color: string;
  createdAt: string;
}

export interface Provider {
  id: string;
  code: string;
  name: string;
  description?: string;
  phone?: string;
  createdAt: string;
}

export interface Group {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
}

export interface Tag {
  id: string;
  name: string;
  description?: string;
  color: string;
  createdAt: string;
}

export type SaleType = 'unit' | 'weight';

export interface Product {
  id: string;
  code: string;
  barcode?: string;
  name: string;
  groupId: string;
  providerId: string;
  tagIds: string[];
  cost: number;
  salePrice: number;
  saleType: SaleType;
  isFrequent: boolean;
  isActive: boolean;
  imageUrl?: string;
  currentStock?: number;
  createdAt: string;
  updatedAt?: string;
}

export interface DenominationBreakdown {
  [denomination: string]: number; // e.g. "200": 2, "100": 5, "50": 4, "20": 10, "10": 15, "5": 20, "2": 25, "1": 30, "0.5": 20, "0.2": 15, "0.1": 10
}

export interface CashRegisterSession {
  id: string;
  registerNumber: string;
  openedAt: string;
  closedAt?: string;
  openedBy: string;
  closedBy?: string;
  initialAmount: number;
  isOpen: boolean;
  cashSalesTotal: number;
  qrSalesTotal: number;
  expectedCash: number;
  countedCash?: number;
  difference?: number;
  breakdown?: DenominationBreakdown;
  notes?: string;
}

export interface SaleItem {
  productId: string;
  productCode: string;
  barcode?: string;
  productName: string;
  saleType: SaleType;
  quantity: number; // Unit quantity or grams for weighed items
  unitCost: number;
  unitPrice: number; // Base unit price or price per 100g
  grossSubtotal: number;
  discount: number; // Fixed amount discount in Bs
  finalSubtotal: number;
}

export type PaymentMethod = 'CASH' | 'QR' | 'MIXED';
export type SaleStatus = 'COMPLETADA' | 'ANULADA';

export interface Sale {
  id: string;
  orderNumber: string;
  registerId: string;
  cashierId: string;
  cashierName: string;
  customerName: string;
  customerTaxId?: string;
  items: SaleItem[];
  grossSubtotal: number;
  itemsDiscountTotal: number;
  globalDiscount: number;
  totalDiscounts: number;
  netTotal: number;
  tax13: number;
  paymentMethod: PaymentMethod;
  cashAmountReceived: number;
  qrAmountReceived: number;
  changeGiven: number;
  status: SaleStatus;
  voidReason?: string;
  voidedBy?: string;
  voidedAt?: string;
  createdAt: string;
  updatedAt?: string;
}

export type UserRole = 'ADMIN' | 'VENDEDOR';

export interface User {
  id: string;
  username: string;
  fullName: string;
  role: UserRole;
  avatarUrl?: string;
}

export interface SystemConfig {
  maxItemDiscount: number; // Default 5 Bs
  maxGlobalDiscount: number; // Default 20 Bs
  defaultTaxRate: number; // 0.13 (13% IVA Bolivia)
  currencySymbol: string; // 'Bs'
  googleAppsScriptUrl?: string;
}
