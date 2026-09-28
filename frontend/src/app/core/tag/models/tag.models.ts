export type TagStatus = 'ACTIVO' | 'ELIMINADO';

export interface Tag {
  id: string;
  name: string;
  color: string;
  status: TagStatus;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  deletedBy: string | null;
  deletedAt: string | null;
}

export interface TagRequest {
  name: string;
  color: string;
  status?: TagStatus;
}
