export type CategoryStatus = 'ACTIVO' | 'ELIMINADO';

export interface Category {
  id: string;
  name: string;
  description: string | null;
  color: string;
  status: CategoryStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  deletedBy: string | null;
  deletedAt: string | null;
}

export interface CategoryRequest {
  name: string;
  description?: string | null;
  color: string;
  status?: CategoryStatus;
}
