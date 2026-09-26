export type SupplierStatus = 'ACTIVO' | 'INACTIVO' | 'ELIMINADO';

export interface Supplier {
  id: string;
  name: string;
  code: string;
  description: string | null;
  phone: string | null;
  status: SupplierStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  deletedBy: string | null;
  deletedAt: string | null;
}

export interface SupplierRequest {
  name: string;
  code: string;
  description?: string | null;
  phone?: string | null;
  status?: SupplierStatus;
}
