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
  // CAT-11: copia de la presentación consumida y los kilos de la línea
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
  // Vacío en el listado; el detalle trae las líneas
  items: ConsumptionItem[];
  // FIN-11: migrado de la planilla; no se anula ni se edita
  historical?: boolean;
}

export interface ConsumptionItemRequest {
  productId: string;
  quantity: number;
  presentationId?: string | null;
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
