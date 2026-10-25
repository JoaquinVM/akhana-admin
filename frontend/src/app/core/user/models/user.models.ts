import { Role } from '../../auth/models/auth.models';

export interface User {
  id: string;
  fullName: string;
  username: string;
  role: Role;
  active: boolean;
  permissions: string[];
  createdAt: string;
  createdBy: string;
  updatedAt: string | null;
  updatedBy: string | null;
  deletedAt: string | null;
  deletedBy: string | null;
}

export interface CreateUserRequest {
  fullName: string;
  username: string;
  password: string;
  permissions: string[];
}

export interface UpdateUserRequest {
  fullName: string;
  permissions: string[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface PermissionGroup {
  id: string;
  label: string;
  permissions: {
    key: string;
    label: string;
  }[];
}
