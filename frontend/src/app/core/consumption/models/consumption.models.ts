import { SaleUnit } from '../../product/models/product.models';

// CON-01: consumo interno valorado al costo; no mueve dinero
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
  // Vacío en el listado; el detalle trae las líneas
  items: ConsumptionItem[];
}

export interface ConsumptionItemRequest {
  productId: string;
  quantity: number;
}

export interface ConsumptionRequest {
  // null: hoy (hora de Bolivia)
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
