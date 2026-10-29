import { SaleUnit } from '../../product/models/product.models';

// CON-01: internal consumption valued at cost; it moves no money
export type ConsumptionStatus = 'REGISTRADO' | 'ANULADO';

export const CONSUMPTION_STATUS_LABELS: Record<ConsumptionStatus, string> = {
  REGISTRADO: 'Registrado',
  ANULADO: 'Anulado'
};

export interface ConsumptionItem {
  id: string;
  productId: string;
  productName: string;
  productCode: string | null;
  saleUnit: SaleUnit;
  quantity: number;
  unitCost: number;
  subtotal: number;
  // CAT-11: copy of the consumed presentation and the line's kilos
  presentationId?: string | null;
  presentationName?: string | null;
  presentationWeightKg?: number | null;
  weightKg?: number | null;
}

export interface Consumption {
  id: string;
  consumptionNumber: string;
  consumptionDate: string;
  consumedFor: string | null;
  comment: string | null;
  totalAmount: number;
  status: ConsumptionStatus;
  createdBy: string;
  createdAt: string;
  voidedBy: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  // Empty in the list; the detail brings the lines
  items: ConsumptionItem[];
  // FIN-11: migrated from the spreadsheet; never voided or edited
  historical?: boolean;
}

export interface ConsumptionItemRequest {
  productId: string;
  quantity: number;
  presentationId?: string | null;
}

export interface ConsumptionRequest {
  // null: today (Bolivia time)
  consumptionDate: string | null;
  consumedFor: string | null;
  comment: string | null;
  items: ConsumptionItemRequest[];
}

export interface ConsumptionFilters {
  from?: string | null;
  to?: string | null;
  productId?: string | null;
  status?: ConsumptionStatus | null;
  search?: string | null;
}

export interface VoidConsumptionRequest {
  voidReason: string;
}
